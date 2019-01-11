package edu.unt.nslab.butshuti.bluetoothvpn.ui.custom_views;

import android.content.Context;
import android.util.AttributeSet;

import edu.unt.nslab.butshuti.bluetoothvpn.R;
import edu.unt.nslab.butshuti.bluetoothvpn.data.objects.Peer;

public class ServiceStatusCardView extends NodeStatusCardView {

    public ServiceStatusCardView(Context context) {
        super(context);
    }

    public ServiceStatusCardView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ServiceStatusCardView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void update(Peer peer){
        super.update(peer);
        ((TwoColumnsTaggedTextView)findViewById(R.id.service_status_column)).setText(peer.getStatus().name());
    }

    public void setPeerCount(int numPeers){
        ((TwoColumnsTaggedTextView)findViewById(R.id.peer_count_column)).setText(String.valueOf(numPeers));
    }

    public void setPktCount(int numPkts){
        ((TwoColumnsTaggedTextView)findViewById(R.id.pkt_count_column)).setText(String.valueOf(numPkts));
    }
}
