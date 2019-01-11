package edu.unt.nslab.butshuti.bluetoothvpn.utils.net_protocols;

import android.content.Context;
import com.google.common.net.InetAddresses;
import java.io.IOException;
import java.math.BigInteger;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Enumeration;

import edu.unt.nslab.butshuti.bluetoothvpn.utils.Logger;


/**
 *Created by butshuti on 5/21/18.
 *
 */
public class NetUtils {

    public static class AddrConfig{
        String ipAddr;
        public int prefixLen;

        AddrConfig(String addr, int prefix){
            ipAddr = addr;
            prefixLen = prefix;
        }

        public String getIpAddr(){
            return ipAddr;
        }
    }

    private final static String p2pInt = "p2p-p2p0";
    private final static String wlan0 = "wlan";
    private final static String tun = "tun";
    public static final String NET_SD_ADDRESS = "SD_ADDRESS";
    public static final String NET_DEV_ADDRESS = "DEV_ADDRESS";
    public static final String NET_TYPE = "NET_TYPE";
    public static final String NET_TYPE_NONE = "NET_TYPE_NONE";

    private static InetAddress getWifiDirectBroadcastAddress() throws IOException {
        return InetAddress.getByName("192.168.49.255");
    }

    public static InetAddress getAddressForString(String arg) throws IOException{
        try{
            return InetAddresses.forString(arg);
        }catch(IllegalArgumentException e){
            throw new IOException(e);
        }
    }

    private static InetAddress toInetAddress(int ipAddr){
        byte[] quads = new byte[4];
        for (int k = 0; k < 4; k++)
            quads[k] = (byte) ((ipAddr >> k * 8) & 0xFF);
        try {
            return InetAddress.getByAddress(quads);
        } catch (UnknownHostException e) {
            Logger.logE(e.getMessage());
        }
        return null;
    }

    public static String getIPStringFromInt(int ipAddr){
        //Get address bytes
        byte[] bytes = BigInteger.valueOf(ipAddr).toByteArray();
        //reverse array to network ordered bytes
        for(int i=0; i<bytes.length/2; i++){
            byte temp = bytes[i];
            bytes[i] = bytes[bytes.length -1 - i];
            bytes[bytes.length -1 - i] = temp;
        }
        return getIPStringFromBytes(bytes);
    }

    public static String getIPStringFromBytes(byte[] bytes){
        String ip;
        try {
            ip = InetAddress.getByAddress(bytes).getHostAddress();
        } catch (UnknownHostException e) {
            ip = "/N/A";
        }
        return ip;
    }

    public static InetAddress getResolvableAddress(AddrConfig config, String key) throws UnknownHostException {
        InetAddress baseAddress = InetAddresses.forString(config.ipAddr);
        byte quad[] = baseAddress.getAddress();
        int keyCode = key.hashCode();
        int offs = config.prefixLen;
        for(int i=1; i<config.prefixLen/8; i++){
            quad[quad.length - i] = (byte)((keyCode >> ((offs++) * 8)) & 0x0FF);
        }
        return InetAddress.getByAddress(quad);
    }

    public static AddrConfig getLocalBTReservedIfaceConfig(){
        AddrConfig ret = new AddrConfig("3.3.0.0", 16);
        return ret;
    }

    public static String getLocalWifiAddress(Context ctx){
        return getLocalIPAddress(".*"+wlan0+".*");
    }

    public static String getLocalTUNIfaceAddress(){
        return getLocalIPAddress(".*" +tun+ ".*");
    }

    public static String getLocalP2PIPAddress(){
        return getLocalIPAddress(".*" +p2pInt+ ".*");
    }

    public static String getLocalIPAddress(String regex) {
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    String iface = intf.getName();
                    if(iface.matches(regex)){
                        if (inetAddress instanceof Inet4Address) {
                            return NetUtils.getIPStringFromBytes(inetAddress.getAddress());
                        }
                    }
                }
            }
        } catch (SocketException ex) {
            Logger.logE(ex.getLocalizedMessage());
        } catch (NullPointerException ex) {
            Logger.logE(ex.getLocalizedMessage());
        }
        return null;
    }
    }
