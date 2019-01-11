package edu.unt.nslab.butshuti.bluetoothvpn.datagram;

import edu.unt.nslab.butshuti.bluetoothvpn.utils.net_protocols.AddressConversions.InvalidBluetoothAdddressException;
import edu.unt.nslab.butshuti.bluetoothvpn.utils.net_protocols.InternetLayerHeaders;
import edu.unt.nslab.butshuti.bluetoothvpn.utils.Logger;

import static edu.unt.nslab.butshuti.bluetoothvpn.utils.net_protocols.AddressConversions.stringToBDAddr;
import static edu.unt.nslab.butshuti.bluetoothvpn.utils.net_protocols.AddressConversions.BDAddrToStr;
import static edu.unt.nslab.butshuti.bluetoothvpn.utils.net_protocols.AddressConversions.BD_ADDR_SIZE;

/**
 * Created by butshuti on 5/18/18.
 */

public class Packet {
    public static final byte PROTOCOL_DATA = 0x0D;
    public static final byte PROTOCOL_ITEST = 0x02;
    public static final byte PROTOCOL_PROXIMITY = 0x03;
    public static final byte PROTOCOL_PROXIMITY_ACK = 0x04;
    public static final byte PROTOCOL_PATH_PROPAGATION = 0x05;
    public static final byte PROTOCOL_TRACEROUTE = 0x06;

    private static final byte DEFAULT_TTL = (byte)64;

    public static final byte NULL_BD_ADDR[] = new byte[]{0, 0, 0, 0, 0, 0};
    public static final String NULL_BD_ADDR_STR = uncheckedParseDBAddr(NULL_BD_ADDR);

    private byte[] srcBDAddr, dstBDAddr;
    private byte buf[]; // IP packet
    private byte protocol, ttl;

    public byte[] getData(){
        return buf;
    }

    byte[] getSrcBDAddr(){
        return srcBDAddr;
    }

    byte[] getDstBDAddr(){
        return dstBDAddr;
    }

    public String getSrcBDAddrStr() throws InvalidBluetoothAdddressException{
        return parseBDAddr(srcBDAddr);
    }

    public String getDstBDAddrStr() throws InvalidBluetoothAdddressException{
        return parseBDAddr(dstBDAddr);
    }

    public boolean updateSrcBTAddr(String addr){
        try{
            srcBDAddr = stringToBDAddr(addr);
            return true;
        }catch (InvalidBluetoothAdddressException e){
            Logger.logE(e.getMessage());
        }
        return false;
    }

    public boolean updateDstBTAddr(String addr){
        try{
            dstBDAddr = stringToBDAddr(addr);
            return true;
        }catch (InvalidBluetoothAdddressException e){
            Logger.logE(e.getMessage());
        }
        return false;
    }

    public void setProtocol(int protocol){
        this.protocol = (byte)protocol;
    }

    public byte getProtocol(){
        return protocol;
    }

    public byte getTtl(){
        return ttl;
    }

    public Packet touchTTL(){
        if(protocol == PROTOCOL_PROXIMITY || protocol == PROTOCOL_PROXIMITY_ACK || protocol == PROTOCOL_PATH_PROPAGATION){
            ttl = 1;
        }else {
            ttl -= 1;
        }
        return this;
    }

    private static String parseBDAddr(byte addr[]) throws InvalidBluetoothAdddressException {
        if(addr == null){
            throw new InvalidBluetoothAdddressException("NULL address");
        }
        if(addr.length != BD_ADDR_SIZE){
            throw new InvalidBluetoothAdddressException(String.format("Invalid BD_ADDR size: %d; expected: %d", addr.length, BD_ADDR_SIZE));
        }
        /**
         * It would be nice to have a formatter here, but since this method is called way too many times, we cannot afford it.
         * String.format("%02X:%02X:%02X:%02X:%02X:%02X", addr[0], addr[1], addr[2], addr[3], addr[4], addr[5]) is the format.
         */
        return BDAddrToStr(addr);
    }

    private static String uncheckedParseDBAddr(byte addr[]){
        try {
            return parseBDAddr(addr);
        } catch (InvalidBluetoothAdddressException e) {
            Logger.logE(e.getMessage());
        }
        return null;
    }

    public static boolean isValidBDAddr(String addr){
        if(!NULL_BD_ADDR_STR.equals(addr)){
            try {
                stringToBDAddr(addr);
                return true;
            } catch (InvalidBluetoothAdddressException e) {
                return false;
            }
        }
        return false;
    }

    public Packet resp(byte data[]){
        if(data == null){
            //Fake payload
            data = NULL_BD_ADDR_STR.getBytes();
        }
        return new Packet(protocol, DEFAULT_TTL, dstBDAddr, srcBDAddr, data);
    }

    public static Packet copy(Packet ref){
        return new Packet(ref.protocol, ref.ttl, ref.dstBDAddr, ref.srcBDAddr, ref.getData().clone());
    }

    public static Packet wrap(byte data[]){
        byte protocol = PROTOCOL_DATA;
        if(data == null){
            //Fake payload
            data = NULL_BD_ADDR_STR.getBytes();
            protocol = PROTOCOL_PROXIMITY;
        }else{
            try {
                InternetLayerHeaders.AddressHeaders addressHeaders = InternetLayerHeaders.parseInetAddr(data);
                if(addressHeaders.getTo().getHostAddress().endsWith(".3.3")){
                    protocol = PROTOCOL_ITEST;
                }
            } catch (InternetLayerHeaders.InvalidDatagramException e) {
                e.printStackTrace();
            }
        }
        return new Packet(protocol, DEFAULT_TTL, NULL_BD_ADDR, NULL_BD_ADDR, data);
    }

    Packet(byte protocol, byte ttl, byte srcBDAddr[], byte dstBDAddr[], byte data[]){
        this.protocol = protocol;
        this.ttl = ttl;
        this.srcBDAddr = srcBDAddr;
        this.dstBDAddr = dstBDAddr;
        this.buf = data;
    }
}