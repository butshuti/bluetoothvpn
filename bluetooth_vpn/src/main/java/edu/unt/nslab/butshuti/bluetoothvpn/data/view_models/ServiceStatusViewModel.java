package edu.unt.nslab.butshuti.bluetoothvpn.data.view_models;

import android.app.Application;
import android.arch.lifecycle.AndroidViewModel;
import android.arch.lifecycle.LiveData;
import android.support.annotation.NonNull;

import edu.unt.nslab.butshuti.bluetoothvpn.data.objects.ServiceStatusWrapper;

public class ServiceStatusViewModel extends AndroidViewModel{


    public ServiceStatusViewModel(@NonNull Application application) {
        super(application);
    }

    public LiveData<ServiceStatusWrapper> getServiceStatus(){
        return Repository.instance().getServiceStatus();
    }
}
