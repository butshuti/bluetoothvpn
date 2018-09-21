package edu.unt.nsllab.butshuti.bluetoothvpn.ui;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.net.InetAddress;

import edu.unt.nsllab.butshuti.bluetoothvpn.R;
import edu.unt.nsllab.butshuti.bluetoothvpn.data.objects.ReachabilityTestResult;
import edu.unt.nsllab.butshuti.bluetoothvpn.iptools.sdquery.SDGracefulScheduler;
import edu.unt.nsllab.butshuti.bluetoothvpn.iptools.sdquery.SDQuery;
import edu.unt.nsllab.butshuti.bluetoothvpn.iptools.sdquery.SDQueryExecutor;
import edu.unt.nsllab.butshuti.bluetoothvpn.utils.Logger;

/**
 *
 * @author butshuti
 */
public class ReachabilityTestActivity extends Activity implements SDQuery.SDProgressListener{
    public final static String INTENT_KEY_TARGET_HOST = "target_host";
    public static final String ACTION_START_REACHABILITY_TEST = "START_REACHABILITY_TEST";
    private final static int DEFAULT_MAX_PROBES = 20;
    private final static int PROGRESSBAR_SCALE = 100;
    private int numAttempts = 1;
    private SDGracefulScheduler sdScheduler;
    private boolean isDismissed = false;
    private ProgressBar progressBar, spinningWheel;
    private TextView progressTextView, networkStateTextView;
    private View progressView, networkStatusView;
    private long lastRequestTs = -1;
    private InetAddress targetHost;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.reachability_test_screen);
        progressBar = findViewById(R.id.progressBar);
        spinningWheel = findViewById(R.id.indeterminateProgressBar);
        progressTextView = findViewById(R.id.progress_text);
        progressView = findViewById(R.id.progresbar_container);
        networkStatusView = findViewById(R.id.network_status_container);
        networkStateTextView = findViewById(R.id.network_status_text);
        progressBar.setIndeterminate(false);
        spinningWheel.setIndeterminate(true);
        progressBar.setMax(PROGRESSBAR_SCALE);
        progressTextView.setText("Preparing service discovery.");
        sdScheduler = new SDGracefulScheduler(-1, -1, SDGracefulScheduler.ConnState.CONN_WAIT);
        if(savedInstanceState == null){
            lastRequestTs = getCurTsSeconds();
        }
        SDQueryExecutor.nextExecutor(this);
    }

    @Override
    public void onNewIntent(Intent intent){
        super.onNewIntent(intent);
        setIntent(intent);
    }

    @Override
    public void onResume(){
        super.onResume();
        isDismissed = false;
        if(getIntent() != null){
            Intent intent = getIntent();
            String action = intent.getAction();
            if(ACTION_START_REACHABILITY_TEST.equals(action)) {
                targetHost = (InetAddress) intent.getExtras().get(INTENT_KEY_TARGET_HOST);
                if (targetHost == null) {
                    toggleProgressDisplay(false);
                    Logger.logE("Target host address not set.");
                    networkStateTextView.setText("Target host address not set.");
                    finishAndReportCancelled();
                } else {
                    toggleProgressDisplay(true);
                    launchSD();
                }
            }else{
                toggleProgressDisplay(false);
                networkStateTextView.setText("Unkown request type: " + action);
            }
        }
        progressTextView.setText(describeConnectivityState());
    }

    /**
     * Override default behavior to set result code before destroying activity when back button is pressed
     */
    @Override
    public void onBackPressed() {
        finishAndReportCancelled();
    }

    /**
     * Override default behavior to set result code before destroying activity
     */
    @Override
    public void onDestroy(){
        if(!(isFinishing() || isDestroyed())){
            finishAndReportCancelled();
        }
        super.onDestroy();
    }

    @Override
    public void onPause(){
        super.onPause();
        if(!(isFinishing() || isDestroyed())){
            finishAndReportCancelled();
        }
        isDismissed = true;
    }

    private String describeConnectivityState() {
        if (sdScheduler != null) {
            return sdScheduler.getLastConnState().expl();
        }
        return "UNKNOWN STATE";
    }

    private void finishAndReportCancelled(){
        if(!(isFinishing() || isDestroyed())){
            Intent resultIntent = new Intent();
            ReachabilityTestResult result = ReachabilityTestResult.emptySD();
            result.setCancelled(ReachabilityTestResult.DiscoveryMode.UNSPECIFIED);
            result.recordElapsedTime(numAttempts, (int)(getCurRequestDuration()));
            resultIntent.putExtra(getString(R.string.net_probe_result_data), result.toJSONString());
            setResult(RESULT_CANCELED, resultIntent);
            finish();
        }
    }

    /**
     * Get the duration of the completing action.
     * @return the duration in seconds
     */
    private long getCurRequestDuration(){
        return  getCurTsSeconds() - lastRequestTs;
    }

    /**
     * Get the current timestamp in seconds
     * @return a timestamp
     */
    private long getCurTsSeconds(){
        return SystemClock.elapsedRealtime() / 1000;
    }

    /**
     * Launch a service discovery task
     */
    protected void launchSD(){
        toggleProgressDisplay(true);
        sendProbes(targetHost, DEFAULT_MAX_PROBES);
    }

    /**
     * Switch views between the progress view (spinning wheels) and connection service status view
     * @param displayProgress
     */
    private void toggleProgressDisplay(boolean displayProgress){
        if(displayProgress){
            spinningWheel.setVisibility(View.VISIBLE);
            progressView.setVisibility(View.VISIBLE);
            networkStatusView.setVisibility(View.GONE);
        }else{
            progressView.setVisibility(View.GONE);
            networkStatusView.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Post SD results.
     * This method is called through a propagation of callbacks originating from an asyncTask performing service discovery (SD).
     * @param result the result of the operation
     * @param probeIdx the requested number of probes in the last batch of probes
     */
    @Override
    public void postResult(ReachabilityTestResult result, int probeIdx){
        if(!isDismissed && (result == null || result.isEmpty())){
            progressTextView.setText("Service Discovery failed");
            progressTextView.setTextColor(Color.RED);
        }
        Intent resultIntent = new Intent();
        if(result == null){
            //Null result
            result = ReachabilityTestResult.emptySD();
        }
        if(!isDismissed){
            if(result.isEmpty()){
                //Service discovery often fails when attempted before the network is ready.
                //Here we use a graceful SD scheduler to attempt retries taking into account our network status and changes in the latter.
                int numRemainingProbes = sdScheduler.getNumProbes();
                numAttempts++;
                if(numRemainingProbes > 0){
                    //Advised a new retry, attempt it!
                    progressTextView.setText("Attempt #" + numAttempts + "/{" + describeConnectivityState() + "}");
                    sendProbes(targetHost, numRemainingProbes);
                    return;
                }else{
                    //The scheduler gave up, so should we!
                    result.setFailed(ReachabilityTestResult.DiscoveryMode.UNSPECIFIED, -1, -1);
                }
            }
            //Gather results and deliver them to the caller...
            result.recordElapsedTime(numAttempts, (int)(getCurRequestDuration()));
            resultIntent.putExtra(getString(R.string.net_probe_result_data), result.toJSONString());
            setResult(RESULT_OK, resultIntent);
            finish();
        }
    }

    /**
     * Update the UI with the current SD progress
     * @param progress
     */
    @Override
    public void updateProgress(Integer...progress){
        float val = PROGRESSBAR_SCALE * (float)progress[0]/progress[1];
        spinningWheel.setVisibility(View.VISIBLE);
        progressBar.setProgress((int)val);
        progressTextView.setText(val + "%");
        Logger.logI("posting Results: " + progressTextView.getText());
    }

    /**
     * Send probes to a remote peer for service discovery.
     * @param hostAddress the peer's address
     * @param maxProbes the maximum number of probes per query.
     */
    private void sendProbes(final InetAddress hostAddress, final int maxProbes){
        SDQuery sdQuery = new SDQuery(getMainLooper(), this, hostAddress, numAttempts, maxProbes);
        sdQuery.setSoTimeoutMs(SDQueryExecutor.DEFAULT_SO_TIMEOUT_MS);
        if(sdScheduler.getConfiguredSoTimeoutMs() < 0){
            //Use the task's defaults for the first time
            sdScheduler.setConfiguredSoTimeoutMs(sdQuery.getSoTimeoutMs());
            sdScheduler.setConfiguredMaxProbes(maxProbes);
        }else{
            //Otherwise let the scheduler adaptively choose convenient parameters to start with
            sdQuery.setSoTimeoutMs(sdScheduler.getCurSoTimeoutMs());
            sdQuery.setMaxProbes(sdScheduler.getNumProbes());
        }
        SDQueryExecutor.schedule(sdQuery);
    }
}
