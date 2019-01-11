package edu.unt.nslab.butshuti.bluetoothvpn.tunnel;

import android.os.Handler;
import android.os.Message;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.unt.nslab.butshuti.bluetoothvpn.datagram.Packet;
import edu.unt.nslab.butshuti.bluetoothvpn.utils.Logger;
import edu.unt.nslab.butshuti.bluetoothvpn.utils.net_protocols.AddressConversions;
import edu.unt.nslab.butshuti.bluetoothvpn.utils.net_protocols.InternetLayerHeaders;

/**
 *An interface for multiplexing and demultiplexing local connections on the external-facing bridge.
 * The interface has many internal-facing sockets and one external-facing socket, hence the need for multiplexing/demultiplexing.
 * Internal-facing sockets are identified by assigned connection tags.
 * This is implemented to maintain a single socket to a remote device.
 *
 * Created by butshuti on 5/17/18.
 */

public class InterfaceController{
    public static class NetworkEvent {
        String remoteAddress;
        Packet pkt;
        NetworkEvent(String remoteAddress, Packet pkt){
            this.remoteAddress = remoteAddress;
            this.pkt = pkt;
        }
    }

    public interface NetworkEventListener{
        void notifyIrrecoverableException(String channelID, String remotePeerAddress);
        void onNewConnection(String channelID, String remotePeerAddress);
    }

    public interface LocalDatagramDeliveryListener{
        boolean deliver(Packet pkt);
    }

    public interface RemoteDatagramDeliveryListener{
        boolean write(Packet pkt, boolean async) throws IOException;
        void shutdown();
        boolean isPrimary();
    }

    public interface InterfaceConfigurationView{
        String getLocalBDAddr();
        String getInterfaceAddress();
        void updateLocalBDAddr(String addr);
    }

    private final class RemoteDatagramForwardingListener implements RemoteDatagramDeliveryListener {
        private RemoteDatagramDeliveryListener listener;
        private RemoteDatagramForwardingListener(RemoteDatagramDeliveryListener listener){
            this.listener = listener;
        }
        @Override
        public boolean write(Packet pkt, boolean async) throws IOException {
            return listener.write(pkt, async);
        }

        @Override
        public void shutdown() {

        }

        @Override
        public boolean isPrimary() {
            return false;
        }
    }

    public static final int DEFAULT_RECEIVE_TIMEOUT_MS = 300;
    private final Map<String, RemoteDatagramDeliveryListener> remoteDatagramDeliveryMap = new HashMap<>(); //Registered handlers for output streams to specific peers
    private LocalDatagramDeliveryListener localDatagramDeliveryListener;
    private Map<String, String> outputChannels = new HashMap<>(); //Control channels for sockets
    private List<NetworkEventListener> eventListeners = new ArrayList<>(); //Event listeners for socket events (mainly exceptions).
    private InterfaceConfigurationView interfaceConfigurationView;
    private boolean active = true; //controller is active
    private int timeout = DEFAULT_RECEIVE_TIMEOUT_MS;
    private static InterfaceController instance = null;
    private boolean echoPending = true, enableRouting = true;
    private Handler handler;

    private InterfaceController(InterfaceConfigurationView interfaceConfigurationView){
        this.interfaceConfigurationView = interfaceConfigurationView;
        handler = new Handler(){
            @Override
            public void handleMessage(Message msg) {
                if(msg.obj instanceof NetworkEvent){
                    NetworkEvent event = (NetworkEvent) msg.obj;
                    InterfaceController.this.process(event.remoteAddress, event.pkt);
                }
            }
        };
        instance = this;
    }

    public static InterfaceController getDefault(InterfaceConfigurationView interfaceConfigurationView){
        if(instance == null){
            instance = new InterfaceController(interfaceConfigurationView);
        }
        return instance;
    }

    public String getLocalInterfaceAddress() throws UnknownHostException {
        if(interfaceConfigurationView != null){
            String address = interfaceConfigurationView.getInterfaceAddress();
            if(address != null) {
                return address.split("/")[0];
            }
        }
        return InetAddress.getLocalHost().getHostAddress();
    }

    public void activate(){
        active = true;
    }

    public void deactivate(){
        active = false;
    }

    public boolean isActive(){
        return active;
    }

    public int getTimeout(){
        return timeout;
    }

    private boolean isLocalBDAddr(String addr){
        if(interfaceConfigurationView != null && interfaceConfigurationView.getLocalBDAddr() != null){
            return interfaceConfigurationView.getLocalBDAddr().equals(addr);
        }
        return false;
    }

