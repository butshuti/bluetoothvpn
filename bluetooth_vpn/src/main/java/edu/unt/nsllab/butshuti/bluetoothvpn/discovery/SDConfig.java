package edu.unt.nsllab.butshuti.bluetoothvpn.discovery;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

import edu.unt.nsllab.butshuti.bluetoothvpn.R;
import edu.unt.nsllab.butshuti.bluetoothvpn.utils.Logger;

import static android.preference.PreferenceManager.getDefaultSharedPreferences;

/**
 * Created by butshuti on 9/8/18.
 */

public class SDConfig {
    public static final String CONFIGURED_PEER_SSID = "CONFIGURED_PEER_SSID";
    public static final String CONFIGURED_PEER_SHARED_KEY = "CONFIGURED_PEER_SHARED_KEY";
    public static final String CONFIGURED_PEER_NAME = "NAME";
    public static final String SELF_BT_SSID = "LOCAL_BT_ADDR";

    public String getDefaultRoutingMode() {
        return defaultRoutingMode;
    }

    private boolean clientModeEnabled, serverModeEnabled, defaultRoutesEnabled;
    private int maxNumServers;
    private String defaultRoutingMode;

    Context ctx;

    public SDConfig(Context applicationCtx){
        ctx = applicationCtx;
        refreshNetConfing();
    }

    public boolean isClientModeEnabled() {
        return clientModeEnabled;
    }

    public boolean isServerModeEnabled() {
        return serverModeEnabled;
    }

    public boolean isDefaultRoutesEnabled() {
        return defaultRoutesEnabled;
    }

    public int getMaxNumServers() {
        return maxNumServers;
    }

    public String getFriendlyName(BluetoothDevice device){
        if(device != null){
            if(device.getAddress().equals(getConfiguredPeerSSID())){
                return getConfiguredPeerName();
            }
            return device.getName() != null ? device.getName() : device.getAddress();
        }
        return "Unnamed device";
    }

    public String getConfiguredPeerSSID() {
        SharedPreferences sharedPref = getDefaultSharedPreferences(ctx);
        return sharedPref.getString(CONFIGURED_PEER_SSID, "");
    }

    public String getConfiguredPeerName() {
        SharedPreferences sharedPref = getDefaultSharedPreferences(ctx);
        return sharedPref.getString(CONFIGURED_PEER_NAME, getConfiguredPeerSSID());
    }

    public void resetConfiguredPeer(){
        SharedPreferences sharedPref = getDefaultSharedPreferences(ctx);
        sharedPref.edit().remove(CONFIGURED_PEER_SSID).commit();
    }

    public String getConfiguredLocalNetDevID() {
        SharedPreferences sharedPref = getDefaultSharedPreferences(ctx);
        return sharedPref.getString(SELF_BT_SSID, null);
    }

    public void setConfiguredLocalNetDevID(String localBDAddr) {
        SharedPreferences sharedPref = getDefaultSharedPreferences(ctx);
        sharedPref.edit().putString(SELF_BT_SSID, localBDAddr).commit();
    }

    public void applyConfig(JSONObject config) {
        Logger.logI("new_config: " + config);
        String devAddress = null, devName = null;
        try {
            devAddress = config.getString(CONFIGURED_PEER_SSID);
            devName = config.getString(CONFIGURED_PEER_NAME);
        } catch (JSONException e) {
            Logger.logE(e.getMessage());
        }
        SharedPreferences.Editor editor = getDefaultSharedPreferences(ctx).edit();
        if(devAddress != null){
            editor.putString(CONFIGURED_PEER_SSID, devAddress);
        }
        if(devName != null){
            editor.putString(CONFIGURED_PEER_NAME, devName);
        }
        editor.commit();
    }

    public boolean verifyConfiguredNetDevID(String address, String name) {
        return address != null && address.equals(getConfiguredPeerSSID());
    }

    public static JSONObject bundleDescr(String devName, String devAddress){
        try {
            JSONObject obj = new JSONObject();
            obj.put(CONFIGURED_PEER_SSID, devAddress);
            obj.put(CONFIGURED_PEER_NAME, devName != null ? devName : devAddress);
            obj.put(CONFIGURED_PEER_SHARED_KEY, "");
            return obj;
        } catch (Exception e) {
            return null;
        }
    }

    public void refreshNetConfing(){
        SharedPreferences sharedPref = getDefaultSharedPreferences(ctx);
        try{
            clientModeEnabled = sharedPref.getBoolean(ctx.getString(R.string.pref_key_enable_client_mode), false);
            serverModeEnabled = sharedPref.getBoolean(ctx.getString(R.string.pref_key_enable_passive_mode), false);
            defaultRoutesEnabled = sharedPref.getBoolean(ctx.getString(R.string.pref_key_install_default_routes), false);
            defaultRoutingMode = sharedPref.getString(ctx.getString(R.string.pref_key_routing_mode), "----");
            maxNumServers = Integer.valueOf(sharedPref.getString(ctx.getString(R.string.pref_key_num_active_connections), "-1"));
        }catch (RuntimeException e){
            Logger.logE(e.getMessage());
        }
    }

}
