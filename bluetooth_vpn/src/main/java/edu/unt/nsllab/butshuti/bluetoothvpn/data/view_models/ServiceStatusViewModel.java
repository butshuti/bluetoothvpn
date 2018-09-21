package edu.unt.nsllab.butshuti.bluetoothvpn.data.view_models;

import android.app.Application;
import android.arch.lifecycle.AndroidViewModel;
import android.arch.lifecycle.LiveData;
import android.support.annotation.NonNull;

import edu.unt.nsllab.butshuti.bluetoothvpn.data.objects.ServiceStatusWrapper;

public class ServiceStatusViewModel extends AndroidViewModel{


    public ServiceStatusViewModel(@NonNull Application application) {
        super(application);
    }

    public LiveData<ServiceStatusWrapper> getServiceStatus(){
        return Repository.instance().getServiceStatus();
    }
}
