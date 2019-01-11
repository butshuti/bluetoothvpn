package edu.unt.nsllab.butshuti.bluetoothvpn.utils.net_protocols;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Created by butshuti on 5/21/18.
 */

public class InternetLayerHeaders {
    public static final int MIN_IP_PACKET_SIZE = 20;

    public static class InvalidDatagramException extends Exception{
        public InvalidDatagramException(String msg){
            super(msg);
        }

        public InvalidDatagramException(Exception e){
            super(e);
        }
    }

    public static class AddressHeaders{
        InetAddress from, to;

        public InetAddress getFrom() {
            return from;
        }

        public InetAddress getTo() {
            return to;
        }

        private AddressHeaders(InetAddress from, InetAddress to){
            this.from = from;
            this.to = to;
        }
    }

    public static AddressHeaders parseInetAddr(byte buf[]) throws InvalidDatagramException {
        if(buf.length > MIN_IP_PACKET_SIZE){
            try{
                InetAddress from = InetAddress.getByAddress(new byte[]{buf[12], buf[13], buf[14], buf[15]});
                InetAddress to = InetAddress.getByAddress(new byte[]{buf[16], buf[17], buf[18], buf[19]});
                return new AddressHeaders(from, to);
            }catch (UnknownHostException e){
                throw new InvalidDatagramException(e);
            }
        }
        throw new InvalidDatagramException("Invalid IP pkt: length: " + buf.length);
    }
}
