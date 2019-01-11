package edu.unt.nslab.butshuti.bluetoothvpn.sockets;

import android.bluetooth.BluetoothSocket;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import edu.unt.nslab.butshuti.bluetoothvpn.tunnel.InterfaceController;
import edu.unt.nslab.butshuti.bluetoothvpn.tunnel.LocalInterfaceBridge;
import edu.unt.nslab.butshuti.bluetoothvpn.tunnel.RemoteInterfaceAdaptor;
import edu.unt.nslab.butshuti.bluetoothvpn.utils.Logger;

/**
 * Created by butshuti on 2/7/18.
 *
 * A server implementation of the {@link RemoteInterfaceAdaptor}.
 * The server case is only special in that it handles multiple client connections at the same time.
 */

public class ServerBluetoothSockerAdaptor extends RemoteInterfaceAdaptor implements InterfaceController.NetworkEventListener {

    private BluetoothSocketWrappers.ServerSocket serverSocket = null; // The local server socket.
    private Map<String, BluetoothSocket> openSockets; // BT-specific sockets from remote clients.

    /**
     * Crate a new server socket adaptor.
     * @param serverSocket The server socket for accepting connections from remote peers.
     * @param interfaceController The local interface controller.
     */
    public ServerBluetoothSockerAdaptor(BluetoothSocketWrappers.ServerSocket serverSocket, InterfaceController interfaceController) {
        super(interfaceController);
        this.serverSocket = serverSocket;
        openSockets = new HashMap<>();
        interfaceController.registerEventListener(this);
    }

    /**
     * Start this adaptor.
     * <p>
     *     Specifically, start the event-loop for accepting incoming connections to dispatch a handler for each.
     * </p>
     * @throws LocalInterfaceBridge.BridgeException
     */
    @Override
    public void startAdaptor() throws LocalInterfaceBridge.BridgeException{
        try {
            LocalInterfaceBridge.reset();
            LocalInterfaceBridge.initialize(InetAddress.getByName(getInterfaceController().getLocalInterfaceAddress()));
        } catch (UnknownHostException e) {
            throw new LocalInterfaceBridge.BridgeException(e);
        }
        while (isActive()){
            final BluetoothSocket socket;
            try {
                Logger.logI("Waiting...");
                socket = serverSocket.accept();
                if(socket != null){
                    Logger.logI("Registered connection from " + socket.getRemoteDevice().getAddress());
                }else{
                    continue;
                }
            } catch (IOException e) {
                throw new LocalInterfaceBridge.BridgeException(e);
            }
            if(socket != null && socket.isConnected()){
                Connection st = new Connection(socket, this);
                LocalInterfaceBridge.addGateway(socket.getRemoteDevice().getAddress());
                String remoteDeviceAddress = socket.getRemoteDevice().getAddress();
                if(openSockets.containsKey(remoteDeviceAddress)){
                    try{
                        openSockets.get(remoteDeviceAddress).close();
                    }catch (IOException e){
                        //Ignore this, remote peer might already have closed.
                    }
                }
                openSockets.put(remoteDeviceAddress, socket);
                st.start();
            }else{
                throw new LocalInterfaceBridge.BridgeException("Invalid socket state");
            }
        }
    }

    /**
     * Stop this adaptor.
     * <p>
     *     This has the effect of shutting down any ongoing connections with remote clients.
     * </p>
     */
    @Override
    public void stopAdaptor() {
        getInterfaceController().deactivate();
        synchronized (openSockets){
            for (Map.Entry<String, BluetoothSocket> entry : openSockets.entrySet()){
                try {
                    entry.getValue().getInputStream().close();
                    entry.getValue().getOutputStream().close();
                    entry.getValue().close();
                } catch (IOException e) {}
            }
        }
        openSockets.clear();
    }

    /**
     * Signal an exception from which the only recovery is to clear the socket with the remote peer.
     * <p>
     *     This happens for instance when an attempt to read from a client socket's input stream results in an IO exception.
     * </p>
     * @param channelID The unique ID for the client's channel.
     * @param remotePeerAddress The remote peer's address
     */
    @Override
    public void notifyIrrecoverableException(String channelID, String remotePeerAddress) {
        synchronized (openSockets) {
            if (openSockets.containsKey(remotePeerAddress)) {
                try {
                    openSockets.get(remotePeerAddress).close();
                } catch (IOException e) {

                }
                openSockets.remove(remotePeerAddress);
                LocalInterfaceBridge.deleteGateway(remotePeerAddress);
            }
        }
    }

    @Override
    public void onNewConnection(String channelID, String remotePeerAddress) {
        //
    }

    public Collection<String> getRecentClients(){
        Collection<String> ret = new ArrayList<>();
        for(BluetoothSocket socket: openSockets.values()){
            if(socket.isConnected()){
                ret.add(socket.getRemoteDevice().getAddress());
            }
        }
        return ret;
    }

}
