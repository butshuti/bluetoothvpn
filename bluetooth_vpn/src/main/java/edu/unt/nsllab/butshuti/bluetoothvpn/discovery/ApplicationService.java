package edu.unt.nsllab.butshuti.bluetoothvpn.discovery;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.UUID;

/**
 * Created by butshuti on 4/26/18.
 */

public class ApplicationService {
    /**
     * Why are SDP UUIDs reversed????
     * @return
     */
    public static UUID getReversedServiceUUID(int idx){
        UUID serviceUUID = getServiceUUID(idx);
        ByteBuffer byteBuffer = ByteBuffer.allocate(16);
        byteBuffer.putLong(serviceUUID.getLeastSignificantBits());
        byteBuffer.putLong(serviceUUID.getMostSignificantBits());
        byteBuffer.rewind();
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        return new UUID(byteBuffer.getLong(), byteBuffer.getLong());
    }

    public static String getServiceName(int idx){
        return String.format("%05Xcpr_monitor_service%05xUUIDGEN", idx, idx);
    }

    public static String getEchoServiceName(){
        return "echo_service";
    }

    public static UUID getServiceUUID(int idx){
        return UUID.nameUUIDFromBytes(String.format("%05X%s%05x", idx, getServiceName(idx), idx).getBytes());
    }
}
