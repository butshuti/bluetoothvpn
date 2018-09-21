package edu.unt.nsllab.butshuti.bluetoothvpn.vpn;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.arch.lifecycle.MutableLiveData;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import edu.unt.nsllab.butshuti.bluetoothvpn.R;
import edu.unt.nsllab.butshuti.bluetoothvpn.data.objects.Peer;
import edu.unt.nsllab.butshuti.bluetoothvpn.data.objects.ServiceStatusWrapper;
import edu.unt.nsllab.butshuti.bluetoothvpn.datagram.Packet;
import edu.unt.nsllab.butshuti.bluetoothvpn.discovery.SDConfig;
import edu.unt.nsllab.butshuti.bluetoothvpn.sockets.ServerBluetoothSockerAdaptor;
import edu.unt.nsllab.butshuti.bluetoothvpn.tunnel.ControllerThread;
import edu.unt.nsllab.butshuti.bluetoothvpn.tunnel.InterfaceController;
import edu.unt.nsllab.butshuti.bluetoothvpn.tunnel.RemoteInterfaceAdaptor;
import edu.unt.nsllab.butshuti.bluetoothvpn.sockets.BluetoothSocketWrappers;
import edu.unt.nsllab.butshuti.bluetoothvpn.sockets.ClientBluetoothSocketAdaptor;
import edu.unt.nsllab.butshuti.bluetoothvpn.ui.BluetoothVPNActivity;
import edu.unt.nsllab.butshuti.bluetoothvpn.utils.Logger;
import edu.unt.nsllab.butshuti.bluetoothvpn.utils.net_protocols.NetUtils;

import static android.bluetooth.BluetoothAdapter.STATE_DISCONNECTED;
import static android.bluetooth.BluetoothAdapter.STATE_OFF;
import static android.bluetooth.BluetoothAdapter.STATE_ON;
import static android.bluetooth.BluetoothDevice.BOND_BONDED;
import static edu.unt.nsllab.butshuti.bluetoothvpn.vpn.BTVpnService.ClientState.CONNECTED;
import static edu.unt.nsllab.butshuti.bluetoothvpn.vpn.BTVpnService.ClientState.CONNECTING;
import static edu.unt.nsllab.butshuti.bluetoothvpn.vpn.BTVpnService.ClientState.EXCEPTION;
import static edu.unt.nsllab.butshuti.bluetoothvpn.vpn.BTVpnService.ClientState.FAILED;
import static edu.unt.nsllab.butshuti.bluetoothvpn.vpn.BTVpnService.ClientState.READY;

public class BTVpnService extends VpnService implements InterfaceController.NetworkEventListener, InterfaceController.LocalDatagramDeliveryListener, InterfaceController.InterfaceConfigurationView {
    enum ClientState{
        READY,
        CONNECTING,
        CONNECTED,
        EXCEPTION,
        FAILED
    }

    enum InterfaceConfigMode{
        ROUTER,
        SERVER,
        CLIENT,
        NONE
    }

    private static final String PKG_PREFIX = "unt.nsl.andcpr.peering.services";
    public static final String BT_DOWN = PKG_PREFIX + ".bt_down";
    public static final String BT_SCANNING = PKG_PREFIX + ".bt_scanning";
    public static final String BT_SCANNING_CONFIGURED_PEER_NOT_FOUND = PKG_PREFIX + ".bt_scanning_cfg_peer_not_found";
    public static final String BT_CONNECT_FAILED = PKG_PREFIX + ".bt_connect_failed";
    public static final String BT_DISCONNECTED = PKG_PREFIX + ".bt_disconnected";
    public static final String BT_CONNECTING = PKG_PREFIX + ".bt_connecting";
    public static final String BT_CONNECTED = PKG_PREFIX + ".bt_connected";
    public static final String BT_PAIRED = PKG_PREFIX + ".bt_bonded";
    public static final String BT_UNPAIRED = PKG_PREFIX + ".bt_unbonded";
    public static final String BT_PAIRING = PKG_PREFIX + ".bt_bonding";
    public static final String BT_PAIRING_FAILED = PKG_PREFIX + ".bt_bonding_failed";
    public static final String BT_REMOTE_PEER_NAME = PKG_PREFIX + "bt_device_name";
    public static final String BT_SERVICE_CURRENT_SD_CONFIG = "bt_service_locator_current_config";
    public static final String BT_VPN_SERVICE_STARTED = "bt_vpn_service_started";
    public static final int STATUS_UPDATE_EXPIRATION = 3000 ;
    public static final int MTU = 256;
    public static BTVpnService instance = null;
    private final MutableLiveData<ServiceStatusWrapper> serviceStatusWrapper = new MutableLiveData<>();

