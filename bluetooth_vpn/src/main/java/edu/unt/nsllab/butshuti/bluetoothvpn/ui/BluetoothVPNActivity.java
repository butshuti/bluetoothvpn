package edu.unt.nsllab.butshuti.bluetoothvpn.ui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.NavigationView;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.GravityCompat;
import android.support.v4.view.ViewPager;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.view.View;

import edu.unt.nsllab.butshuti.bluetoothvpn.R;
import edu.unt.nsllab.butshuti.bluetoothvpn.data.view_models.Repository;
import edu.unt.nsllab.butshuti.bluetoothvpn.ui.custom_views.ClickableView;
import edu.unt.nsllab.butshuti.bluetoothvpn.ui.fragments.ConnectivityGraphFragment;
import edu.unt.nsllab.butshuti.bluetoothvpn.ui.fragments.NetworkStatusFragment;
import edu.unt.nsllab.butshuti.bluetoothvpn.utils.Logger;
import edu.unt.nsllab.butshuti.bluetoothvpn.vpn.BTVPNApplication;
import edu.unt.nsllab.butshuti.bluetoothvpn.vpn.BTVpnService;

import static edu.unt.nsllab.butshuti.bluetoothvpn.ui.PingTestActivity.ACTION_PING_TEST;
import static edu.unt.nsllab.butshuti.bluetoothvpn.ui.PingTestActivity.EXTRA_KEY_HOSTNAME;
import static edu.unt.nsllab.butshuti.bluetoothvpn.vpn.BTVPNApplication.BT_VPN_SERVICE_STARTED;

/**
 * Created by butshuti on 5/17/18.
 */

public class BluetoothVPNActivity extends AppCompatActivity{

    public static final int CONFIGURE_PEERS_REQUEST_CODE = 0x77;
    public static final int CONFIGURE_NETWORK = 0x55;
    public static final int VPN_START_REQUEST_CODE = 0x88;

    private BTVpnService vpnService = null;
    private FloatingActionButton configFab, activateVpnFab;
    private ViewPager viewPager;
    private TabLayout tabs;
    private TabPageAdapter pagerAdapter;
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private BroadcastReceiver broadcastReceiver;

    @Override
    public void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        setContentView(R.layout.network_diagnostics_screen);
        broadcastReceiver = createServiceStatusListener();
        viewPager = findViewById(R.id.pager);
        tabs = findViewById(R.id.tabs);
        pagerAdapter = new TabPageAdapter(getSupportFragmentManager(), viewPager);
        navigationView = findViewById(R.id.diagnostics_screen_navigation_view);
        drawerLayout = findViewById(R.id.drawer_layout);
        viewPager.setAdapter(pagerAdapter);
        tabs.setupWithViewPager(viewPager);
        configFab = findViewById(R.id.config_fab);
        activateVpnFab = findViewById(R.id.activate_vpn_fab);
        prepareToolbar();
        registerViewClickHandlers();
    }

    @Override
    public void onResume(){
        super.onResume();
    }

    @Override
    protected void onStart() {
        super.onStart();
        registerReceiver(broadcastReceiver, new IntentFilter(BT_VPN_SERVICE_STARTED));
        startVPNService();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if(vpnService != null){
            Repository.instance().removeDataSource(((BTVPNApplication)getApplication()).getServiceStatusWrapper());
        }
        unregisterReceiver(broadcastReceiver);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent){
        if(requestCode == VPN_START_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                startService(new Intent(this, BTVpnService.class));
                activateVpnFab.setVisibility(View.INVISIBLE);
            }else{
                stopService(new Intent(this, BTVpnService.class));
                activateVpnFab.setVisibility(View.VISIBLE);
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if(item.getItemId() == android.R.id.home){
            drawerLayout.openDrawer(GravityCompat.START);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private boolean startVPNService(){
        Intent prepareIntent = BTVpnService.prepare(this);
        if(prepareIntent != null){
            startActivityForResult(prepareIntent, VPN_START_REQUEST_CODE);
        }else{
            startService(new Intent(this, BTVpnService.class));
        }
        vpnService = BTVpnService.getInstance();
        return vpnService != null;
    }

    private void prepareToolbar(){
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionbar = getSupportActionBar();
        actionbar.setDisplayHomeAsUpEnabled(true);
        actionbar.setHomeAsUpIndicator(R.drawable.ic_menu);
    }

    public void onNodeViewClick(View view){
        if(view instanceof ClickableView) {
            String hostAddress = ((ClickableView) view).getSelectedText();
            if(hostAddress != null){
                runReachabilityTest(hostAddress);
            }
        }
    }

    private void registerViewClickHandlers(){
        navigationView.setNavigationItemSelectedListener(item -> {
            switch (item.getItemId()){
                case R.id.nav_menu_item_ping_test:
                    runReachabilityTest(null);
                    break;
                case R.id.nav_menu_item_traceroute:
                    runTraceroute();
                    break;
                case R.id.nav_menu_item_routing_peers:
                    configurePeers();
                    break;
                case R.id.nav_menu_item_configure_network:
                    launchNetworkConfiguration();
                    break;
            }
            drawerLayout.closeDrawers();
            return true;
        });
        configFab.setOnClickListener(v -> launchNetworkConfiguration());
        activateVpnFab.setOnClickListener(v -> startVPNService());
        activateVpnFab.setVisibility(View.INVISIBLE);
    }

    private void configurePeers(){
        Intent intent = new Intent();
        intent.setAction(getString(R.string.action_bt_service_locator));
        intent.setType("text/json");
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivityForResult(intent, CONFIGURE_PEERS_REQUEST_CODE);
    }

    private void launchNetworkConfiguration(){
        Intent intent = new Intent();
        intent.setAction(getString(R.string.action_bt_service_settings));
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivityForResult(intent, CONFIGURE_NETWORK);
    }


    private void runReachabilityTest(String hostAddress) {
        Intent request = new Intent(this, PingTestActivity.class);
        if(hostAddress != null){
            request.setAction(ACTION_PING_TEST);
            request.putExtra(EXTRA_KEY_HOSTNAME, hostAddress);
        }
        startActivity(request);
    }

    private void runTraceroute(){

    }

    @Override
    public void onPointerCaptureChanged(boolean hasCapture) {

    }

    public BroadcastReceiver createServiceStatusListener() {
        return new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if(BT_VPN_SERVICE_STARTED.equals(action)){
                    vpnService = BTVpnService.getInstance();
                    if(vpnService != null){
                        Repository.instance().addDataSource(((BTVPNApplication)getApplication()).getServiceStatusWrapper());
                    }
                }
            }
        };
    }

    private final class TabPageAdapter extends FragmentStatePagerAdapter {
        private final class Tab{
            Fragment fragment;
            int title;
            int icon;
            Tab(Fragment fragment, int title, int icon){
                this.fragment = fragment;
                this.title = title;
                this.icon = icon;
            }
        }
        private Tab tabs[] = new Tab[]{
                new Tab(NetworkStatusFragment.newInstance(), R.string.tab_name_service_status, R.drawable.network),
                new Tab(ConnectivityGraphFragment.newInstance(), R.string.tab_name_connectivity_graph, R.drawable.graph)
        };

        public TabPageAdapter(FragmentManager supportFragmentManager, ViewPager viewPager) {
            super(supportFragmentManager);
            viewPager.setAdapter(this);
        }

        @Override
        public Fragment getItem(int position) {
            return tabs[position%tabs.length].fragment;
        }

        @Override
        public int getCount() {
            return tabs.length;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return getString(tabs[position].title);
        }

        public int getPageIcon(int position) {
            return tabs[position].icon;
        }
    }
}