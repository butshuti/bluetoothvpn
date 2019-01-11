package edu.unt.nslab.butshuti.bluetoothvpn.ui;

import android.Manifest;
import android.app.Activity;
import android.app.Application;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.ParcelUuid;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import edu.unt.nslab.butshuti.bluetoothvpn.R;
import edu.unt.nslab.butshuti.bluetoothvpn.data.objects.Peer;
import edu.unt.nslab.butshuti.bluetoothvpn.data.view_models.PeersListViewModel;
import edu.unt.nslab.butshuti.bluetoothvpn.discovery.ApplicationService;
import edu.unt.nslab.butshuti.bluetoothvpn.discovery.SDConfig;
import edu.unt.nslab.butshuti.bluetoothvpn.utils.Logger;

import static android.bluetooth.BluetoothAdapter.STATE_ON;

/**
 * Bluetooth Service Locator.
 *
 * <p>
 *     The service locator searches for BT peers advertising a target application UUID through SDP.
 * </p>
 */
public class ServiceLocatorActivity extends Activity implements AdapterView.OnItemClickListener{

    public static final String BT_SERVICE_SEARCH_SELECTED_PEER = "service_location";
    public static final int BT_SERVICE_PERMISSION_REQUEST_ID = 0;
    public static final int BT_SERVICE_PAIR_REQUEST_ID = 0x037;
    public static final String BT_SERVICE_CURRENT_SD_CONFIG = "bt_service_locator_current_config";
    private static final String PERMISSIONS[] = new String[]{Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION};

