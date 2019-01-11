package edu.unt.nslab.butshuti.bluetoothvpn.data.view_models;

import android.app.Application;
import android.arch.lifecycle.AndroidViewModel;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.support.annotation.NonNull;

import edu.unt.nslab.butshuti.bluetoothvpn.data.objects.ReachabilityTestResult;

public class ReachabilityTestSummaryViewModel extends AndroidViewModel {
    private MutableLiveData<ReachabilityTestResult> sdParams;

    public ReachabilityTestSummaryViewModel(@NonNull Application application) {
        super(application);
    }

    public LiveData<ReachabilityTestResult> getSdParams() {
        if(sdParams == null){
            sdParams = new MutableLiveData<>();
            sdParams.postValue(ReachabilityTestResult.emptySD());
        }
        return sdParams;
    }

    public void update(ReachabilityTestResult reachabilityTestResult){
        if(this.sdParams == null){
            getSdParams();
        }
        this.sdParams.postValue(reachabilityTestResult);
    }
}
