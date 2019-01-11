package edu.unt.nslab.butshuti.bluetoothvpn.iptools.sdquery;

import edu.unt.nslab.butshuti.bluetoothvpn.utils.Logger;

/**
 * Created by butshuti on 9/10/18.
 *
 */
final class EchoDataParcel {
    static final int MAX_SIZE = 256;
    long cookie;
    String dataStr;

    EchoDataParcel(long cookie, String dataStr) {
        this.cookie = cookie;
        String cookieStr = String.valueOf(cookie);
        this.dataStr = dataStr.substring(0, Math.min(dataStr.length(), MAX_SIZE - cookieStr.length()));
    }

    long getSessionCookie() {
        return cookie;
    }

    String getDataStr() {
        return dataStr;
    }

    byte[] toBytes() {
        return (String.valueOf(cookie) + ";" + (dataStr == null ? "-" : dataStr)).getBytes();
    }

    static EchoDataParcel fromBytes(byte[] arg, int len) {
        if (arg != null) {
            String toks[] = new String(arg, 0, len).split(";");
            if (toks.length == 2) {
                try {
                    return new EchoDataParcel(Long.valueOf(toks[0]), toks[1]);
                } catch (NumberFormatException e) {
                    Logger.logE(e.getMessage());
                }
            }
        }
        return new EchoDataParcel(0, "");
    }
}
