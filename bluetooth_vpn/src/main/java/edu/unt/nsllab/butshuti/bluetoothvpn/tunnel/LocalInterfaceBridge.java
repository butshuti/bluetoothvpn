package edu.unt.nsllab.butshuti.bluetoothvpn.tunnel;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import edu.unt.nsllab.butshuti.bluetoothvpn.utils.Logger;
import edu.unt.nsllab.butshuti.bluetoothvpn.utils.net_protocols.NetUtils;

/**
 * Created by butshuti on 5/17/18.
 *
 * This class implements a bridge between local application sockets and the interface controller.
 * If multiple TCP/IP-like connections are opened to a remote peer, they will terminate locally into this bridge.
 * This design was aimed at establishing exactly one socket to the remote peer, regardless of how many sockets the application thinks there is.
 * There will be an instance of this class for each remote peer.
 * In the local {@link InterfaceController}, each instance of this bridge will appear with a unique IP address.
 * This bridge thus implements a pseudo DHCP service between remote adaptors and the local interface controller.
 */

public class LocalInterfaceBridge {
    public static class BridgeException extends IOException{
        public BridgeException(IOException e){
            super(e);
        }
        public BridgeException(String msg){
            super(msg);
        }
    }

    private static final Map<String, InetAddress> reservedAddresses = new HashMap<>();
    private static final Map<InetAddress, String> routes = new HashMap<>();
    private static InetAddress ifaceAddress = null;
    private static InetAddress INET_DEFAULT = null;

    private boolean defaultGateway = false;

    private static InetAddress getDefault() throws UnknownHostException {
        if(INET_DEFAULT == null){
            INET_DEFAULT = InetAddress.getByAddress(new byte[]{0, 0, 0, 0});
        }
        return INET_DEFAULT;
    }

    /**
     * Return addresses currently associated with remote peers.
     * @return
     */
    public static String[] getReservedAddresses(){
        if(reservedAddresses.isEmpty()){
            return new String[]{};
        }
        String ret[] = new String[reservedAddresses.size()];
        int offs = 0;
        for(InetAddress ia : reservedAddresses.values()){
            ret[offs++] = ia.getHostAddress();
        }
        return ret;
    }

    public static boolean isInitialized(){
        return ifaceAddress != null;
    }

    /**
     * Reset the IP associations.
     */
    public static void reset(){
        reservedAddresses.clear();
        ifaceAddress = null;
    }

    /**
     * Initialize the pool of IP addresses
     * @param interfaceAddress The base address to reserve for the interface controller.
     * @return True on success.
     */
    public static boolean initialize(InetAddress interfaceAddress){
        if(ifaceAddress == null || (!ifaceAddress.equals(interfaceAddress))){
            reservedAddresses.clear();
        }
        ifaceAddress = interfaceAddress;
        return ifaceAddress != null && reservedAddresses.isEmpty();
    }

    /**
     * Return the IP address associated with the remote peer's address.
     * @param devAddress The remote peer's address
     * @return The associated address, or NULL if none.
     */
    private static InetAddress getReservedAddress(String devAddress){
        if(devAddress == null){
            return null;
        }
        synchronized (reservedAddresses){
            return reservedAddresses.get(devAddress);
        }
    }

    /**
     * Generate an IP address for a new peer association.
     * @param devAddress The remote peer's address
     * @param isDefault True if default routes will resolve to the peer.
     * @return
     * @throws BridgeException
     */
    private static InetAddress nextInetAddr(String devAddress, boolean isDefault) throws BridgeException {
        synchronized (reservedAddresses){
            if(ifaceAddress == null){
                throw new BridgeException("Interface bridge not initialized.");
            }
            InetAddress nextAddr = null;
            if(isDefault){
                nextAddr = ifaceAddress;
            }else if(reservedAddresses.containsKey(devAddress) && reservedAddresses.get(devAddress) != null){
                return reservedAddresses.get(devAddress);
            }else{
                byte[] quad = ifaceAddress.getAddress();
                quad[2] = (byte)((devAddress.hashCode() >> 16) & 0x07F);
                quad[3] = (byte) (devAddress.hashCode() & 0x07F);
                try {
                    nextAddr = NetUtils.getResolvableAddress(NetUtils.getLocalBTReservedIfaceConfig(), devAddress);
                } catch (UnknownHostException e) {
                    Logger.logE(e.getMessage());
                }
            }
            if(nextAddr == null){
                throw new BridgeException("Unable to assign requested address.");
            }else if(reservedAddresses.values().contains(nextAddr)){
                throw new BridgeException("Cannot reassign address " + nextAddr);
            }
            reservedAddresses.put(devAddress, nextAddr);
            return nextAddr;
        }
    }

    public static void addProximity(String target, String gateway){
        try {
            addRoute(nextInetAddr(gateway, false), gateway);
            addRoute(nextInetAddr(target, false), gateway);
        } catch (BridgeException e) {
            e.printStackTrace();
        }
    }

    /**
     * {@see {@link LocalInterfaceBridge#addGateway(String, boolean)}}
     * @param remoteDevAddress
     * @throws BridgeException
     */
    public static void addGateway(String remoteDevAddress) throws BridgeException {
        addGateway(remoteDevAddress, false);
    }

    public static void deleteGateway(String remoteAddress){
        if(routes != null && routes.containsKey(remoteAddress)){
            routes.remove(remoteAddress);
        }
    }

    /**
     * Update routes with a new connected peer.
     * @param remoteDevAddress  The remote peer's address
     * @param isDefault True if only one peer connection is assumed at any given time.
     * @throws BridgeException
     */
    private static void addGateway(String remoteDevAddress, boolean isDefault) throws BridgeException {
        InetAddress addr = nextInetAddr(remoteDevAddress, isDefault);
        addRoute(addr, remoteDevAddress);
        if(isDefault){
            try {
                addRoute(getDefault(), remoteDevAddress);
            } catch (UnknownHostException e) {
                throw new BridgeException(e);
            }
        }
    }

    private static boolean addRoute(InetAddress inetAddress, String bdAddr){
        if(routes.containsKey(inetAddress) && !routes.get(inetAddress).equals(bdAddr)){
            Logger.logE(String.format("Competing routes for %s: (current: %s, new: %s). Failing: must invalidate current before updating.",
                    inetAddress, routes.get(inetAddress), bdAddr));
            return false;
        }
        Logger.logI(String.format("Registering new route [%s->%s]", inetAddress, bdAddr));
        routes.put(inetAddress, bdAddr);
        return routes.get(inetAddress).equals(bdAddr);
    }

    public static String getRoute(InetAddress inetAddress){
        Logger.logE("ROUTES: " + routes);
        if(routes.containsKey(inetAddress)){
            return routes.get(inetAddress);
        }
        try{
            InetAddress defaultRoute = getDefault();
            if(routes.containsKey(defaultRoute)){
                return routes.get(defaultRoute);
            }
        }catch (UnknownHostException e){
            Logger.logE(e.getMessage());
        }
        return null;
    }

    public static Collection<String> getRoutes(){
        return routes.values();
    }
}