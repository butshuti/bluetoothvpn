package edu.unt.nsllab.butshuti.bluetoothvpn.data.view_models;

import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MediatorLiveData;
import android.arch.lifecycle.MutableLiveData;
import android.arch.lifecycle.Observer;
import android.support.annotation.Nullable;

import java.util.List;

import edu.unt.nsllab.butshuti.bluetoothvpn.data.objects.Peer;
import edu.unt.nsllab.butshuti.bluetoothvpn.data.objects.ServiceStatusWrapper;

public class Repository implements Observer<ServiceStatusWrapper> {
    private static final Repository INSTANCE = new Repository();

    private final MediatorLiveData<ServiceStatusWrapper> serviceStatusDescriptionMediatorLiveData = new MediatorLiveData<>();
    private final MutableLiveData<List<Peer>> peersLiveData = new MutableLiveData<>();

    private Repository() {}

    public static Repository instance() {
        return INSTANCE;
    }

    public LiveData<ServiceStatusWrapper> getServiceStatus() {
        return serviceStatusDescriptionMediatorLiveData;
    }

    public LiveData<List<Peer>> getPeers(){
        return peersLiveData;
    }

    public void addDataSource(LiveData<ServiceStatusWrapper> data) {
        serviceStatusDescriptionMediatorLiveData.addSource(data, this);
    }

    public void removeDataSource(LiveData<ServiceStatusWrapper> data) {
        serviceStatusDescriptionMediatorLiveData.removeSource(data);
    }

    @Override
    public void onChanged(@Nullable ServiceStatusWrapper serviceStatusWrapper) {
        if(serviceStatusWrapper != null) {
            serviceStatusDescriptionMediatorLiveData.setValue(serviceStatusWrapper);
            peersLiveData.setValue(serviceStatusWrapper.getClients());
        }
    }
}
