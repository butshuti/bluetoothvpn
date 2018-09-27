package edu.unt.nsllab.butshuti.bluetoothvpn.datagram;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;


import edu.unt.nsllab.butshuti.bluetoothvpn.utils.Logger;

import static edu.unt.nsllab.butshuti.bluetoothvpn.utils.net_protocols.AddressConversions.BD_ADDR_SIZE;


/**
 * Created by butshuti on 1/3/18.
 */
public abstract class WireInterface {

    /**
     * Preamble format:
     *      ______________________________________
     *         |         |       |       |       |
     *         |----------------------------------
     *      0  | Protocl | TTL   |  pkt_size     |
     *         |----------------------------------
     *      4  |     Source BD_ADDR              |
     *         <                 -----------------
     *      8  |                 |               |
     *         |------------------               >
     *      12 |     Destination BD_ADDR         |
     *         |----------------------------------
     *      16 |         Preamble XOR            |
     *      -- |---------------------------------|
     */
    public final static int PREAMBLE_SIZE = 20;
    private final byte staticPreambleByteArr[] = new byte[PREAMBLE_SIZE];
    private byte savedPreamble[] = null;


    protected abstract int read(byte buffer[], int max) throws IOException;

    public final synchronized Packet readMultipartNext() throws IOException {
        int size;
        byte preamble[];
        if(savedPreamble != null){
            preamble = savedPreamble;
            size = preamble.length;
        }else{
            preamble = staticPreambleByteArr;
            size = read(preamble, PREAMBLE_SIZE);
        }
        if(size == PREAMBLE_SIZE){
            ByteBuffer byteBuffer = ByteBuffer.wrap(preamble);
            byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
            byte protocol = byteBuffer.get();
            byte ttl = byteBuffer.get();
            short pktSize = byteBuffer.getShort();
            byte srcBDAddr[] = new byte[BD_ADDR_SIZE];
            byte dstBDAddr[] = new byte[BD_ADDR_SIZE];
            byteBuffer.get(srcBDAddr, 0, BD_ADDR_SIZE);
            byteBuffer.get(dstBDAddr, 0, BD_ADDR_SIZE);
            short preambleXor = byteBuffer.getShort();
            short dataXor = byteBuffer.getShort();
            if (pktSize <= 0 || preambleXor != calcPktPreambleXor(preamble)) {
                IOException e = new IOException(String.format("Invalid or corrupted preamble: expected %d, got %d; sz=%d / [%s]", preambleXor, calcPktPreambleXor(preamble), pktSize, Arrays.toString(preamble)));
                Logger.logE(e.getMessage());
                throw e;
            }
            byte data[] = new byte[pktSize];
            int embeddedSize = read(data, pktSize);
            if(embeddedSize  == pktSize && dataXor == calcBufXor(data, pktSize)){
                savedPreamble = null;
                return new Packet(protocol, ttl, srcBDAddr, dstBDAddr, data);
            }else if(embeddedSize == 0){
                //Not enough data available, save preamble for next read.
                savedPreamble = preamble;
            }else{
                Logger.logE("Read packet does not match preamble");
            }
        }
        return null;
    }

    public final static byte[] toBytes(Packet pkt){
        if(pkt != null){
            byte ret[] = new byte[pkt.getData().length + PREAMBLE_SIZE];
            ByteBuffer byteBuffer = ByteBuffer.wrap(ret);
            byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
            byteBuffer.put(pkt.getProtocol());
            byteBuffer.put(pkt.getTtl());
            byteBuffer.putShort((short)pkt.getData().length);
            byteBuffer.put(pkt.getSrcBDAddr(), 0, BD_ADDR_SIZE);
            byteBuffer.put(pkt.getDstBDAddr(), 0, BD_ADDR_SIZE);
            short preXor = calcPktPreambleXor(ret);
            short dataXor = calcBufXor(pkt.getData(), pkt.getData().length);
            byteBuffer.putShort(preXor);
            byteBuffer.putShort(dataXor);
            byteBuffer.put(pkt.getData(), 0, pkt.getData().length);
            return ret;
        }
        return new byte[]{};
    }

    private final static short calcPktPreambleXor(byte buf[]){
        return calcBufXor(buf, PREAMBLE_SIZE - 4);
    }

    private final static short calcBufXor(byte buf[], int size){
        if(buf == null || buf.length < size){
            return -1;
        }
        short ret = 0;
        int offs;
        for(offs = 0; offs < size/4; offs++){
            int cur = offs * 4;
            ret ^= ((byte) (buf[cur] & 0xFF)) ^ ((byte) (buf[cur+1] & 0xFF))
                    ^ ((byte) (buf[cur+2] & 0xFF)) ^ ((byte) (buf[cur+3] & 0xFF));
        }
        for(int cur = offs * 4; cur < size; cur++){
            ret ^= ((byte) (buf[cur] & 0xFF));
        }
        return ret;
    }
}
