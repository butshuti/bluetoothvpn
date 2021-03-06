package edu.unt.nslab.butshuti.bluetoothvpn.sockets;

import android.bluetooth.BluetoothSocket;

import java.io.IOException;
import java.net.InetAddress;

import edu.unt.nslab.butshuti.bluetoothvpn.tunnel.InterfaceController;
import edu.unt.nslab.butshuti.bluetoothvpn.tunnel.LocalInterfaceBridge;
import edu.unt.nslab.butshuti.bluetoothvpn.tunnel.RemoteInterfaceAdaptor;
import edu.unt.nslab.butshuti.bluetoothvpn.utils.Logger;

/**
 * Created by butshuti on 5/17/18.
 *
 * A client implementation of the {@link RemoteInterfaceAdaptor}.
 * Client adaptors are basic in the fact that they maintain a connection to exactly one server at a time.
 * That means exactly one socket, one local bridge.
 */

public class ClientBluetoothSocketAdaptor extends RemoteInterfaceAdaptor{

    private BluetoothSocketWrappers.ClientSocket clientSocket;
    private Connection connectionPipe;

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
                connectionPipe = new Connection(btSocket, this);
                LocalInterfaceBridge.addGateway(btSocket.getRemoteDevice().getAddress());
                connectionPipe.start();
            }catch (Exception e) {
                setLastException(new Exception(String.format("Failed to connect to %s: %s", clientSocket.getRemoteDevice(), e.getMessage())));
                LocalInterfaceBridge.deleteGateway(btSocket.getRemoteDevice().getAddress());
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
        if(connectionPipe != null){
            connectionPipe.interrupt();
        }
    }
}
