package edu.unt.nslab.butshuti.bluetoothvpn.vpn;

import android.content.Intent;
import android.net.VpnService;

import edu.unt.nslab.butshuti.bluetoothvpn.tunnel.VPNFDController;

public class BTVpnService extends VpnService {

    public static BTVpnService instance = null;
    private VPNFDController vpnfdController;

    @Override
    public void onCreate() {
        super.onCreate();
        instance =this;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId){
        prepare();
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    private void prepare(){
        vpnfdController = ((BTVPNApplication)getApplication()).prepare(new Builder());
    }

    public static BTVpnService getInstance(){
        return instance;
    }

    public boolean isActive(){
        return vpnfdController != null;
    }

    @Override
    public void onRevoke(){
        if(vpnfdController != null){
            vpnfdController.terminate();
        }
        stopSelf();
    }

}