    private BluetoothAdapter btAdapter;
    private BluetoothDevice configuredPeer = null;
    private List<BluetoothDevice> selectedPeers;
    private BroadcastReceiver btStateBroadcastReceiver = null;
    private TimerTask continuousDiscoveryTask = null;
    private Timer networkSDTimer;
    private BluetoothSocketWrappers.ClientSocket clientSocket;
    private BluetoothSocketWrappers.ServerSocket serverSocket;
    private InterfaceController interfaceController;
    private ClientBluetoothSocketAdaptor clientBluetoothSocketAdaptor;
    private ServerBluetoothSockerAdaptor serverBluetoothSockerAdaptor;
    private ClientState clientState = READY;
    private NotificationManager notificationManager;
    private int NOTIFICATION = R.string.vpn_service_activated;
    private ControllerThread controllerThread;
    private String localBDAddr;
    private SDConfig sdConfig;

    private InterfaceConfigMode interfaceConfigMode = InterfaceConfigMode.NONE;


    public BTVpnService() {

    }

    @Override
    public void onCreate(){
        super.onCreate();
        sdConfig = new SDConfig(getApplicationContext());
        notificationManager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        btStateBroadcastReceiver = getBTStateBroadcastReceiver();
        registerReceiver(btStateBroadcastReceiver, getBTStateIntentFilter());
        interfaceController = InterfaceController.getDefault(this);
        interfaceController.registerEventListener(this);
        interfaceController.registerLocalDeliveryListener(this);
        clientBluetoothSocketAdaptor = null;
        selectedPeers = new ArrayList<>();
        localBDAddr = sdConfig.getConfiguredLocalNetDevID();
        instance = this;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId){
        refreshNetConfig();
        prepare();
        notifyServiceStarted();
        updateServiceStatusDescription();
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy(){
        super.onDestroy();
        if(notificationManager != null){
            notificationManager.cancel(NOTIFICATION);
        }
        if(btStateBroadcastReceiver != null) {
            unregisterReceiver(btStateBroadcastReceiver);
        }
        if(btAdapter != null) {
            btAdapter.cancelDiscovery();
        }
        if(interfaceController != null){
            interfaceController.deactivate();
        }
        if(clientBluetoothSocketAdaptor != null){
            clientBluetoothSocketAdaptor.stopAdaptor();
        }
        if(serverBluetoothSockerAdaptor != null){
            serverBluetoothSockerAdaptor.stopAdaptor();
        }
        if(clientSocket != null){
            try {
                clientSocket.close();
            } catch (IOException e) {
                Logger.logE(e.getMessage());
            }
            clientSocket = null;
        }
        if(controllerThread != null){
            controllerThread.terminate();
        }
        stopSelf();
        Logger.logI("VPN: service destroyed.");
    }

    @Override
    public void notifyIrrecoverableException(String channelID, String remotePeerAddress) {
        if(clientBluetoothSocketAdaptor != null && clientBluetoothSocketAdaptor.getChannelID().equals(channelID)){
            if(clientSocket != null && clientSocket.getRemoteDevice().getAddress().equals(remotePeerAddress)){
                updateClientState(EXCEPTION);
                try {
                    resetPeerConnection();
                } catch (IOException e) {
                    Logger.logE(e.getMessage());
                }
            }
        }
    }

    @Override
    public void onNewConnection(String channelID, String remotePeerAddress){
        Logger.logI("Connected to " + remotePeerAddress);
        updateClientState(CONNECTED);
        notifyPeerConnected();
    }

    @Override
    public boolean deliver(Packet pkt) {
        if(controllerThread != null){
            return controllerThread.deliver(pkt.getData());
        }
        return false;
    }

    @Override
    public String getLocalBDAddr() {
        return localBDAddr;
    }

    @Override
    public String getInterfaceAddress() {
        if(getLocalBDAddr() != null){
            NetUtils.AddrConfig config = NetUtils.getLocalBTReservedIfaceConfig();
            try {
                InetAddress ifaceAddress = NetUtils.getResolvableAddress(config, getLocalBDAddr());
                return ifaceAddress.getHostAddress() + "/" + config.prefixLen;
            } catch (UnknownHostException e) {
                Logger.logE(e.getMessage());
            }
        }
        return null;
    }

    @Override
    public void updateLocalBDAddr(String BDAddr) {
        if(localBDAddr == null){
            localBDAddr = BDAddr;
            sdConfig.setConfiguredLocalNetDevID(BDAddr);
            try {
                if(clientBluetoothSocketAdaptor != null) {
                    clientBluetoothSocketAdaptor.startAdaptor();
                }
                if(serverBluetoothSockerAdaptor != null) {
                    serverBluetoothSockerAdaptor.stopAdaptor();
                }
                if(controllerThread != null){
                    controllerThread.terminate();
                    try {
                        controllerThread.join();
                    } catch (InterruptedException e) {
                        Logger.logE(e.getMessage());
                    }
                }
                prepare();
            } catch (IOException e) {
                Logger.logE(e.getMessage());
            }
        }
    }

    private void prepare(){
        //Must be in ready clientState to start any session changes
        ServiceStatusWrapper serviceStatus = new ServiceStatusWrapper();
        serviceStatus.setHostName(getLocalBDAddr());
        serviceStatus.setInterfaceAddress(getInterfaceAddress());
        if(enableNetworking()){
            configuredPeer = null;
            String configuredAddress = sdConfig.getConfiguredPeerSSID();
            try{
                configuredPeer = btAdapter.getRemoteDevice(configuredAddress);
            }catch (IllegalArgumentException e){
                configuredPeer = null;
                Logger.logE(e.getMessage());
            }
            if(!configurePeerSession()){
                if(configuredPeer != null) {
                    connectWithConfiguredPeer();
                }else if(clientModeEnabled()){
                    //No configured peer. Start discovery if enabled to do so.
                    startServiceDiscovery();
                }
            }
            if((controllerThread == null || !controllerThread.isAlive()) && getInterfaceAddress() != null){
                PendingIntent configureIntent = PendingIntent.getActivity(this, 0, new Intent(this, BluetoothVPNActivity.class), 0);
                Builder builder = new Builder();
                String interfaceAddress[] = getInterfaceAddress().split("/");
                NetUtils.AddrConfig config = NetUtils.getLocalBTReservedIfaceConfig();
                builder.addAddress(interfaceAddress[0], Integer.valueOf(interfaceAddress[1]));
                builder.addRoute(config.getIpAddr(), config.prefixLen);
                builder.setMtu(MTU);
                builder.setBlocking(false);
                builder.setSession(String.format("Bluetooth VPN through %s", getConfiguredPeerName()));
                builder.setConfigureIntent(configureIntent);
                ParcelFileDescriptor fd = builder.establish();
                if(fd != null){
                    controllerThread =  new ControllerThread(fd, interfaceController, MTU);
                    controllerThread.start();
                    showNotification(getString(R.string.msg_vpn_service_started), false);
                }else{
                    Logger.logE("Error establishing VPN....");
                }
            }else if(getInterfaceAddress() == null && interfaceController.isActive()){
                interfaceController.pingProximities();
            }
        }
        serviceStatusWrapper.postValue(serviceStatus);
    }

    public static BTVpnService getInstance(){
        return instance;
    }

    public MutableLiveData<ServiceStatusWrapper> getServiceStatusWrapper() {
        return serviceStatusWrapper;
    }

    public static boolean isActive(){
        return instance != null && instance.controllerThread != null && instance.controllerThread.isActive()
                && instance.interfaceController.isActive() && !instance.interfaceConfigMode.equals(InterfaceConfigMode.NONE);
    }

    private boolean clientModeEnabled(){
        refreshNetConfig();
        return interfaceConfigMode.equals(InterfaceConfigMode.CLIENT) ||
                interfaceConfigMode.equals(InterfaceConfigMode.ROUTER);
    }

    private boolean serverModeEnabled(){
        refreshNetConfig();
        return interfaceConfigMode.equals(InterfaceConfigMode.SERVER) ||
                interfaceConfigMode.equals(InterfaceConfigMode.ROUTER);
    }

    private void refreshNetConfig(){
        sdConfig.refreshNetConfing();
        if(sdConfig.isServerModeEnabled()){
            if(sdConfig.isClientModeEnabled()){
                interfaceConfigMode = InterfaceConfigMode.ROUTER;
            }else{
                interfaceConfigMode = InterfaceConfigMode.SERVER;
            }
        }else{
            interfaceConfigMode = InterfaceConfigMode.CLIENT;
        }
    }

    private void startServiceDiscovery(){
        if(configuredPeerFound()){
            //No need to do service discovery if there already is a configured peer.
            return;
        }
        synchronized (BTVpnService.class){
            if(btAdapter != null){
                if(btAdapter.isDiscovering()){
                    btAdapter.cancelDiscovery();
                }
                btAdapter.disable();
                btAdapter = null;
            }
        }
        selectedPeers.clear();
        if(startServiceLocator()){
            updateClientState(READY);
        }
    }

    private boolean startServiceLocator(){
        Intent intent = new Intent();
        intent.setAction(getString(R.string.action_bt_service_locator));
        intent.setType("text/json");
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        return true;
    }

    private boolean enableNetworking() {
        initializeBTAdapter();
        if(btAdapter != null ){
            return btAdapter.enable();
        }
        return false;
    }

    private boolean configuredPeerFound(){
        return configuredPeer != null && clientState != FAILED;
    }

    private boolean configurePeerSession() {
        sdConfig.refreshNetConfing();
        updateServiceStatusDescription();
        Logger.logI("ConfigurePeerSession...........");
        if(btAdapter == null){
            initializeBTAdapter();
            return false;
        }
        if(serverModeEnabled() && !listening()){
            startServer();
        }
        if(clientModeEnabled()){
            for(BluetoothDevice device : btAdapter.getBondedDevices()){
                if(sdConfig.verifyConfiguredNetDevID(device.getAddress(), device.getName())){
                    //Paired with the configured peer, now start a session
                    onDeviceFound(device);
                    return true;
                }
            }
        }
        return false;
    }

    private void updateServiceStatusDescription(){
        ServiceStatusWrapper ssw = new ServiceStatusWrapper();
        ssw.setHostName(getLocalBDAddr());
        ssw.setInterfaceAddress(getInterfaceAddress());
        Set<Peer> peers = new HashSet<>();
        NetUtils.AddrConfig config = NetUtils.getLocalBTReservedIfaceConfig();
        if(serverBluetoothSockerAdaptor != null){
            for(String btAddress : serverBluetoothSockerAdaptor.getRecentClients()){
                try {
                    peers.add(new Peer(sdConfig.getFriendlyName(getRemoteDevice(btAddress)), NetUtils.getResolvableAddress(config, btAddress).getHostAddress(), Peer.Status.CONNECTED));
                } catch (UnknownHostException e) {
                    Logger.logE(e.getMessage());
                }
            }
        }
        if (clientModeEnabled() && getConfiguredPeerName() != null) {
            try {
                String address = NetUtils.getResolvableAddress(config, sdConfig.getConfiguredPeerSSID()).getHostAddress();
                Peer p = new Peer(getConfiguredPeerName(), address, connectedWithConfiguredPeer() ? Peer.Status.CONNECTED : Peer.Status.DISCONNECTED);
                if(!peers.contains(p)){
                    peers.add(p);
                }
            } catch (UnknownHostException e) {
                Logger.logE(e.getMessage());
            }
        }
        ssw.setClients(peers);
        serviceStatusWrapper.postValue(ssw);
    }

    private void onDeviceFound(BluetoothDevice device){
        if(sdConfig.verifyConfiguredNetDevID(device.getAddress(), device.getName())){
            if(configuredPeer == null || !device.equals(configuredPeer)){
                updateClientState(READY);
            }
            configuredPeer = device;
            if(btAdapter == null){
                initializeBTAdapter();
            }
            if(clientModeEnabled()){
                updateClientState(READY);
                notifyProvisionaryState(CONNECTING, false);
                if(btAdapter != null){
                    btAdapter.cancelDiscovery();
                    connectWithConfiguredPeer();
                }
            }
        }
    }

    private boolean inReadyState(){
        return clientState == READY || clientState == FAILED;
    }

    private boolean networkingEnabled(){
        synchronized (BTVpnService.class){
            if(btAdapter == null){
                btAdapter = BluetoothAdapter.getDefaultAdapter();
            }
            return btAdapter != null && btAdapter.enable();
        }
    }

    private void initializeBTAdapter(){
        Logger.logI("Initializing BT adaptor...");
        synchronized (BTVpnService.class) {
            if (btAdapter == null) {
                btAdapter = BluetoothAdapter.getDefaultAdapter();
            }
            if (!btAdapter.isEnabled()) {
                btAdapter.enable();
            }
        }
    }

    private boolean updateClientState(ClientState newState){
        Logger.logI("Change clientState: " + clientState.name() + " => " + newState.name());
        clientState = newState;
        notifyProvisionaryState(clientState, false);
        return true;
    }

    private boolean connectWithConfiguredPeer(){
        if(configuredPeer == null){
            return false;
        }
        //Attempt to connect will be done only in ready states.
        if(inReadyState()){
            updateClientState(CONNECTING);
            connect();
            if(connectedWithConfiguredPeer()){
                updateClientState(CONNECTED);
            }
            notifyPeerConnecting();
        }
        return clientState == CONNECTED;
    }

    private void scheduleSDTask(){
        TimerTask stateRefreshTask = new TimerTask() {
            @Override
            public void run() {
                Thread.currentThread().setName("BTVPNService#scheduleSDTask()");
                if(!refreshRouteInfo()){
                    return;
                }
                validatePeerConnections();
            }
        };
        if(networkSDTimer != null){
            networkSDTimer.cancel();
        }
        networkSDTimer = new Timer();
        networkSDTimer.scheduleAtFixedRate(stateRefreshTask, 0, STATUS_UPDATE_EXPIRATION);
    }

    private void validatePeerConnections(){
        if(!isActive() || (clientModeEnabled() && !connectedWithConfiguredPeer())){
            prepare();
        }
    }

    private void connect(){
        initializeBTAdapter();
        if(!connectedWithConfiguredPeer()) {
            new Thread() {
                @Override
                public void run() {
                    try {
                        setName("BTVPNService#connect() @ " + getName());
                        clientSocket = new BluetoothSocketWrappers.ClientSocket(btAdapter, configuredPeer);
                        interfaceController.activate();
                        clientBluetoothSocketAdaptor = new ClientBluetoothSocketAdaptor(clientSocket, interfaceController);
                        try {
                            clientBluetoothSocketAdaptor.startAdaptor();
                            try {
                                Thread.sleep(STATUS_UPDATE_EXPIRATION);
                            } catch (InterruptedException e) {

                            }
                            if(clientSocket != null && clientSocket.isConnected()){
                                scheduleSDTask();
                                notifyPeerConnected();
                            }
                        } catch (RemoteInterfaceAdaptor.RemoteInterfaceException e) {
                            updateClientState(FAILED);
                            resetPeerConnection();
                        }
                    } catch (IOException e) {
                        updateClientState(FAILED);
                        Logger.logE(e.getMessage());
                    }
                }
            }.start();
        }
    }

    private boolean listening(){
        return serverBluetoothSockerAdaptor != null && serverBluetoothSockerAdaptor.isActive() && serverSocket != null;
    }

    private void startServer(){
        initializeBTAdapter();
        if(btAdapter == null){
            Logger.logE("BT adapter not yet initialized.");
            return;
        }
        if(!listening()){
            new Thread(){
                @Override
                public void run(){
                    setName("BTVPNService#startServer() @ " + getName());
                    try{
                        serverSocket = new BluetoothSocketWrappers.ServerSocket(btAdapter);
                        serverBluetoothSockerAdaptor = new ServerBluetoothSockerAdaptor(serverSocket, interfaceController);
                        interfaceController.activate();
                        serverBluetoothSockerAdaptor.startAdaptor();
                    }catch (IOException e){
                        Logger.logE(e.getMessage());
                        try {
                            if(serverSocket != null){
                                serverSocket.close();
                            }
                            if(serverBluetoothSockerAdaptor != null){
                                serverBluetoothSockerAdaptor.stopAdaptor();
                            }
                        } catch (IOException e1) {

                        }
                    }
                }
            }.start();
        }
    }

    private boolean refreshRouteInfo() {
        if(clientSocket != null && clientSocket.isConnected()){
            return clientBluetoothSocketAdaptor.isActive();
        }
        if(networkSDTimer == null){
            scheduleSDTask();
        }
        networkSDTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                Thread.currentThread().setName("BTVPNService#refreshRouteInfo() @ " + Thread.currentThread().getName());
                if(connectedWithConfiguredPeer()) {
                    configurePeerSession();
                }
            }
        }, STATUS_UPDATE_EXPIRATION);
        return false;
    }

    private void resetPeerConnection() throws IOException {
        synchronized (BTVpnService.class) {
            if (clientSocket != null || clientBluetoothSocketAdaptor != null) {
                if (clientSocket != null) {
                    clientSocket.close();
                    clientSocket = null;
                }
                if (clientBluetoothSocketAdaptor != null) {
                    clientBluetoothSocketAdaptor.stopAdaptor();
                    clientBluetoothSocketAdaptor = null;
                }
                notifyPeerDisconnected();
            }
            updateClientState(FAILED);
            notifyPeerConnectFailed();
        }
    }

    private IntentFilter getBTStateIntentFilter(){
        IntentFilter ret = new IntentFilter();
        ret.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        ret.addAction(BluetoothDevice.ACTION_FOUND);
        ret.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        ret.addAction(BluetoothDevice.ACTION_PAIRING_REQUEST);
        ret.addAction(getString(R.string.action_bt_configured_peer_changed));
        ret.addAction(getString(R.string.action_bt_configured_peer_revoked));
        return ret;
    }

    private BroadcastReceiver getBTStateBroadcastReceiver(){
        return new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if(BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)){
                    int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1);
                    int previousState = intent.getIntExtra(BluetoothAdapter.EXTRA_PREVIOUS_STATE, -1);
                    if(state == STATE_ON){
                        notifyNetworkConnected();
                    }else if(state == STATE_OFF){
                        notifyNetworkDisconnected();
                    }else if(state == STATE_DISCONNECTED){
                        notifyPeerDisconnected();
                    }
                }else if(BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)){
                    int state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1);
                    int previousState = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, -1);
                    if(state == BOND_BONDED && previousState != BOND_BONDED){
                        BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                        notifyPairingCompleted(sdConfig.getFriendlyName(device));
                    }else if(state == BluetoothDevice.BOND_BONDING){
                        BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                        notifyPairingStarted(sdConfig.getFriendlyName(device));
                    }else if(state == BluetoothDevice.BOND_NONE){
                        BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                        notifyUnpaired(sdConfig.getFriendlyName(device));
                    }else{
                        notifyPeerDisconnected();
                    }
                }else if(BluetoothDevice.ACTION_PAIRING_REQUEST.equals(action)){
                    Logger.logD("Pairing request");
                }else if(getString(R.string.action_bt_configured_peer_changed).equals(action)){
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    String currentConfig = intent.getStringExtra(BT_SERVICE_CURRENT_SD_CONFIG);
                    if(currentConfig != null){
                        try {
                            JSONObject config = new JSONObject(currentConfig);
                            sdConfig.applyConfig(config);
                            if(device != null){
                                onDeviceFound(device);
                            }
                        } catch (JSONException e) {
                            Logger.logE(e.getMessage());
                        }
                    }
                }else if(getString(R.string.action_bt_configured_peer_revoked).equals(action)){
                    Logger.logE("next()() -- peer revoked");
                    resetConfiguredPeer();
                }
            }
        };
    }

    /**
     * Notify listening front-ends that the local network is back up (Wi-Fi / Wi-Fi Direct turned on).
     */
    private void notifyNetworkConnected(){
        //Bluetooth just got (re-)enabled
        enableNetworking();
    }

    /**
     * Notify listening front-ends that the service has started.
     */
    private void notifyServiceStarted() {
        synchronized (BTVpnService.class) {
            Intent intent = new Intent();
            intent.setAction(BT_VPN_SERVICE_STARTED);
            sendBroadcast(intent);
        }
    }

    /**
     * Notify listening front-ends that the local network is down (BT turned off).
     */
    private void notifyNetworkDisconnected() {
        synchronized (BTVpnService.class) {
            if(interfaceController != null){
                interfaceController.deactivate();
            }
            btAdapter = null;
            Intent intent = new Intent();
            intent.setAction(BT_DOWN);
            sendBroadcast(intent);
        }
    }

    /**
     * Notify listening front-ends that connectivity has been lost (remote peer went offline).
     */
    private void notifyPeerDisconnected() {
        if(networkingEnabled() && networkSDTimer != null) {
            if(continuousDiscoveryTask != null){
                continuousDiscoveryTask.cancel();
            }
            continuousDiscoveryTask = new TimerTask() {
                @Override
                public void run() {
                    Thread.currentThread().setName("BTVPNService#continuousDiscoveryTask @ " + Thread.currentThread().getName());
                    if (connectedWithConfiguredPeer()) {
                        this.cancel();
                    } else {
                        configurePeerSession();
                    }
                }
            };
            try{
                networkSDTimer.scheduleAtFixedRate(continuousDiscoveryTask, STATUS_UPDATE_EXPIRATION, STATUS_UPDATE_EXPIRATION);
            }catch (IllegalStateException e){
                //Timer may have been cancelled.
                scheduleSDTask();
            }
        }
        Intent intent = new Intent();
        intent.setAction(BT_DISCONNECTED);
        intent.putExtra(BT_REMOTE_PEER_NAME, getConfiguredPeerName());
        sendBroadcast(intent);
    }


    private void notifyPeerConnectFailed() {
        Intent intent = new Intent();
        intent.setAction(BT_CONNECT_FAILED);
        intent.putExtra(BT_REMOTE_PEER_NAME, getConfiguredPeerName());
        sendBroadcast(intent);
        //Start connection attempt heartbeat if none exists.
        if(networkSDTimer == null){
            networkSDTimer = new Timer();
            notifyPeerDisconnected();
        }
    }

    /**
     * Notify listening front-ends that a connection to the selected peer has been established.
     */
    private void notifyPeerConnected() {
        btAdapter.cancelDiscovery();
        if(continuousDiscoveryTask != null) {
            continuousDiscoveryTask.cancel();
        }
        Intent intent = new Intent();
        intent.setAction(BT_CONNECTED);
        intent.putExtra(BT_REMOTE_PEER_NAME, getConfiguredPeerName());
        sendBroadcast(intent);
        String msg = "Connected with " + getConfiguredPeerName();
        notifyProvisionaryState(CONNECTED, true);
        updateServiceStatusDescription();
    }

    /**
     * Notify listening front-ends that a connection to the selected peer is in progress.
     */
    private void notifyPeerConnecting() {
        Intent intent = new Intent();
        intent.setAction(BT_CONNECTING);
        intent.putExtra(BT_REMOTE_PEER_NAME, getConfiguredPeerName());
        sendBroadcast(intent);
    }

    /**
     * Notify listening front-ends that a bonding to the selected peer has been established.
     * @param peerName
     */
    private void notifyPairingCompleted(String peerName) {
        if(peerName != null){
            Intent intent = new Intent();
            intent.setAction(BT_PAIRED);
            intent.putExtra(BT_REMOTE_PEER_NAME, peerName);
            sendBroadcast(intent);
        }
    }

    private void notifyUnpaired(String peerName) {
        if(peerName == null || peerName.isEmpty()){
            return;
        }
        if(configuredPeer != null && peerName.equals(configuredPeer.getName())){
            try {
                resetPeerConnection();
            } catch (IOException e) {}
        }
        Intent intent = new Intent();
        intent.setAction(BT_UNPAIRED);
        intent.putExtra(BT_REMOTE_PEER_NAME, peerName);
        sendBroadcast(intent);
    }

    /**
     * Notify listening front-ends that a bonding to the selected peer is in progress.
     * @param peerName
     */
    private void notifyPairingStarted(String peerName) {
        if(peerName == null || peerName.isEmpty()){
            return;
        }
        Intent intent = new Intent();
        intent.setAction(BT_PAIRING);
        intent.putExtra(BT_REMOTE_PEER_NAME, peerName);
        sendBroadcast(intent);
    }

    private void notifyProvisionaryState(ClientState state, boolean newSession) {
        Logger.logI(String.format("Provisionary state: %s, new session: %s", state, newSession ? "Yes" : "No"));
        showNotification(state.name(), false);
    }

    private String getConfiguredPeerName(){
        return sdConfig.getConfiguredPeerName();
    }

    private BluetoothDevice getRemoteDevice(String devAddr){
        if(btAdapter != null){
            return btAdapter.getRemoteDevice(devAddr);
        }
        return null;
    }

    private void resetConfiguredPeer() {
        sdConfig.resetConfiguredPeer();
        try {
            resetPeerConnection();
        } catch (IOException e) {

        }
        startServiceDiscovery();
    }



    private boolean connectedWithConfiguredPeer(){
        synchronized (BTVpnService.class) {
            if (clientSocket == null || clientBluetoothSocketAdaptor == null || configuredPeer == null) {
                return false;
            }
            if (clientSocket.getRemoteDevice().equals(configuredPeer)) {
                return clientSocket.isConnected() && clientBluetoothSocketAdaptor.isActive();
            }
            return false;
        }
    }

    private void showNotification(String text, boolean failureState) {
        //PendingIntent to launch the SD test activity if the user selects this notification
        Class<?> cls = BluetoothVPNActivity.class;
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, cls), 0);
        Intent stopServiceIntent =  new Intent(this, cls).setAction(getString(R.string.action_vpn_stop_service));
        Intent networkStatusServiceIntent =  new Intent(this, cls).setAction(getString(R.string.action_vpn_show_status));
        PendingIntent stopServicePendingIntent = PendingIntent.getActivity(this, 0, stopServiceIntent, 0);
        PendingIntent networkStatusPendingIntent = PendingIntent.getActivity(this, 0, networkStatusServiceIntent, 0);
        Notification.Action stopServiceAction = new Notification.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopServicePendingIntent).build();
        Notification.Action networkStatusAction = new Notification.Action.Builder(android.R.drawable.ic_menu_manage, "View", networkStatusPendingIntent).build();
        // Set the info for the views that show in the notification panel.
        Notification notification = new Notification.Builder(this)
                .setSmallIcon(android.R.drawable.presence_online)
                .setTicker(text)
                .setWhen(System.currentTimeMillis())
                .setContentTitle("Bluetooth VPN")
                .setContentText(text)
                .setContentIntent(contentIntent)
                .addAction(networkStatusAction)
                .addAction(stopServiceAction)
                .setOngoing(true)
                .setColor(failureState ? Color.RED : Color.GREEN)
                .build();
        notificationManager.notify(NOTIFICATION, notification);
    }
}