    private boolean isLocalIPAddr(String addr){
        try {
            return getLocalInterfaceAddress().equals(addr);
        } catch (UnknownHostException e) {
            Logger.logE(e.getMessage());
            return false;
        }
    }

    private boolean isEchoPkt(Packet pkt){
        if(pkt != null) {
            return pkt.getProtocol() == Packet.PROTOCOL_PROXIMITY || pkt.getProtocol() == Packet.PROTOCOL_PROXIMITY_ACK;
        }
        return false;
    }

    private boolean isEchoRequestPkt(Packet pkt){
        if(pkt != null) {
            return pkt.getProtocol() == Packet.PROTOCOL_PROXIMITY;
        }
        return false;
    }

    private boolean isDataPkt(Packet pkt){
        if(pkt != null) {
            return pkt.getProtocol() == Packet.PROTOCOL_DATA;
        }
        return false;
    }

    private boolean isInterfaceTest(Packet pkt){
        if(pkt != null){
            return pkt.getProtocol() == Packet.PROTOCOL_ITEST;
        }
        return false;
    }

    private boolean isPathPropagation(Packet pkt){
        if(pkt != null){
            return pkt.getProtocol() == Packet.PROTOCOL_PATH_PROPAGATION;
        }
        return false;
    }

    public boolean forwardingServiceEnabled(){
        return enableRouting;
    }


    /**
     * Register a network event listener.
     * @param listener
     */
    public void registerEventListener(NetworkEventListener listener){
        if(listener != null){
            eventListeners.add(listener);
        }
    }

    /**
     * Make this interface a forwarder.
     * @param enableRouting
     */
    public void setEnableRouting(boolean enableRouting){
        this.enableRouting = enableRouting;
    }

    /**
     * Register an input stream listener to deliver packets locally.
     * <p>
     * </p>
     * @param listener The listener.
     */
    public void registerLocalDeliveryListener(LocalDatagramDeliveryListener listener){
        localDatagramDeliveryListener = listener;
    }

    /**
     * Register an output stream listener for a remote peer.
     * <p>
     *     When registered, data to be delivered to the peer will be handed directly to this listener instead of being queued onto the send queue.
     *     This may be an async write.
     * </p>
     * @param remoteAddress The address of the remote peer.
     * @param channelID A unique ID for the pseudo-channel
     * @param listener The listener.
     */
    public void registerRemoteDeliveryListener(String remoteAddress, String channelID, RemoteDatagramDeliveryListener listener){
        Logger.logE("Registering remote delivery listener for " + remoteAddress + ": " + listener);
        if(listener != null){
            if(listener.isPrimary() || !remoteDatagramDeliveryMap.containsKey(remoteAddress) || !remoteDatagramDeliveryMap.get(remoteAddress).isPrimary()){
                remoteDatagramDeliveryMap.put(remoteAddress, listener);
                outputChannels.put(remoteAddress, channelID);
            }
        }else if(remoteDatagramDeliveryMap.containsKey(remoteAddress)){
            remoteDatagramDeliveryMap.remove(remoteAddress);
            outputChannels.remove(remoteAddress);
        }
    }

    /**
     * Notify state listeners that a channel to a remote peer has generated an IO exception.
     * <p>
     *     Since this exception is irrecoverable, any state related to the described channel should be invalidated immediately.
     *     Further communcation with the remote peer should be done with a freshly initiated channel.
     * </p>
     * @param channelID A unique ID used by the listener to identify the channel
     * @param remotePeerAddress The address of the remote peer.
     */
    public void notifyChannelException(String channelID, String remotePeerAddress){
        for(NetworkEventListener listener : eventListeners){
            listener.notifyIrrecoverableException(channelID, remotePeerAddress);
        }
    }

    /**
     * Notify state listeners that a channel to a remote peer has successfully connected.
     * @param channelID A unique ID used by the listener to identify the channel
     * @param remotePeerAddress The address of the remote peer.
     */
    public void onNewConnection(String channelID, String remotePeerAddress){
        for(NetworkEventListener listener : eventListeners){
            listener.onNewConnection(channelID, remotePeerAddress);
        }
    }

    /**
     * Receive data from a remote peer.
     *
     * <p>
     *     Mainly, deliver the data to local applications that registered to receive from the identified sender.
     *     If this controller acts as a forwarder and the sender's address differs from the source address marked in the packet,
     *     register the sender as a relay for the source address and forward the packet to the current next hop.
     * </p>
     */
    public boolean receive(String remoteDevice, Packet pkt){
        Message msg = Message.obtain();
        msg.obj= new NetworkEvent(remoteDevice, pkt);
        handler.sendMessage(msg);
        return true;
    }

