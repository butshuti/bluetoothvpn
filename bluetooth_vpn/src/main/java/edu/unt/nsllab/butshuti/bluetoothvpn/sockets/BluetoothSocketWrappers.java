package edu.unt.nsllab.butshuti.bluetoothvpn.sockets;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;

import java.io.Closeable;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import edu.unt.nsllab.butshuti.bluetoothvpn.discovery.ApplicationService;
import edu.unt.nsllab.butshuti.bluetoothvpn.utils.Logger;

/**
 * Created by butshuti on 2/7/18.
 */

public class BluetoothSocketWrappers {
    static final int MAX_NUM_CHANNELS = 1;
    private final static boolean REVERSE_CONNECT = false;
    private static int SELECTED_CHANNEL_IDX = 0;
    private static final int CLIENT_SOCKET_POLL_INDEX = 20;
    private static final int CONNECTION_WAIT_TIMEOUT = -3000;
    private static final int REVERSE_CONNECT_WAIT_INTERVAL = 1000;
    private static final boolean FORCE_DEFAULT_SECURE = false;

    public static class ServerSocket implements Closeable {
        private int channelIndex;
        private BluetoothServerSocket channels[];
        private Map<BluetoothDevice, BluetoothSocket> connectedClients;
        private boolean secure;

        public ServerSocket(BluetoothAdapter bluetoothAdapter) throws IOException{
            this(bluetoothAdapter, FORCE_DEFAULT_SECURE);
        }

        private ServerSocket(BluetoothAdapter bluetoothAdapter, boolean secure) throws IOException {
            this.secure = secure;
            channels = new BluetoothServerSocket[MAX_NUM_CHANNELS];
            connectedClients = new HashMap<>();
            for(int i=0; i<MAX_NUM_CHANNELS; i++) {
                if(secure){
                    channels[i] = bluetoothAdapter.listenUsingRfcommWithServiceRecord(ApplicationService.getServiceName(i), ApplicationService.getServiceUUID(i));
                }else {
                    channels[i] = bluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord(ApplicationService.getServiceName(i), ApplicationService.getServiceUUID(i));
                }
            }
            channelIndex = 0;
        }

        public BluetoothSocket accept() throws IOException {
            if(channelIndex >= MAX_NUM_CHANNELS){
                channelIndex = 0;
            }
            BluetoothServerSocket nextChannel = channels[channelIndex++];
            if(nextChannel != null){
                BluetoothSocket socket;
                try {
                    socket = manageConnection(nextChannel.accept());//CONNECTION_WAIT_TIMEOUT/MAX_NUM_CHANNELS));
                }catch (IOException e){
                    return null;
                }
                recordConnection(socket);
                return socket;
            }
            throw new IOException("No channel available.");
        }

        private BluetoothSocket manageConnection(BluetoothSocket incomingConnectionRequest) throws IOException {
            if(!REVERSE_CONNECT){
                return incomingConnectionRequest;
            }
            if(incomingConnectionRequest != null){
                Logger.logD("Accepted connection from " + incomingConnectionRequest.getRemoteDevice());
                BluetoothSocket socket;
                if(secure) {
                    socket = incomingConnectionRequest.getRemoteDevice()
                            .createRfcommSocketToServiceRecord(ApplicationService.getServiceUUID(CLIENT_SOCKET_POLL_INDEX));
                }else{
                    socket = incomingConnectionRequest.getRemoteDevice()
                            .createInsecureRfcommSocketToServiceRecord(ApplicationService.getServiceUUID(CLIENT_SOCKET_POLL_INDEX));
                }
                try {
                    int timeout = 2 * REVERSE_CONNECT_WAIT_INTERVAL;
                    Logger.logE(String.format("Waiting for %d ms to reverse-connect to %s.", timeout, incomingConnectionRequest.getRemoteDevice().getAddress()));
                    Thread.sleep(timeout);
                } catch (InterruptedException e) {

                }
                try {
                    Logger.logE("Attempting reverse-connection...");
                    socket.connect();
                    Logger.logD("Registered connection to " + socket.getRemoteDevice());
                }catch (IOException e){
                    Logger.logE(String.format("%s: %s", incomingConnectionRequest.getRemoteDevice(),e.getMessage()));
                    return null;
                }finally {
                    try{
                        incomingConnectionRequest.close();
                    }catch (IOException e){}
                }
                return socket;
            }
            return null;
        }

        private void recordConnection(BluetoothSocket socket){
            if(socket != null){
                if(connectedClients.containsKey(socket.getRemoteDevice())){
                    BluetoothSocket previousSocket = connectedClients.get(socket.getRemoteDevice());
                    if(previousSocket != null){
                        try {
                            previousSocket.close();
                        } catch (IOException e) {}
                    }
                }
                connectedClients.put(socket.getRemoteDevice(), socket);
                Logger.logD("Accepted connection from " + socket.getRemoteDevice() + " on channel #" + (channelIndex-1));
            }
        }

        /**
         * Closes this stream and releases any system resources associated
         * with it. If the stream is already closed then invoking this
         * method has no effect.
         *
         * @throws IOException if an I/O error occurs
         */
        @Override
        public void close() throws IOException {
            for(int i=0; i<channels.length; i++){
                channels[i].close();
            }
            for(BluetoothSocket socket : connectedClients.values()) {
                try {
                    socket.close();
                } catch (IOException e) {}
            }
        }
    }

