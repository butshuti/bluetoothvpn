package edu.unt.nslab.butshuti.bluetoothvpn.data.view_models;

import android.app.Application;
import android.arch.lifecycle.AndroidViewModel;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;

import java.net.Inet4Address;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import edu.unt.nslab.butshuti.bluetoothvpn.data.objects.Peer;
import edu.unt.nslab.butshuti.bluetoothvpn.data.objects.ServiceStatusWrapper;

/**
 * Created by butshuti on 9/15/18.
 */

public class PeersListViewModel extends AndroidViewModel {

    public PeersListViewModel(Application application) {
        super(application);
    }

    public LiveData<List<Peer>> getPeers() {
        return Repository.instance().getPeers();
    }
}