    private boolean process(String remoteDevice, Packet pkt){
        boolean success = false;
        if(pkt != null && pkt.getTtl() > 0){
            try{
                //Make sure source address is set.
                if(!Packet.isValidBDAddr(pkt.getSrcBDAddrStr())){
                    //Only update source address if it was not already set: sending devices may not know their physical address.
                    //If it is already set, this may be a multihop routing, so preserve the preset address
                    pkt.updateSrcBTAddr(remoteDevice);
                }else if(!pkt.getSrcBDAddrStr().equals(remoteDevice)){
                    //Update route to original peer
                    LocalInterfaceBridge.addProximity(pkt.getSrcBDAddrStr(), pkt.getSrcBDAddrStr());
                }
                if(isPathPropagation(pkt)){
                    Logger.logI("Path propagation pkt: self->" + remoteDevice + "->" + pkt.getSrcBDAddrStr());
                }
                if(isEchoPkt(pkt)){
                    //Echo tests never cross the controller, they are for troubleshooting purposes only.
                    if(interfaceConfigurationView.getLocalBDAddr() == null && echoPending){
                        if(!Packet.NULL_BD_ADDR_STR.equals(pkt.getDstBDAddrStr())){
                            interfaceConfigurationView.updateLocalBDAddr(pkt.getDstBDAddrStr());
                        }
                    }
                    return isEchoRequestPkt(pkt) ? sendEchoResponse(remoteDevice, pkt) : true;
                }else if(forwardingServiceEnabled() && !isLocalBDAddr(pkt.getDstBDAddrStr())){
                    success = forward(pkt, remoteDevice);
                }
                String srcDevAddress = pkt.getSrcBDAddrStr();
                if(!Packet.isValidBDAddr(srcDevAddress)){
                    srcDevAddress = remoteDevice;
                }
                if(srcDevAddress != null && !srcDevAddress.equals(remoteDevice) && remoteDatagramDeliveryMap.containsKey(remoteDevice)){
                    //Mark the sender as a relay/gateway for the source address in the packet, so the sender will act as an intermediary to the source.
                    RemoteDatagramDeliveryListener gateway = new RemoteDatagramForwardingListener(remoteDatagramDeliveryMap.get(remoteDevice));
                    registerRemoteDeliveryListener(srcDevAddress, outputChannels.get(remoteDevice), gateway);
                }
                Logger.logI(remoteDevice + " ->IN: /" + pkt.getSrcBDAddrStr() + " => " + pkt.getDstBDAddrStr());
                if(isDataPkt(pkt) && isLocalBDAddr(pkt.getDstBDAddrStr())){
                    //If forwarding is enabled, just forward and forget packets unless interception is enabled (This is similar to just routing for other peers).
                    //If interception is enabled, deliver each packet locally in addition to forwarding it (This is similar to mirroring a session to a remote peer).
                    if(localDatagramDeliveryListener != null) {
                        return localDatagramDeliveryListener.deliver(pkt);
                    }
                }
            }catch (AddressConversions.InvalidBluetoothAdddressException e){
                Logger.logD(e.getMessage());
            }
        }else if(pkt != null){
            Logger.logE("Expired packet? TTL="+ pkt.getTtl());
        }
        return success;
    }


    private boolean forward(Packet pkt, String receivedFrom) {
        try {
            String dst = pkt.getDstBDAddrStr();
            if(dst.equals(receivedFrom) || isLocalBDAddr(dst)){
               return false;
            }
            pkt.touchTTL();
            if(!sendDirect(pkt, dst, true)) {
                //Destination is not an adjacent host, find indirect route if any
                InternetLayerHeaders.AddressHeaders headers = InternetLayerHeaders.parseInetAddr(pkt.getData());
                dst = LocalInterfaceBridge.getRoute(headers.getTo());
                if (dst != null) {
                    return sendDirect(pkt, dst, true);
                }
                Logger.logE(String.format("No <<forwarding>> route for outgoing packet: %s => %s", pkt.getSrcBDAddrStr(), pkt.getDstBDAddrStr()) + ":: " + headers.getFrom() + "=>" + headers.getTo());
            }
        } catch (AddressConversions.InvalidBluetoothAdddressException e) {
            Logger.logE(e.getMessage());
        } catch (InternetLayerHeaders.InvalidDatagramException e) {
            Logger.logE(e.getMessage());
        }
        return false;
    }

