package edu.unt.nslab.butshuti.bluetoothvpn.tunnel;

import android.bluetooth.BluetoothSocket;
import android.os.SystemClock;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import edu.unt.nslab.butshuti.bluetoothvpn.datagram.Packet;
import edu.unt.nslab.butshuti.bluetoothvpn.datagram.WireInterface;
import edu.unt.nslab.butshuti.bluetoothvpn.sockets.BluetoothSocketWrappers;
import edu.unt.nslab.butshuti.bluetoothvpn.utils.Logger;
import edu.unt.nslab.butshuti.bluetoothvpn.utils.ThreadLifeMonitor;

/**
 * Created by butshuti on 5/17/18.
 *
 * Abstract socket wrapper for BT-to-BT connections.
 * This wrapper will extends locally though an {@link InterfaceController}.
 * Implementations of this wrapper will differ in whether they implement a server or client end of the wrapped socket.
 * For instance, while a client socket connects to exactly one remote server, a server socket handles multiple client connections and dispatches sockets to handle each received connection.
 * Individual connections are served by instances of the {@link Connection} class.
 */
public abstract class RemoteInterfaceAdaptor{

    /**
     * Custom exception class.
     */
    public static class RemoteInterfaceException extends IOException {
        public RemoteInterfaceException(String msg){
            super(msg);
        }
        public RemoteInterfaceException(IOException e){
            super(e);
        }
    }

    private InterfaceController interfaceController;
    private String channelID;
    private Exception lastException;

    /**
     * Constructor.
     * @param interfaceController The interface controller for this adaptor.
     */
    public RemoteInterfaceAdaptor(InterfaceController interfaceController){
        channelID = UUID.randomUUID().toString();
        this.interfaceController = interfaceController;
        this.interfaceController.activate();
    }

    public String getChannelID(){
        return channelID;
    }

    protected InterfaceController getInterfaceController(){
        return interfaceController;
    }

    protected void clearLastException(){
        lastException = null;
    }

    protected void setLastException(Exception e){
        lastException = e;
    }

    public String getLastErrorStatus(){
        if(lastException != null){
            return lastException.getMessage();
        }
        return "Connection reset";
    }

    /**
     * Assert whether this adaptor is up.
     * @return True/False, depending on whether the adaptor is active or not.
     */
    public boolean isActive(){
        return interfaceController.isActive();
    }

    /**
     * Report an IO Exception happened on a channel for a remote peer.
     * The exception will be handled by the interface controller.
     * @param remotePeerAddress The remote peer's address.
     */
    public void notifySocketException(String remotePeerAddress){
        interfaceController.notifyChannelException(channelID, remotePeerAddress);
    }

    /**
     * Report a new connection with a remote peer.
     * @param remotePeerAddress The remote peer's address.
     */
    public void reportNewConnection(String remotePeerAddress){
        interfaceController.onNewConnection(channelID, remotePeerAddress);
    }

    /**
     * Start the adaptor's event loop.
     * @throws LocalInterfaceBridge.BridgeException
     * @throws RemoteInterfaceException
     */
    public abstract void startAdaptor() throws LocalInterfaceBridge.BridgeException, RemoteInterfaceException, BluetoothSocketWrappers.HostUnreachableException;

    /**
     * Stop the adaptor's event-loop.
     */
    public abstract void stopAdaptor();

    protected final static class Connection extends WireInterface implements InterfaceController.RemoteDatagramDeliveryListener{
        private String remoteAddress;
        private BluetoothSocket socket;
        private RemoteInterfaceAdaptor adaptor;
        private boolean active;
        private long bytesIN = 0, bytesOUT = 0;
        private PipedInputStream pipedInputStream;
        private PipedOutputStream pipedOutputStream;
        private static SocketThread socketThread = null;

