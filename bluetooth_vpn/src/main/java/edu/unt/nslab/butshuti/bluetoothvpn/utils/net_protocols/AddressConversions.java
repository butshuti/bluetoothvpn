package edu.unt.nslab.butshuti.bluetoothvpn.utils.net_protocols;


import java.util.Arrays;

import edu.unt.nslab.butshuti.bluetoothvpn.utils.NumberUtils;

/**
 * Created by butshuti on 5/21/18.
 */

public class AddressConversions {
    public static final short BD_ADDR_SIZE = 6;

    public static class InvalidBluetoothAdddressException extends Exception{
        public InvalidBluetoothAdddressException(String msg){
            super(msg);
        }

        public InvalidBluetoothAdddressException(Exception e){
            super(e);
        }
    }

    public static byte[] stringToBDAddr(String addr) throws InvalidBluetoothAdddressException{
        byte[] ret;
        String toks[];
        if(addr == null){
            throw new InvalidBluetoothAdddressException("Malformed BD_ADDR: " + addr);
        }
        toks = addr.split(":");
        if(toks.length != BD_ADDR_SIZE){
            throw new InvalidBluetoothAdddressException("Malformed BD_ADDR: " + addr);
        }
        ret = new byte[BD_ADDR_SIZE];
        for(int offs = 0; offs < BD_ADDR_SIZE; offs++){
            try{
                short val = (short)Math.abs(Short.valueOf(toks[offs], 16));
                ret[offs] = (byte)(val & 0x0FF);
            }catch (NumberFormatException e){
                throw new InvalidBluetoothAdddressException(e);
            }
        }
        return ret;
    }

    public static String BDAddrToStr(byte addr[]) throws InvalidBluetoothAdddressException{
        if(addr == null || addr.length != BD_ADDR_SIZE){
            throw new InvalidBluetoothAdddressException("Invalid BDAddr: " + addr == null ? "NULL" : Arrays.toString(addr));
        }
        StringBuilder sb = new StringBuilder(NumberUtils.byteToHexStr(addr[0]));
        for(int i=1; i<BD_ADDR_SIZE; i++){
            sb.append(":");
            sb.append(NumberUtils.byteToHexStr(addr[i]));
        }
        return sb.toString();
    }

}
