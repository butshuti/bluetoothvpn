package edu.unt.nsllab.butshuti.bluetoothvpn.utils;


import edu.unt.nsllab.butshuti.bluetoothvpn.utils.net_protocols.AddressConversions;

/**
 * Created by butshuti on 5/21/18.
 */

public class NumberUtils {
    private static final char[] DEC_TO_HEX = "0123456789ABCDEF".toCharArray();

    public static String byteToHexStr(byte val) throws AddressConversions.InvalidBluetoothAdddressException {
        return decToHexDigit((val >> 4) & 0x0F) + "" + decToHexDigit(val & 0x0F);
    }

    public static char decToHexDigit(int digit) throws AddressConversions.InvalidBluetoothAdddressException {
        if(digit >= 0 && digit <= 15){
            return DEC_TO_HEX[digit];
        }
        throw new AddressConversions.InvalidBluetoothAdddressException("Invalid hex digit range: " + digit);
    }
}
