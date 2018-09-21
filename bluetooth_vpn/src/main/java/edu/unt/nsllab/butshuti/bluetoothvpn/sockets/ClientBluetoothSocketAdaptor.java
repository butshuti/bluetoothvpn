package edu.unt.nsllab.butshuti.bluetoothvpn.sockets;

import android.bluetooth.BluetoothSocket;

import java.io.IOException;
import java.net.InetAddress;

import edu.unt.nsllab.butshuti.bluetoothvpn.tunnel.InterfaceController;
import edu.unt.nsllab.butshuti.bluetoothvpn.tunnel.LocalInterfaceBridge;
import edu.unt.nsllab.butshuti.bluetoothvpn.tunnel.RemoteInterfaceAdaptor;
import edu.unt.nsllab.butshuti.bluetoothvpn.utils.Logger;

/**
 * Created by butshuti on 12/20/17.
 *
 * A client implementation of the {@link RemoteInterfaceAdaptor}.
 * Client adaptors are basic in the fact that they maintain a connection to exactly one server at a time.
 * That means exactly one socket, one local bridge.
 */

public class ClientBluetoothSocketAdaptor extends RemoteInterfaceAdaptor{

    private BluetoothSocketWrappers.ClientSocket clientSocket;
    private SocketThread socketThread;

    public ClientBluetoothSocketAdaptor(BluetoothSocketWrappers.ClientSocket socket, InterfaceController interfaceController){
        super(interfaceController);
        clientSocket = socket;
    }

    @Override
    public void startAdaptor() throws RemoteInterfaceException {
        BluetoothSocket btSocket;
        clearLastException();
        try{
            {
                btSocket = clientSocket.newConnectedSocket();
            }
            try {
                if(!LocalInterfaceBridge.isInitialized()) {
                    LocalInterfaceBridge.reset();
                    LocalInterfaceBridge.initialize(InetAddress.getByName(getInterfaceController().getLocalInterfaceAddress()));
                }
            } catch (IOException e) {
                throw new RemoteInterfaceException(e);
            }
            try{
                reportNewConnection(clientSocket.getRemoteDevice().getAddress());
                socketThread = new SocketThread(btSocket, this);
                LocalInterfaceBridge.addGateway(btSocket.getRemoteDevice().getAddress());
                socketThread.start();
            }catch (Exception e) {
                setLastException(new Exception(String.format("Failed to connect to %s: %s", clientSocket.getRemoteDevice(), e.getMessage())));
                Logger.logE(e.getMessage());
                if(btSocket != null){
                    try {
                        btSocket.close();
                    } catch (IOException e1) {
                        Logger.logE(e.getMessage());
                    }
                }
                throw new RemoteInterfaceException(e.getMessage());
            }
        }catch (IOException e) {
            Exception customException = new Exception("Failed to connect to " + clientSocket.getRemoteDevice() + ": " + e.getMessage());
            Logger.logE(customException.getMessage());
            setLastException(customException);
            notifySocketException(clientSocket.getRemoteDevice().getAddress());
        }
    }

    /**
     * Start the adaptor.
     */
    @Override
    public void stopAdaptor() {
        try {
            clientSocket.close();
        } catch (IOException e) {

        }
        if(socketThread != null){
            socketThread.interrupt();
        }
    }
}
