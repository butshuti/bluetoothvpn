# Summary
A Bluetooth-based VPN application for Android.
 
 BluetoothVPN allows multi-hop connectivity between Android devices using P2P routing. 
 It can be used for group messaging and sensor data collection when the deployment scenario requires capabilities 
 that a simple piconet cannot support: an arbitrary number of inter-connected peers in the network 
 (up to 20 devices in transitive proximity), and a wider range (when peers are scattered as relays over a large coverage area).
 
 ![bluetoothvpn_home_small.png](https://bitbucket.org/repo/x8ELkxj/images/2484036129-bluetoothvpn_home_small.png)
 ![bluetoothvpn_ping_results_small.png](https://bitbucket.org/repo/x8ELkxj/images/2201839970-bluetoothvpn_ping_results_small.png)

 # How to Use
 ## Prerequisites
 1. Dowload sources and build with `Gradle`
 2. Install the `BluetoothVPN` APK on devices to connect
 ## Run and Connect
 1. Go to Bluetooth settings to scan and pair Bluetooth peers
 2. Launch the BluetoothVPN app
 3. Configure device's peering mode (2 modes are available: listening and connecting mode)
 4. Grant VPN permissions at the system prompt
 5. For each device, configure at least one gateway
    1.  Select `Routing Peers` in the main menu
    2.  Click on a discovered peer to open a peering connection (the selected device must be configured to accept peering connections)
    3.  (Note that some devices can accept more than one connection)
 6. Devices should reconnect to their configured peers on start from now on
 7. Note the network address namespace created by the VPN (`3.3.0.0/16`): traffic to IP addresses in that range will be routed through the VPN.
