package edu.unt.nsllab.butshuti.bluetoothvpn.vpn;

import android.content.Intent;
import android.net.VpnService;

public class BTVpnService extends VpnService {

    public static BTVpnService instance = null;

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
        stopSelf();
    }

    private void prepare(){
        ((BTVPNApplication)getApplication()).prepare(new Builder());
    }

    public static BTVpnService getInstance(){
        return instance;
    }

}
