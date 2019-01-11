package edu.unt.nslab.butshuti.bluetoothvpn.ui.fragments;

import android.arch.lifecycle.Observer;
import android.arch.lifecycle.ViewModelProviders;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import edu.unt.nslab.butshuti.bluetoothvpn.R;
import edu.unt.nslab.butshuti.bluetoothvpn.data.objects.ReachabilityTestResult;
import edu.unt.nslab.butshuti.bluetoothvpn.utils.measurement.CDF;
import edu.unt.nslab.butshuti.bluetoothvpn.utils.measurement.CDFDiagram;
import edu.unt.nslab.butshuti.bluetoothvpn.data.view_models.ReachabilityTestSummaryViewModel;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;


/**
 * A user interface for initiating service discovery, and testing an
 * existing connection
 */
public class PingTestResultsFragment extends Fragment implements Observer<ReachabilityTestResult> {

    private View view;
    private ImageView resultsIconView;
    private ReachabilityTestSummaryViewModel reachabilityTestSummaryViewModel;

    public PingTestResultsFragment() {
    }

    public static PingTestResultsFragment newInstance(){
        return new PingTestResultsFragment();
    }

    @Override
    public void onDetach() {
        super.onDetach();
    }

    @Override
    public void onAttach(Context context){
        super.onAttach(context);
        reachabilityTestSummaryViewModel = ViewModelProviders.of(getActivity()).get(ReachabilityTestSummaryViewModel.class);
        reachabilityTestSummaryViewModel.getSdParams().observe(this, this);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        this.view = inflater.inflate(R.layout.ping_test_results_fragment, container, false);
        resultsIconView = this.view.findViewById(R.id.resultsIcon);
        return this.view;
    }

    /**
     * Update results of a requested action.
     * @param results service discovery results
     */
    private void updateSDResultsDetails(ReachabilityTestResult results){
        if(results == null){
            return;
        }
        CDFDiagram cdfDiagram = new CDFDiagram(resultsIconView);
        CDF resultsCDF = results.getCDF(100);
        cdfDiagram.draw(resultsCDF.getCumDist(), "RTT", "ms");
        String serviceAddress;
        if(results.getServiceAddr() != null){
            serviceAddress = results.getServiceAddr().getHostAddress();
        }else{
            serviceAddress = "N/A";
        }
        double probeSuccess = Math.round(100.0*results.getNumProbes() / results.getNumRequestedProbes());
        String meanRTTStr = results.getAvgRTT() > 0.0 ? String.valueOf(results.getAvgRTT()) + " ms" : "---";
        String RTTRangeStr = String.format("[%.1f , %.1f]", resultsCDF.getMin(), resultsCDF.getMax());
        //The following are 'rows' to dynamically embed in the results view.
        LinearLayout svcAddrLine = getDetailLine("Network Address", serviceAddress);
        LinearLayout resultLine = getDetailLine("Discovery result", results.getStatus().getExpl());
        LinearLayout discoveryModeLine = getDetailLine("Discovery Mode", results.getDiscoveryMode());
        LinearLayout successRatioLine = getDetailLine("Successful probes", probeSuccess + " %");
        LinearLayout hopsLine = getDetailLine("Number of hops", String.valueOf(results.getNumHops()) + "*");
        LinearLayout pktSizeLine = getDetailLine("Packet size", String.valueOf(results.getPacketSize()));
        LinearLayout timeoutLine = getDetailLine("Configured Timeout", String.valueOf(results.getConfiguredTimeout()) + " ms");
        LinearLayout meanRTTLine = getDetailLine("Average RTT", meanRTTStr);
        LinearLayout modeRTTLine = getDetailLine("RTT Range", RTTRangeStr);
        LinearLayout durationLine = getDetailLine("Duration", String.valueOf(results.getDurationSeconds()) + " sec");
        LinearLayout numAttemptsLine = getDetailLine("Number of attempts", String.valueOf(results.getNumAttempts()));
        LinearLayout resultsView = (LinearLayout)this.view.findViewById(R.id.sd_results_details);
        if(probeSuccess < 25){
            successRatioLine.setBackgroundColor(Color.RED);
        }else if(probeSuccess < 50){
            successRatioLine.setBackgroundColor(Color.YELLOW);
        }
        if(results.getStatus().equals(ReachabilityTestResult.Status.FAILED)){
            resultLine.setBackgroundColor(Color.RED);
        }else if(results.getStatus().equals(ReachabilityTestResult.Status.CANCELLED)){
            resultLine.setBackgroundColor(Color.GRAY);
        }else{
            resultLine.setBackgroundColor(Color.WHITE);
        }
        //Refresh the result view (remove all pre-existing child views)
        resultsView.removeAllViews();
        //The weight of the results view must be set to the number of embedded children views to force them to occupy appropriate spaces.
        resultsView.setWeightSum(12);
        //Dynamically add the children views ('rows')
        svcAddrLine.setBackgroundResource(R.drawable.app_gradient_background);
        ((TextView)svcAddrLine.findViewById(R.id.row_line_content)).setTextColor(Color.parseColor("#FFFFFFFF"));
        resultsView.addView(svcAddrLine);
        resultsView.addView(resultLine);
        resultsView.addView(discoveryModeLine);
        resultsView.addView(successRatioLine);
        resultsView.addView(hopsLine);
        resultsView.addView(pktSizeLine);
        resultsView.addView(timeoutLine);
        resultsView.addView(meanRTTLine);
        resultsView.addView(modeRTTLine);
        resultsView.addView(durationLine);
        resultsView.addView(numAttemptsLine);
    }

    /**
     * Factory method for creating a child view to be used as a 'row' in the results view
     * @param label The row's leftmost column content (result title/label)
     * @param content The row's rightmost column content (result content)
     * @return a linear layout populated and formatted to be included in the results view
     */
    private LinearLayout getDetailLine(String label, String content){
        Context context = getActivity();
        LinearLayout ll;
        TextView labelTv, contentTv;
        ll = (LinearLayout) LayoutInflater.from(context).inflate(R.layout.two_columns_net_probe_results_row, null);
        if(ll != null){
            labelTv = ll.findViewById(R.id.row_line_label);
            contentTv = ll.findViewById(R.id.row_line_content);
        }else{
            ll = new LinearLayout(context);
            ll.setOrientation(LinearLayout.HORIZONTAL);
            labelTv = new TextView(context);
            ll.addView(labelTv);
            contentTv = new TextView(context);
            ll.addView(contentTv);
        }
        labelTv.setText(label);
        contentTv.setText(content);
        return ll;
    }

    @Override
    public void onChanged(@Nullable ReachabilityTestResult reachabilityTestResult) {
        updateSDResultsDetails(reachabilityTestResult);
    }
}