    private BluetoothAdapter bluetoothAdapter;
    private  BTDeviceArrayAdapter newPeersListAdapter, pairedPeersListAdapter;
    private List<BluetoothDevice> pairedDevicesList;
    private BroadcastReceiver broadcastReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.discovered_peer_selection_list);
        setResult(Activity.RESULT_CANCELED);
        Button scanButton = (Button) findViewById(R.id.button_scan);
        scanButton.setOnClickListener(v -> {
            discoverPeers();
            v.setVisibility(View.GONE);
        });
        broadcastReceiver = createBTBroadcastReceiver();
        pairedPeersListAdapter = new BTDeviceArrayAdapter(this, getApplication());
        newPeersListAdapter = new BTDeviceArrayAdapter(this, getApplication());
        ListView pairedListView = (ListView) findViewById(R.id.paired_devices);
        pairedDevicesList = new ArrayList<>();
        pairedListView.setAdapter(pairedPeersListAdapter);
        pairedListView.setOnItemClickListener(this);
        ListView newDevicesListView = (ListView) findViewById(R.id.new_devices);
        newDevicesListView.setAdapter(newPeersListAdapter);
        newDevicesListView.setOnItemClickListener(this);
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.cancelDiscovery();
        }
        // Get a set of currently paired devices
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();

        // If there are paired devices, add each one to the ArrayAdapter
        if (pairedDevices.size() > 0) {
            findViewById(R.id.title_paired_devices).setVisibility(View.VISIBLE);
            for (BluetoothDevice device : pairedDevices) {
                device.fetchUuidsWithSdp();
                pairedPeersListAdapter.add(device);
                pairedDevicesList.add(device);
            }
        } else {
            pairedPeersListAdapter.clear();
        }
    }

    @Override
    public void onStart(){
        super.onStart();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        intentFilter.addAction(BluetoothDevice.ACTION_FOUND);
        intentFilter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        intentFilter.addAction(BluetoothDevice.ACTION_UUID);
        intentFilter.addAction(BluetoothDevice.ACTION_PAIRING_REQUEST);
        intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        registerReceiver(broadcastReceiver, intentFilter);
        checkPermissions();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bluetoothAdapter != null) {
            bluetoothAdapter.cancelDiscovery();
        }
        this.unregisterReceiver(broadcastReceiver);
    }

    private void checkPermissions(){
        for(String permission : PERMISSIONS){
            if(ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED){
                ActivityCompat.requestPermissions(this, new String[]{permission}, BT_SERVICE_PERMISSION_REQUEST_ID);
            }
        }
    }

    /**
     * Scan for new peers.
     */
    private void discoverPeers() {
        if(bluetoothAdapter.isDiscovering()){
            bluetoothAdapter.cancelDiscovery();
        }
        setProgressBarIndeterminateVisibility(true);
        if(bluetoothAdapter.getState() == STATE_ON){
            setTitle("Scanning for devices...");
            findViewById(R.id.title_new_devices).setVisibility(View.VISIBLE);
            bluetoothAdapter.startDiscovery();
        }else{
            setTitle("Activating Bluetooth...");
            bluetoothAdapter.enable();
        }
    }

    /**
     * Handle clicks on individual items.
     * {@see {@link AdapterView.OnItemClickListener}}
     * @param parent
     * @param view
     * @param position
     * @param id
     */
    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        if(bluetoothAdapter != null){
            bluetoothAdapter.cancelDiscovery();
            //Get the text view containing the BT address
            TextView addressTextView = BTDeviceArrayAdapter.gteAddressTextView(view);
            if(addressTextView != null){
                BluetoothDevice device;
                try{
                    device = bluetoothAdapter.getRemoteDevice(addressTextView.getText().toString());
                    if(device.getBondState() == BluetoothDevice.BOND_NONE){
                        //If selected device is not paired, request pairing.
                        if(createBondWith(device)){
                            setProgressBarIndeterminateVisibility(true);
                            setTitle("Pairing with " + device);
                            Toast.makeText(this, "Pairing with " + device.toString(), Toast.LENGTH_LONG);
                        }else{
                            Toast.makeText(this, "Failed to pair with " + device.toString(), Toast.LENGTH_LONG);
                        }
                        return;
                    }
                } catch (IllegalArgumentException e){
                    Logger.logE(e.getMessage());
                    Toast.makeText(this, "Invalid Bluetooth address: " + addressTextView.getText(), Toast.LENGTH_LONG).show();
                    return;
                }
                if(!serviceAdvertised(device)){
                    //Fetch available services to see if the device implements the target service
                    device.fetchUuidsWithSdp();
                    promptForceSelection(device);
                }else {
                    //Finish and return results if this activity was called from another one.
                    returnResults(device);
                }
            }
        }
    }

    /**
     * Request pairing.
     * <p>
     *     Start the Bluetooth settings to pair with a given device.
     * </p>
     * @param device The device to pair with
     * @return true, always.
     */
    private boolean createBondWith(BluetoothDevice device) {
        Intent intent = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, device);
        startActivityForResult(intent, BT_SERVICE_PAIR_REQUEST_ID);
        return true;
    }

    /**
     * Process results and return to caller.
     * @param device The selected device.
     */
    private void returnResults(BluetoothDevice device){
        JSONObject config = SDConfig.bundleDescr(device.getName() != null ? device.getName() : device.getAddress(), device.getAddress());
        SDConfig sdConfig = new SDConfig(this);
        sdConfig.applyConfig(config);
        Intent broadcastIntent = new Intent();
        broadcastIntent.setAction(getString(R.string.action_bt_configured_peer_changed));
        broadcastIntent.putExtra(BluetoothDevice.EXTRA_DEVICE, device);
        broadcastIntent.putExtra(BT_SERVICE_CURRENT_SD_CONFIG, config.toString());
        sendBroadcast(broadcastIntent);
        Intent resultIntent = new Intent();
        resultIntent.putExtra(BT_SERVICE_SEARCH_SELECTED_PEER, device);
        setResult(Activity.RESULT_OK, resultIntent);
        finish();
    }

    /**
     * Parse advertised UUIDs and check for the target UUID.
     * @param device A BT device.
     * @return true if the target UUID was found.
     */
    private static boolean serviceAdvertised(BluetoothDevice device){
        UUID serviceUUID = ApplicationService.getServiceUUID(0), reversedServiceUUID = ApplicationService.getReversedServiceUUID(0);
        if(device.getUuids() == null){
            return false;
        }
        for(ParcelUuid uuid : device.getUuids()){
            if(uuid.getUuid().equals(serviceUUID) || uuid.getUuid().equals(reversedServiceUUID)){
                return true;
            }
        }
        return false;
    }

    /**
     * Check if device is currently configured as a peer.
     * @param device A BT device.
     * @return true if the target UUID was found.
     */
    private static boolean isConfiguredPeer(BluetoothDevice device, Context ctx){
        if(device == null || ctx == null){
            return false;
        }
        SDConfig sdConfig = new SDConfig(ctx);
        return sdConfig.verifyConfiguredNetDevID(device.getAddress(), device.getName());
    }

    /**
     * Check if device is currently connected.
     * @param device A BT device.
     * @return true if the target UUID was found.
     */
    private static boolean isConnected(BluetoothDevice device, Application application){
        if(device == null || application == null){
            return false;
        }
        List<Peer> peers = new PeersListViewModel(application).getPeers().getValue();
        if(peers != null){
            for(Peer peer : peers){
                if(peer.isConnected() && peer.getHostName().equals(device.getName())){
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Display confirmation prompt if the selected device has not yet advertised the target UUID the application is looking for.
     * <p>
     *     Since UUIDs are fetched asynchronously, delays between their updates are not predictable.
     *     If the user is sure that the target service is running on the selected device, it makes sense to let them select it even if the SDP database has not yet been updated with its UUID.
     * </p>
     * @param device The selected device.
     */
    public void promptForceSelection(final BluetoothDevice device){
        ActionInfoDialog dialog = ActionInfoDialog.fromContext(this);
        dialog.setMessage("Configuring " + device.getName() + " as selected peer", device + " (SDP) has not yet advertised support for this application service. Continue anyway?");
        dialog.configureActions(
                (dialogInterface, i) -> returnResults(device),
                (dialogInterface, i) -> {
                    return;
                });
        dialog.show();
    }

    /**
     * Method called when a device is found (while scanning).
     * @param device The discovered device
     */
    private void recordNewDevice(BluetoothDevice device){
        if(device == null || device.getBondState() != BluetoothDevice.BOND_BONDED){
            return;
        }
        findViewById(R.id.title_paired_devices).setVisibility(View.VISIBLE);
        device.fetchUuidsWithSdp();
        newPeersListAdapter.remove(device);
        if(!pairedDevicesList.contains(device)){
            pairedDevicesList.add(device);
            pairedPeersListAdapter.add(device);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults){

    }

    /**
     * Custom array adapter to display non-String Objects.
     */
    private static class BTDeviceArrayAdapter extends ArrayAdapter<BluetoothDevice> {
        private LayoutInflater layoutInflater;
        private Application application;

        public BTDeviceArrayAdapter(@NonNull Context context, Application application) {
            super(context, R.layout.discovered_peer_selection_list);
            layoutInflater = LayoutInflater.from(context);
            this.application = application;
        }
        @Override
        public View getView(int position, View convertView, ViewGroup parent){
            if(convertView == null){
                convertView = layoutInflater.inflate(R.layout.discovered_peer_details, parent, false);
            }
            BluetoothDevice device = getItem(position);
            ((TextView)convertView.findViewById(R.id.peerName)).setText(device.getName());
            ((TextView)convertView.findViewById(R.id.peerAddress)).setText(device.getAddress());
            convertView.findViewById(R.id.peerSelectionIcon).setVisibility(View.INVISIBLE);
            if(device.getBondState() == BluetoothDevice.BOND_BONDED){
                convertView.setBackgroundColor(Color.WHITE);
                if(ServiceLocatorActivity.isConnected(device, application)){
                    ((ImageView)convertView.findViewById(R.id.peerStatusIcon)).setImageResource(android.R.drawable.presence_online);
                }else if(ServiceLocatorActivity.serviceAdvertised(device)){
                    ((ImageView)convertView.findViewById(R.id.peerStatusIcon)).setImageResource(android.R.drawable.presence_away);
                }else{
                    ((ImageView)convertView.findViewById(R.id.peerStatusIcon)).setImageResource(android.R.drawable.presence_offline);
                }
                if(ServiceLocatorActivity.isConfiguredPeer(device, getContext())){
                    convertView.findViewById(R.id.peerSelectionIcon).setVisibility(View.VISIBLE);
                }
            }else{
                convertView.setBackgroundColor(Color.CYAN);
            }
            return convertView;
        }

        public static TextView gteAddressTextView(View view) {
            return (TextView)view.findViewById(R.id.peerAddress);
        }
    }

    /**
     * Broadcast receiver for Bluetooth-related events the activity needs to track.
     */
    private BroadcastReceiver createBTBroadcastReceiver() {
        return new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                    if (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1) == STATE_ON) {
                        discoverPeers();
                    }
                } else if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
                        newPeersListAdapter.add(device);
                    } else {
                        recordNewDevice(device);
                    }
                } else if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {
                    if (intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1) == BluetoothDevice.BOND_BONDED) {
                        BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                        recordNewDevice(device);
                    }
                } else if (BluetoothDevice.ACTION_UUID.equals(action)) {
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    recordNewDevice(device);
                } else if (BluetoothDevice.ACTION_PAIRING_REQUEST.equals(action)) {
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    if (device != null) {
                        if (ActivityCompat.checkSelfPermission(ServiceLocatorActivity.this, Manifest.permission.BLUETOOTH_PRIVILEGED) == PackageManager.PERMISSION_GRANTED) {
                            //device.setPairingConfirmation(true);
                        }
                    }
                } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                    setProgressBarIndeterminateVisibility(false);
                    setTitle("Select Peer to Connect to");
                    if (newPeersListAdapter.getCount() == 0) {
                        newPeersListAdapter.clear();
                    }
                }
            }
        };
    }

}