package edu.unt.nsllab.butshuti.bluetoothvpn.tunnel;

import android.bluetooth.BluetoothSocket;
import android.os.SystemClock;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedList;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import edu.unt.nsllab.butshuti.bluetoothvpn.datagram.Packet;
import edu.unt.nsllab.butshuti.bluetoothvpn.datagram.WireInterface;
import edu.unt.nsllab.butshuti.bluetoothvpn.sockets.BluetoothSocketWrappers;
import edu.unt.nsllab.butshuti.bluetoothvpn.utils.Logger;

import static edu.unt.nsllab.butshuti.bluetoothvpn.tunnel.RemoteInterfaceAdaptor.DummyState.NONE;
import static edu.unt.nsllab.butshuti.bluetoothvpn.tunnel.RemoteInterfaceAdaptor.DummyState.READ_END;
import static edu.unt.nsllab.butshuti.bluetoothvpn.tunnel.RemoteInterfaceAdaptor.DummyState.READ_START;
import static edu.unt.nsllab.butshuti.bluetoothvpn.tunnel.RemoteInterfaceAdaptor.DummyState.WRITE_END;
import static edu.unt.nsllab.butshuti.bluetoothvpn.tunnel.RemoteInterfaceAdaptor.DummyState.WRITE_START;

/**
 * Created by butshuti on 12/20/17.
 *
 * Abstract socket wrapper for BT-to-BT connections.
 * This wrapper will extends locally though an {@link InterfaceController}.
 * Implementations of this wrapper will differ in whether they implement a server or client end of the wrapped socket.
 * For instance, while a client socket connects to exactly one remote server, a server socket handles multiple client connections and dispatches sockets to handle each received connection.
 * Individual connections are served by instances of the {@link SocketThread} class.
 */
public abstract class RemoteInterfaceAdaptor{


    enum DummyState{
        READ_START, READ_END,
        WRITE_START, WRITE_END,
        NONE
    };

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

    /**
     * Thread for handling connections received from remote peers.
     */
    protected class SocketThread extends Thread implements InterfaceController.RemoteDatagramDeliveryListener {

        private static final int STREAM_REFRESH_INTERVAL = 3000;
        BluetoothSocket btSocket;
        RemoteInterfaceAdaptor adaptor;
        private final Queue<Packet> outputQueue;
        private final Lock outputQueueLock = new ReentrantLock();
        private volatile OutputStream outputStream;
        private boolean errorState;
        private long pktsIn = 0, pktsOut = 0, lastPktCount = -1;
        private long stream_refresh_schedule = -1;
        private long startTs, lastStatsLogTs;
        private DummyState dummyState = NONE;
        private ExecutorService executorService = Executors.newSingleThreadExecutor();

        /**
         * Create an instance of the socket thread.
         * @param socket Bluetooth socket, a termination into the remote end of the connection (Input stream and output stream).
         * @param adaptor The interface adaptor, a termination into the local end of the connection (through the {@link InterfaceController}).
         */
        public SocketThread(BluetoothSocket socket, RemoteInterfaceAdaptor adaptor){
            setDaemon(false);
            //android.os.Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
            this.adaptor = adaptor;
            btSocket = socket;
            outputQueue = new LinkedList<>();
            errorState = false;
            startTs = SystemClock.uptimeMillis();
            lastStatsLogTs = startTs;
        }