    /**
     * Copy a buffer of data to a connection identified by the remote peer address and connection tag.
     * @param datagram the IP datagram to send
     */
    public void send(byte datagram[], boolean async){
        if(datagram == null){
            return;
        }
        Packet pkt = Packet.wrap(datagram);
        if(interfaceConfigurationView.getLocalBDAddr() == null){
            echoPending = true;
            pkt.setProtocol(Packet.PROTOCOL_PROXIMITY);
        }else{
            echoPending = false;
        }
        //Parse DST IP addr, find remote delivery listener
        try{
            InternetLayerHeaders.AddressHeaders addressHeaders = InternetLayerHeaders.parseInetAddr(pkt.getData());
            Logger.logI(String.format("Packet_for(%s)", addressHeaders.getTo()));
            String dst = LocalInterfaceBridge.getRoute(addressHeaders.getTo());
            if(dst != null && remoteDatagramDeliveryMap.containsKey(dst)) {
                sendDirect(pkt, dst, async);
            }else{
                Logger.logE(String.format("No route for outgoing packet: %s => %s", pkt.getSrcBDAddrStr(), pkt.getDstBDAddrStr()) + ":: " + addressHeaders.getFrom() + "=>" + addressHeaders.getTo());
            }
        }catch (AddressConversions.InvalidBluetoothAdddressException e){
            Logger.logE(e.getMessage());
        }catch (InternetLayerHeaders.InvalidDatagramException e2){
            Logger.logE(e2.getMessage());
        }
    }

    /**
     * Send marked packet to the specified receiver.
     * @param pkt The data parcel/packet to send.
     * @param remoteDeviceAddr The remote peer's physical address.
     */
    public boolean sendDirect(Packet pkt, String remoteDeviceAddr, boolean async){
        RemoteDatagramDeliveryListener writer = remoteDatagramDeliveryMap.get(remoteDeviceAddr);
        if(isInterfaceTest(pkt)){
            processInterfaceTest(pkt);
        }else if(writer != null){
            pkt.updateDstBTAddr(remoteDeviceAddr);
            try{
                Logger.logI(remoteDeviceAddr + " <-OUT::: /" + pkt.getSrcBDAddrStr() + " => " + pkt.getDstBDAddrStr());
            }catch (AddressConversions.InvalidBluetoothAdddressException e){
                Logger.logE(e.getMessage());
            }
            try {
                pkt.touchTTL();
                return writer.write(pkt, async);
            }catch (IOException e){
                Logger.logE(e.getMessage());
                //Invalidate current routes to this peer if an exception happens.
                writer.shutdown();
                remoteDatagramDeliveryMap.remove(remoteDeviceAddr);
                if(outputChannels.containsKey(remoteDeviceAddr)) {
                    notifyChannelException(outputChannels.get(remoteDeviceAddr), remoteDeviceAddr);
                    outputChannels.remove(remoteDeviceAddr);
                }
            }
        }
        return false;
    }

    public void pingProximities(){
        byte data[] = new byte[InternetLayerHeaders.MIN_IP_PACKET_SIZE];
        Packet pkt = Packet.wrap(data);
        pkt.setProtocol(Packet.PROTOCOL_PROXIMITY);
        for(String peer : LocalInterfaceBridge.getRoutes()){
            sendDirect(pkt, peer, true);
        }
    }

    /**
     * Echo responses are sent by the interface without communicating the queries with the "application".
     * <p>
     *     This is only used to test delays caused by the network, hoping to avoid queueing caused by request processing in the application.
     * </p>
     * @param remoteDevAddress The peer from which the packet was received
     * @param pkt The reference data parcel to respond to
     * @return True if the response was queued to be sent.
     */
    private boolean sendEchoResponse(String remoteDevAddress, Packet pkt){
        if(pkt == null){
            return false;
        }
        Packet resp = pkt.resp(pkt.getData()).touchTTL();
        resp.setProtocol(Packet.PROTOCOL_PROXIMITY_ACK);
        boolean ret = sendDirect(resp, remoteDevAddress, true);
        if(forwardingServiceEnabled()){
            for(String target : LocalInterfaceBridge.getRoutes()){
                if(target.equals(remoteDevAddress)){
                    continue;
                }
                Packet routeAdvPacket = Packet.copy(resp);
                routeAdvPacket.updateSrcBTAddr(target);
                routeAdvPacket.setProtocol(Packet.PROTOCOL_PATH_PROPAGATION);
                sendDirect(routeAdvPacket, remoteDevAddress, true);
            }
        }
        return ret;
    }

    /**
     * Interface test queries are made from the client to the network interface to test for delays between them.
     * <p>
     *     Since this interface may be encapsulating or tunneling high-level protocols,
     *     it helps to know the overhead incurred by going through this interface.
     * </p>
     * @param pkt The reference datagram.
     */
    private void processInterfaceTest(Packet pkt){
        if(pkt != null && localDatagramDeliveryListener != null){
            localDatagramDeliveryListener.deliver(pkt.resp(null));
        }
    }
}