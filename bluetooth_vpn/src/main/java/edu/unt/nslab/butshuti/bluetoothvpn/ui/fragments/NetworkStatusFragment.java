package edu.unt.nslab.butshuti.bluetoothvpn.ui.fragments;

import android.arch.lifecycle.Observer;
import android.arch.lifecycle.ViewModelProviders;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.GridLayout;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.Collection;
import java.util.List;

import edu.unt.nslab.butshuti.bluetoothvpn.R;
import edu.unt.nslab.butshuti.bluetoothvpn.data.objects.Peer;
import edu.unt.nslab.butshuti.bluetoothvpn.data.objects.ServiceStatusWrapper;
import edu.unt.nslab.butshuti.bluetoothvpn.ui.custom_views.NodeStatusCardView;
import edu.unt.nslab.butshuti.bluetoothvpn.ui.custom_views.ServiceStatusCardView;
import edu.unt.nslab.butshuti.bluetoothvpn.data.view_models.PeersListViewModel;
import edu.unt.nslab.butshuti.bluetoothvpn.data.view_models.ServiceStatusViewModel;

public class NetworkStatusFragment extends Fragment implements Observer{

    private ServiceStatusCardView localNodeStatusView;

    public NetworkStatusFragment() {
        // Required empty public constructor
    }


    public static NetworkStatusFragment newInstance() {
        NetworkStatusFragment fragment = new NetworkStatusFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view =  inflater.inflate(R.layout.network_status_fragment, container, false);
        localNodeStatusView = view.findViewById(R.id.local_node_status_view);
        return view;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        PeersListViewModel peersListViewModel = ViewModelProviders.of(getActivity()).get(PeersListViewModel.class);
        ServiceStatusViewModel serviceStatusViewModel = ViewModelProviders.of(getActivity()).get(ServiceStatusViewModel.class);
        peersListViewModel.getPeers().observe(this, this);
        serviceStatusViewModel.getServiceStatus().observe(this, this);
    }

    @Override
    public void onDetach() {
        super.onDetach();
    }

    private void updatePeers(@NonNull List<Peer> peers) {
        GridLayout gridLayout = getView().findViewById(R.id.grid);
        gridLayout.removeAllViews();
        for(Peer peer : peers){
            NodeStatusCardView cardView = (NodeStatusCardView) getLayoutInflater().inflate(R.layout.peer_node_status_view, gridLayout, false);
            cardView.update(peer);
            gridLayout.addView(cardView);
        }
        localNodeStatusView.setPeerCount(peers.size());
    }

    private void updateLocalNodeStatus(@NonNull ServiceStatusWrapper statusDescription){
        List<Peer> peers = statusDescription.getClients();
        localNodeStatusView.update(statusDescription.describe());
        localNodeStatusView.setPeerCount(peers.size());
        localNodeStatusView.setPktCount((int)statusDescription.getPktCount());
    }

    @Override
    public void onChanged(@Nullable Object o) {
        if(o == null){
            return;
        }
        if(o instanceof Collection && !((List)o).isEmpty() && ((List)o).get(0) instanceof Peer){
            updatePeers((List)o);
        }else if(o instanceof ServiceStatusWrapper){
            updateLocalNodeStatus((ServiceStatusWrapper)o);
        }
    }
}
