package edu.unt.nslab.butshuti.bluetoothvpn.ui.custom_views;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v7.widget.CardView;
import android.util.AttributeSet;
import android.widget.TextView;

import edu.unt.nslab.butshuti.bluetoothvpn.R;
import edu.unt.nslab.butshuti.bluetoothvpn.data.objects.Peer;

public class NodeStatusCardView extends CardView implements ClickableView{

    public NodeStatusCardView(Context context) {
        super(context);
    }

    public NodeStatusCardView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public NodeStatusCardView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void update(@NonNull Peer peer){
        ((TextView)findViewById(R.id.node_name_textview)).setText(peer.getHostName());
        ((TextView)findViewById(R.id.node_ip_textview)).setText(peer.getHostAddress());
        if(peer.getStatus().equals(Peer.Status.CONNECTED)){
            findViewById(R.id.node_icon).setBackgroundResource(R.drawable.oval_success_background);
        }else if(peer.getStatus().equals(Peer.Status.CONNECTING)){
            findViewById(R.id.node_icon).setBackgroundResource(R.drawable.oval_background);
        }else {
            findViewById(R.id.node_icon).setBackgroundResource(R.drawable.oval_error_background);
        }
    }

    @Override
    public String getSelectedText(){
        return String.valueOf(((TextView)findViewById(R.id.node_ip_textview)).getText());
    }
}