        @Override
        public void run(){
            setName("SocketThread @ " + getName());
            final InputStream inputStream;
            String remoteDevAddress = null;
            long lastRefresh = Math.max(stream_refresh_schedule, SystemClock.elapsedRealtime());
            try{
                remoteDevAddress = btSocket.getRemoteDevice().getAddress();
                inputStream = btSocket.getInputStream();
                outputStream = btSocket.getOutputStream();
                WireInterface wireInterface = new WireInterface() {
                    @Override
                    protected int read(byte[] buffer, int max) throws IOException {
                        if(inputStream.available() >= max){
                            return inputStream.read(buffer);
                        }
                        return 0;
                    }
                };
                adaptor.getInterfaceController().registerRemoteDeliveryListener(remoteDevAddress, getChannelID(), this);
                errorState = !(adaptor.isActive() && btSocket.isConnected());
                while (adaptor.isActive() && btSocket.isConnected() && !errorState){
                    dummyState = READ_START;
                    Packet pkt = wireInterface.readMultipartNext();
                    if(pkt != null){
                        adaptor.getInterfaceController().receive(remoteDevAddress, pkt);
                        pktsIn++;
                        adaptor.getInterfaceController().registerRemoteDeliveryListener(remoteDevAddress, getChannelID(), this);
                    }
                    dummyState = READ_END;
                    long curTs = SystemClock.elapsedRealtime();
                    if(flushOutputQueue()){
                        lastRefresh = curTs;
                    }else if(stream_refresh_schedule < 0){
                        stream_refresh_schedule = curTs + STREAM_REFRESH_INTERVAL * 5 ;
                    }else if(curTs > lastRefresh + STREAM_REFRESH_INTERVAL){
                        byte data[] = String.format("dev(%s).keepAlive(%d) @%d", remoteDevAddress, STREAM_REFRESH_INTERVAL, lastRefresh).getBytes();
                        Packet keepAlivePkt = Packet.wrap(data);
                        keepAlivePkt.setProtocol(Packet.PROTOCOL_PROXIMITY);
                        write(keepAlivePkt, true);
                        lastRefresh = curTs;
                    }
                    if((pktsIn + pktsOut) % 10000 <= 2){
                        printStats();
                    }
                }
            }catch (IOException e){
                e.printStackTrace();
                Logger.logE(String.format("IOException: %s. Invalidating adaptor for %s.", e.getMessage(), remoteDevAddress));
                adaptor.getInterfaceController().registerRemoteDeliveryListener(remoteDevAddress, getChannelID(), null);
                shutdown();
                return;
            }
        }

        @Override
        public boolean write(Packet pkt, boolean async) throws IOException {
            if(errorState){
                throw new IOException("Invalid write state.");
            }
            if(!async){
                return write(pkt);
            }
            boolean pktQueued = false, hasLock = false;
            if(pkt != null) {
                try{
                    hasLock = outputQueueLock.tryLock(300, TimeUnit.MILLISECONDS);
                    if(hasLock){
                        pktQueued = outputQueue.offer(pkt);
                    }
                }catch (IllegalArgumentException e){
                    throw new IOException(e);
                } catch (InterruptedException e) {

                }finally {
                    if(hasLock){
                        outputQueueLock.unlock();
                    }
                }
            }
            return pktQueued;
        }

        private void printStats(){
            long tsDiff = (SystemClock.uptimeMillis() - startTs)/1000;
            if(tsDiff == 0){
                return;
            }
            if(pktsIn + pktsOut == lastPktCount && lastStatsLogTs < tsDiff + STREAM_REFRESH_INTERVAL*3){
                return;
            }
            lastStatsLogTs = tsDiff;
            Logger.logI(String.format("IN: %d pkts/s, OUT: %d pkts/s, queue_growth: %d/s, max_queue_reqs: %d/s, outQueueSize: %d", pktsIn/tsDiff, pktsOut/tsDiff, outputQueue.size()/tsDiff, (pktsOut+outputQueue.size())/tsDiff, outputQueue.size()));
        }

        @Override
        public void shutdown(){
            adaptor.notifySocketException(btSocket.getRemoteDevice().getAddress());
            try {
                btSocket.getInputStream().close();
                btSocket.getOutputStream().close();
                btSocket.close();
                errorState = true;
                if(!(executorService.isShutdown() || executorService.isTerminated())){
                    executorService.shutdownNow();
                }
            } catch (IOException e) {
                Logger.logE(e.getMessage());
            }
        }

        private boolean write(Packet pkt) throws IOException {
            submitWrite(pkt);
            return true;
        }


        private void submitWrite(Packet pkt) throws IOException{
            Future future = executorService.submit(new Runnable() {
                @Override
                public void run() {
                    try{
                        outputStream.write(WireInterface.toBytes(pkt));
                        outputStream.flush();
                    }catch (IOException e){
                        errorState = true;
                    }
                }
            });
            try {
                future.get(500, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                Logger.logE("OutputStream STUCK? --- tearing connection down!");
                future.cancel(true);
                throw new IOException(e);
            }
        }

        private boolean flushOutputQueue() throws IOException {
            int sent = 0;
            Packet pkt = null;
            boolean hasLock = outputQueueLock.tryLock();
            if(hasLock) {
                try{
                    if (outputQueue.peek() != null) {
                        pkt = outputQueue.poll();
                    }
                }finally {
                    outputQueueLock.unlock();
                }
            }
            dummyState = WRITE_START;
            if(pkt != null){
                byte[] bytes = WireInterface.toBytes(pkt);
                outputStream.write(bytes);
                outputStream.flush();
                sent++;
                pktsOut++;
            }
            dummyState = WRITE_END;
            return sent > 0;
        }
    }
}