        /**
         * Create an instance of the socket thread.
         * @param btSocket Bluetooth socket, a termination into the remote end of the connection (Input stream and output stream).
         * @param adaptor The interface adaptor, a termination into the local end of the connection (through the {@link InterfaceController}).
         */
        public Connection(BluetoothSocket btSocket, RemoteInterfaceAdaptor adaptor){
            socket = btSocket;
            this.adaptor = adaptor;
            remoteAddress = socket.getRemoteDevice().getAddress();
            if(socketThread == null){
                synchronized (SocketThread.class){
                    if(socketThread == null){
                        socketThread = new SocketThread(adaptor);
                    }
                }
            }
        }

        public void start() {
            socketThread.addStream(this);
            adaptor.getInterfaceController().registerRemoteDeliveryListener(remoteAddress, adaptor.getChannelID(), this);
            if(!socketThread.isAlive()){
                socketThread.start();
            }
            active = true;
        }

        public void interrupt(){
            try {
                invalidate();
            } catch (IOException e) {
                Logger.logE(e.getMessage());
            }
        }

        private boolean flush() throws IOException {
            int sent = 0;
            while (getPipedInputStream().available() > 0){
                getOutputStream().write(getPipedInputStream().read());
                sent++;
            }
            return sent > 0;
        }

        @Override
        public boolean write(Packet pkt, boolean async) throws IOException {
            if(Thread.currentThread().equals(socketThread)){
                //Avoid reading and writing from the same thread.
                return false;
            }
            byte data[] = toBytes(pkt);
            if(data != null) {
                getPipedOutputStream().write(data);
                bytesOUT += data.length;
                return true;
            }
            return false;
        }

        @Override
        public void shutdown() {
            try {
                invalidate();
            } catch (IOException e) {
                Logger.logE(e.getMessage());
            }
        }

        @Override
        public boolean isPrimary() {
            return true;
        }

        private boolean write(Packet pkt) throws IOException {
            if(!Thread.currentThread().equals(socketThread)){
                //Only registered socket thread is expected to write to the stream.
                return false;
            }
            if(pkt != null) {
                byte data[] = toBytes(pkt);
                if (data != null) {
                    getOutputStream().write(data);
                    bytesOUT += data.length;
                    return true;
                }
            }
            return false;
        }


        public boolean isConnected(){
            return active && socket.isConnected();
        }

        private void invalidate() throws IOException {
            active = false;
            adaptor.notifySocketException(remoteAddress);
            if(socketThread != null){
                socketThread.removeStream(remoteAddress);
                if(socketThread.connections.size() == 0){
                    socketThread.interrupt();
                    socketThread = null;
                }
            }
            if(socket != null){
                socket.getInputStream().close();
                socket.getOutputStream().close();
                socket.close();
            }
        }

        private PipedOutputStream getPipedOutputStream() throws IOException {
            if(pipedInputStream == null){
                pipedInputStream = new PipedInputStream();
                pipedOutputStream = new PipedOutputStream(pipedInputStream);
            }
            return pipedOutputStream;
        }

        private PipedInputStream getPipedInputStream() throws IOException {
            if(pipedInputStream == null){
                pipedInputStream = new PipedInputStream();
                pipedOutputStream = new PipedOutputStream(pipedInputStream);
            }
            return pipedInputStream;
        }

        private OutputStream getOutputStream() throws IOException {
            if(pipedInputStream == null){
                pipedInputStream = new PipedInputStream();
                pipedOutputStream = new PipedOutputStream(pipedInputStream);
            }
            return socket.getOutputStream();
        }

        @Override
        protected int read(byte[] buffer, int max) throws IOException {
            if(socket.getInputStream().available() >= max){
                bytesIN++;
                return socket.getInputStream().read(buffer);
            }
            return 0;
        }

        @Override
        public String toString(){
            return remoteAddress + (isConnected() ? "" : "(DISCONNECTED)" + "IN=" + bytesIN + "/OUT=" + bytesOUT);
        }

        @Override
        public boolean equals(Object other){
            return other != null && other instanceof Connection && ((Connection) other).remoteAddress.equals(remoteAddress);
        }

        @Override
        public int hashCode(){
            return remoteAddress.hashCode();
        }
    }

    /**
     * Thread for handling connections received from remote peers.
     */
    private final static class SocketThread extends Thread{

