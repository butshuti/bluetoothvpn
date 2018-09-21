package edu.unt.nsllab.butshuti.bluetoothvpn.ui;

import android.app.Activity;
import android.arch.lifecycle.ViewModelProviders;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;

import java.net.InetAddress;
import java.net.UnknownHostException;

import edu.unt.nsllab.butshuti.bluetoothvpn.R;
import edu.unt.nsllab.butshuti.bluetoothvpn.data.objects.ReachabilityTestResult;
import edu.unt.nsllab.butshuti.bluetoothvpn.data.view_models.ReachabilityTestSummaryViewModel;
import edu.unt.nsllab.butshuti.bluetoothvpn.ui.custom_views.CustomSearchView;
import edu.unt.nsllab.butshuti.bluetoothvpn.ui.fragments.PingTestResultsFragment;
import edu.unt.nsllab.butshuti.bluetoothvpn.ui.templates.ToolbarFrameActivity;
import edu.unt.nsllab.butshuti.bluetoothvpn.utils.Logger;

import static edu.unt.nsllab.butshuti.bluetoothvpn.ui.ReachabilityTestActivity.ACTION_START_REACHABILITY_TEST;
import static edu.unt.nsllab.butshuti.bluetoothvpn.ui.ReachabilityTestActivity.INTENT_KEY_TARGET_HOST;

public class PingTestActivity extends ToolbarFrameActivity implements CustomSearchView.SearchViewListener {

    public static final int PING_TEST_REQUEST_CODE = 0x99;
    public static final String ACTION_PING_TEST = "ping";
    public static final String EXTRA_KEY_HOSTNAME = "dst";
    private ReachabilityTestSummaryViewModel reachabilityTestSummaryViewModel;
    private String lastQuery = null;

    @Override
    public void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        reachabilityTestSummaryViewModel = ViewModelProviders.of(this).get(ReachabilityTestSummaryViewModel.class);
        enableSearchInterface(this);
    }

    @Override
    public void onResume(){
        super.onResume();
        Intent intent = getIntent();
        if(intent != null){
            String action = intent.getAction();
            if(action != null && action.equals(ACTION_PING_TEST)){
                String hostAddress = intent.getStringExtra(EXTRA_KEY_HOSTNAME);
                saveQuery(hostAddress);
                intent.setAction("");
                runPingTest(hostAddress);
            }
        }
    }

    @Override
    public Fragment getMainFragment() {
        return new PingTestResultsFragment();
    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent){
        if(requestCode == PING_TEST_REQUEST_CODE) {
            if(resultCode == RESULT_OK){
                String resultStr = intent.getStringExtra(getString(R.string.net_probe_result_data));
                if(resultStr != null){
                    postResults(ReachabilityTestResult.fromString(resultStr));
                }
            }else{
                postResults(ReachabilityTestResult.emptySD().setFailed(ReachabilityTestResult.DiscoveryMode.UNSPECIFIED, -1, -1));
            }
        }
    }

    private void postResults(ReachabilityTestResult results){
        reachabilityTestSummaryViewModel.update(results);
    }

    private void runPingTest(String hostAddress){
        Intent intent = new Intent(this, ReachabilityTestActivity.class);
        InetAddress addr = null;
        try {
            addr = InetAddress.getByName(hostAddress);
        } catch (UnknownHostException e) {
            Logger.logE(e.getMessage());
            return;
        }
        intent.putExtra(INTENT_KEY_TARGET_HOST, addr);
        intent.setAction(ACTION_START_REACHABILITY_TEST);
        startActivityForResult(intent, PING_TEST_REQUEST_CODE);
    }

    private boolean isValidHostAddress(String addr) {
        if (addr != null){
            String toks[] = addr.split("\\.");
            if(toks.length == 4){
                for(String tok : toks){
                    try{
                        Integer.parseInt(tok);
                    }catch (NumberFormatException e){
                        return false;
                    }
                }
                return true;
            }
        }
        return false;
    }
    @Override
    public boolean onQuerySubmitted(String query) {
        if(isValidHostAddress(query)){
            saveQuery(query);
            runPingTest(query);
            return true;
        }
        return false;
    }

    @Override
    public void refreshLastQuery(){
        if(lastQuery != null){
            runPingTest(lastQuery);
        }else{
            displayErrorMsg("Nothing to refresh yet. Please use the toolbar to enter & submit queries.");
        }
    }

    @Override
    protected void saveQuery(String query){
        super.saveQuery(query);
        if(query != null){
            lastQuery = query;
        }
    }

    private void displayErrorMsg(String msg){
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);
        AlertDialog dialog = dialogBuilder.create();
        dialog.setMessage(msg);
        dialog.setIcon(android.R.drawable.ic_dialog_alert);
        dialog.show();
    }
}