    public static class ClientSocket implements Closeable{
        private static final int MAX_TIMEOUT_COUNT = 3;
        private static final ReentrantLock CLIENT_GLOBAL_SOCKET_LOCK = new ReentrantLock();
        private BluetoothAdapter bluetoothAdapter;
        private BluetoothDevice remoteDevice;
        private static BluetoothSocket lastConnectedSocket;
        private boolean secure;
        private static int timeoutCount = 0;

        public ClientSocket(BluetoothAdapter bluetoothAdapter, BluetoothDevice device){
            this(bluetoothAdapter, device, FORCE_DEFAULT_SECURE);
        }

        private ClientSocket(BluetoothAdapter bluetoothAdapter, BluetoothDevice device, boolean secure) {
            this.bluetoothAdapter = bluetoothAdapter;
            this.secure = secure;
            remoteDevice = device;
            lastConnectedSocket = null;
            if(!bluetoothAdapter.isEnabled()){
                bluetoothAdapter.enable();
            }
        }

        public BluetoothSocket newConnectedSocket() throws IOException{
            //Important: Executions of this method must not overlap, to avoid inconsistent connection states.
            if(CLIENT_GLOBAL_SOCKET_LOCK.tryLock()){
                try{
                    boolean connectionRequestAccepted = false;
                    bluetoothAdapter.cancelDiscovery();
                    if(lastConnectedSocket != null){
                        if(lastConnectedSocket.isConnected()){
                            return lastConnectedSocket;
                        }
                        try{
                            lastConnectedSocket.close();
                            lastConnectedSocket = null;
                        }catch (IOException e){
                            //Ignore exception, remote peer may already have disconnected
                            lastConnectedSocket = null;
                        }
                    }
                    BluetoothSocket ret;
                    IOException lastIOException = null;
                    int channelIdx = (SELECTED_CHANNEL_IDX++) % MAX_NUM_CHANNELS;
                    Logger.logD("Attempting connection on channel ID " + channelIdx);
                    BluetoothSocket connectionRequest;
                    if(secure){
                        connectionRequest = remoteDevice.createRfcommSocketToServiceRecord(ApplicationService.getServiceUUID(channelIdx));
                    }else {
                        connectionRequest = remoteDevice.createInsecureRfcommSocketToServiceRecord(ApplicationService.getServiceUUID(channelIdx));
                    }
                    Logger.logD("Sending connection request on ch#" + channelIdx);
                    try{
                        try {
                            connectionRequest.connect();
                            connectionRequestAccepted = true;
                            Logger.logD("Request accepted: ch#" + channelIdx + "... waiting for incoming connection.");
                        }catch (IOException e){
                            Logger.logE("connect failed.");
                            //throw new HostUnreachableException(e);
                        }
                        if(connectionRequestAccepted){
                            if(!REVERSE_CONNECT){
                                lastConnectedSocket = connectionRequest;
                                return connectionRequest;
                            }else{
                                BluetoothServerSocket connectionPollSocket;
                                if(secure){
                                    connectionPollSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord(ApplicationService.getServiceName(CLIENT_SOCKET_POLL_INDEX),
                                            ApplicationService.getServiceUUID(CLIENT_SOCKET_POLL_INDEX));
                                }else{
                                    connectionPollSocket = bluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord(ApplicationService.getServiceName(CLIENT_SOCKET_POLL_INDEX),
                                            ApplicationService.getServiceUUID(CLIENT_SOCKET_POLL_INDEX));
                                }
                                //Clients must timeout fast and retry if no connection is established.
                                int timeout = MAX_NUM_CHANNELS*CONNECTION_WAIT_TIMEOUT/Math.max(MAX_NUM_CHANNELS, MAX_TIMEOUT_COUNT/MAX_NUM_CHANNELS);
                                ret = connectionPollSocket.accept(timeout + REVERSE_CONNECT_WAIT_INTERVAL);
                                timeoutCount = 0;
                                try{
                                    connectionRequest.close();
                                }catch (IOException e){
                                    //Ignore exception, remote peer may already have disconnected.
                                }
                                connectionPollSocket.close();
                                lastConnectedSocket = ret;
                                Logger.logD("Connected to " + remoteDevice);
                                return ret;
                            }
                        }
                    }catch (IOException e){
                        if(e.getMessage().startsWith("Try again")){
                            timeoutCount++;
                            Logger.logE("Timeout #" + timeoutCount + ": " + e.getMessage());
                        }else{
                            Logger.logE(e.getMessage());
                        }
                        if(timeoutCount >= MAX_TIMEOUT_COUNT){
                            bluetoothAdapter.disable();
                        }
                        lastIOException = e;
                        if(channelIdx >= MAX_NUM_CHANNELS){
                            throw e;
                        }
                    }
                    if(lastIOException == null){
                        lastIOException = new IOException("Unable to connect to " + remoteDevice);
                    }
                    throw lastIOException;
                }finally {
                    CLIENT_GLOBAL_SOCKET_LOCK.unlock();
                }
            }else {
                throw new IOException("Unable to obtain connection CLIENT_GLOBAL_SOCKET_LOCK.");
            }
        }

        public boolean isConnected(){
            return lastConnectedSocket != null && lastConnectedSocket.isConnected();
        }

        public BluetoothDevice getRemoteDevice(){
            return remoteDevice;
        }

        public void close() throws IOException{
            if(lastConnectedSocket != null){
                lastConnectedSocket.close();
            }
            lastConnectedSocket = null;
        }
    }

    public static final class HostUnreachableException extends Exception{
        HostUnreachableException(Exception e){
            super(e);
        }
    }
}