        private static final int STREAM_REFRESH_INTERVAL = 5000;
        RemoteInterfaceAdaptor adaptor;
        private volatile Map<String, Connection> connections;
        private boolean errorState;
        private long pktsIn = 0, pktsOut = 0, lastPktCount = -1;
        private long stream_refresh_schedule = -1;
        private long startTs, lastStatsLogTs;

        /**
         * Create an instance of the socket thread.
         * @param adaptor The interface adaptor, a termination into the local end of the connection (through the {@link InterfaceController}).
         */
        private SocketThread(RemoteInterfaceAdaptor adaptor){
            setDaemon(false);
            this.adaptor = adaptor;
            errorState = false;
            startTs = SystemClock.uptimeMillis();
            lastStatsLogTs = startTs;
            connections = new ConcurrentHashMap<>();
        }

        @Override
        public void run(){
            long lastRefresh = Math.max(stream_refresh_schedule, SystemClock.elapsedRealtime());
            errorState = !adaptor.isActive();
            while (adaptor.isActive() && !errorState){
                Iterator<Map.Entry<String, Connection>> iterator = connections.entrySet().iterator();
                while(iterator.hasNext()) {
                    Map.Entry<String, Connection> connectionEntry = iterator.next();
                    if(!connectionEntry.getValue().isConnected()){
                        continue;
                    }
                    Connection connection = connectionEntry.getValue();
                    String remoteAddres = connectionEntry.getKey();
                    try{
                        Packet pkt = connection.readMultipartNext();
                        if (pkt != null) {
                            adaptor.getInterfaceController().receive(remoteAddres, pkt);
                            pktsIn++;
                            adaptor.getInterfaceController().registerRemoteDeliveryListener(remoteAddres, adaptor.getChannelID(), connection);
                        }
                        long curTs = SystemClock.elapsedRealtime();
                        if (connection.flush()) {
                            lastRefresh = curTs;
                        } else if (stream_refresh_schedule < 0) {
                            stream_refresh_schedule = curTs + STREAM_REFRESH_INTERVAL * 5;
                        } else if (curTs > lastRefresh + STREAM_REFRESH_INTERVAL) {
                            byte data[] = String.format("dev(%s).keepAlive(%d) @%d", remoteAddres, STREAM_REFRESH_INTERVAL, lastRefresh).getBytes();
                            Packet keepAlivePkt = Packet.wrap(data);
                            keepAlivePkt.setProtocol(Packet.PROTOCOL_PROXIMITY);
                            write(keepAlivePkt, remoteAddres);
                            lastRefresh = curTs;
                        }
                    }catch (IOException e){
                        e.printStackTrace();
                        Logger.logE(String.format("IOException: %s. Invalidating adaptor for %s.", e.getMessage(), remoteAddres));
                        adaptor.getInterfaceController().registerRemoteDeliveryListener(remoteAddres, adaptor.getChannelID(), null);
                        connection.shutdown();
                        iterator.remove();
                        return;
                    }
                }
                if ((pktsIn + pktsOut) % 10 <= 2) {
                    printStats();
                }
            }
        }

        private void addStream(Connection connection){
            connections.put(connection.remoteAddress, connection);
        }

        private void removeStream(String remoteAddress){
            connections.remove(remoteAddress);
        }

        private void printStats(){
            long tsDiff = (SystemClock.uptimeMillis() - startTs)/1000;
            setName("Connections " + connections.keySet() + " @ [[ " + ThreadLifeMonitor.getNextAgeDescr("__", false, pktsIn, pktsOut) + " ]]");
            if(tsDiff == 0){
                return;
            }
            if(pktsIn + pktsOut == lastPktCount && lastStatsLogTs < tsDiff + STREAM_REFRESH_INTERVAL*3){
                return;
            }
            lastStatsLogTs = tsDiff;
        }

        private boolean write(Packet pkt, String remoteAddress) throws IOException {
            Connection connection = connections.get(remoteAddress);
            if(pkt != null && connection != null) {
                connection.write(pkt);
                return true;
            }
            return false;
        }
    }
}