package edu.unt.nsllab.butshuti.bluetoothvpn.data.objects;

import com.google.common.net.InetAddresses;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

import edu.unt.nsllab.butshuti.bluetoothvpn.utils.Logger;
import edu.unt.nsllab.butshuti.bluetoothvpn.utils.measurement.CDF;

import static edu.unt.nsllab.butshuti.bluetoothvpn.data.objects.ReachabilityTestResult.DiscoveryMode.UNSPECIFIED;

/**
 * ReachabilityTestResult is a container for service discovery results.
 *
 * @author butshuti
 */
public class ReachabilityTestResult {
    private static final String PARAM_SERVICE_ADDR = "svc_addr";
    private static final String PARAM_RTT = "rtt";
    private static final String PARAM_CONFIGURED_TIMEOUT = "cfg_timeout";
    private static final String PARAM_FAILURE_REASON = "failure_reason";
    private static final String PARAM_SESS_ID = "sess_id";
    private static final String PARAM_NUM_PROBES = "num_probes";
    private static final String PARAM_PROBE_RTTS = "probeRTTs";
    private static final String PARAM_NUM_ATTEMPTS = "num_attemps";
    private static final String PARAM_DURATION_SECONDS = "duration_seconds";
    private static final String PARAM_NUM_REQUESTED_PROBES = "num_requested_probes";
    private static final String PARAM_DISCOVERY_MODE = "probe_protocol";
    private static final String PARAM_NUM_HOPS = "num_hops";
    private static final String PARAM_PKT_SIZE = "pkt_size";

    private InetAddress serviceAddr, localAddr, broadcastAddr;
    private long sessId;
    private long sdStart;
    private int configuredTimeout;
    private int numHops;
    private int packet_size;
    private int numRequestedProbes, numAttempts, durationSeconds;
    private long cumulRTT;
    private DiscoveryMode discoveryMode;
    private List<Integer> probeRTTs;
    private String[] route;
    private Status status = Status.NONE;
    private static InetAddress NULL_ADDR = InetAddress.getLoopbackAddress();
    public final static ReachabilityTestResult EMPTY_SD = emptySD();
    public enum Status {
        NONE("Operation not started"),
        PENDING("Operation in progress"),
        SUCCESSFUL("Operation successful"),
        FAILED("Operation failed"),
        CANCELLED("Operation cancelled");
        private String expl;

        Status(String expl){
            this.expl = expl;
        }

        public String getExpl(){return expl;}
    }

    public enum DiscoveryMode{
        ECHO, ICMP, UNSPECIFIED
    }

    public boolean isEmpty(){
        return sessId == 0 || NULL_ADDR.equals(serviceAddr) || NULL_ADDR.equals(broadcastAddr);
    }

    /**
     * Return an empty/uninitialized SD
     * @return
     */
    public static ReachabilityTestResult emptySD (){
        return new ReachabilityTestResult(0, NULL_ADDR, NULL_ADDR, NULL_ADDR, 0, -1);
    }

    /**
     * Record the last known status as 'cancelled'
     * @return the target instance
     */
    public ReachabilityTestResult setCancelled(DiscoveryMode discoveryMode){
        status = Status.CANCELLED;
        this.discoveryMode = discoveryMode;
        return this;
    }

    /**
     * Record the last known status as 'failed'
     * @return the target instance
     */
    public ReachabilityTestResult setFailed(DiscoveryMode discoveryMode, int packetSize, int numHops){
        status = Status.FAILED;
        this.discoveryMode = discoveryMode;
        this.packet_size = packetSize;
        this.numHops = numHops;
        return this;
    }

    /**
     * Record the last known status as 'completed'
     * @return the target instance
     */
    public ReachabilityTestResult setCompleted(DiscoveryMode discoveryMode, int packetSize, int numHops){
        status = Status.SUCCESSFUL;
        this.discoveryMode = discoveryMode;
        this.packet_size = packetSize;
        this.numHops = numHops;
        return this;
    }

    public void recordTimeOut(){
        recordRTT(configuredTimeout);
    }

    /**
     * Record the RTT for the current probe
     * @param rtt
     */
    private void recordRTT(int rtt){
        if(probeRTTs.size() < numRequestedProbes) {
            probeRTTs.add(rtt);
            cumulRTT += rtt;
        }
    }

    /**
     * Record the SD task duration
     * @param numAttempts duration in number of attempts
     * @param durationSeconds duration in number of seconds elapsed
     */
    public void recordElapsedTime(int numAttempts, int durationSeconds){
        this.numAttempts = numAttempts;
        this.durationSeconds = durationSeconds;
    }

    public ReachabilityTestResult recordRoute(List<String> route){
        if(route != null){
            numHops = ((1 + route.size()) / 2) - 1;
            this.route = new String[route.size()];
            route.toArray(this.route);
        }
        return this;
    }

    public boolean isCancelled(){return status.equals(Status.CANCELLED);}

    public String getDiscoveryMode(){
        return discoveryMode.name();
    }

    public int getNumHops(){
        return (route != null && route.length > 0) ? ((route.length + 1)/2) - 1 : numHops;
    }

    public String[] getRoute(){
        return route;
    }

    public Status getStatus(){return status;}
    /**
     * @return the number of probes done for the discovery.
     */
    public int getNumProbes() {
        return probeRTTs.size();
    }

    /**
     * Return the configured so_timeout
     * @return
     */
    public long getConfiguredTimeout(){
        return configuredTimeout;
    }

    /**
     * Return the number of requested probes
     * @return
     */
    public int getNumRequestedProbes(){
        return numRequestedProbes;
    }

    /**
     *
     * @return The current session ID
     */
    public long getSessId() {
        return sessId;
    }

    /**
     *
     * @return The broadcast address for the service
     */
    public InetAddress getBroadcastAddr() {
        return broadcastAddr;
    }

    /**
     *
     * @return  The local address used in the probes
     */
    public InetAddress getLocalAddr() {
        return localAddr;
    }

    /**
     *
     * @return  The discovered service address
     */
    public InetAddress getServiceAddr() {
        return serviceAddr;
    }



    /**
     * Initializes parameters for a new session discovery.
     * @param sess_id   The session ID
     * @param local_addr    The local address
     * @param service_addr  The service address
     * @param broadcast_addr    The broadcast address
     */
    public ReachabilityTestResult(long sess_id, InetAddress local_addr, InetAddress service_addr, InetAddress broadcast_addr, int configuredTimeout, int numRequestedProbes){
        sessId = sess_id;
        localAddr = local_addr;
        serviceAddr = service_addr;
        broadcastAddr = broadcast_addr;
        this.numRequestedProbes = numRequestedProbes;
        this.probeRTTs = new ArrayList<>();
        cumulRTT = 0;
        this.configuredTimeout = configuredTimeout;
        durationSeconds = 0;
        numAttempts = 0;
        sdStart = -1;
        numHops = -1;
        packet_size = -1;
        discoveryMode = UNSPECIFIED;
    }

    /**
     *
     * @return The average RTT of all probes in the discovery
     */
    public float getAvgRTT(){
        int numProbes = getNumProbes();
        if(numProbes == 0){
            return -1;
        }
        return Math.round(100*cumulRTT/numProbes)/100.0f;
    }

    public int getPacketSize(){
        return packet_size;
    }

    /**
     * Get the recorded SD task duration in number of attempts
     * @return the number of attempts/retries
     */
    public int getNumAttempts(){
        return numAttempts;
    }

    /**
     * Get the recorded SD task duration in seconds
     * @return the number of seconds since the first attempt
     */
    public int getDurationSeconds(){
        return durationSeconds;
    }

    /**
     * Return a CDFDiagram of the recorded probe RTTs.
     * @return
     */
    public CDF getCDF(float resolution){
        float values[] = new float[probeRTTs.size()];
        for(int i=0; i<probeRTTs.size(); i++){
            values[i] = probeRTTs.get(i).floatValue();
        }
        return new CDF(values, resolution);
    }

    /**
     * Updates an {@link ReachabilityTestResult instance} with new parameters.
     * @param arg   The instance to update
     * @param sessID    The new session ID
     * @param rtt   The new RTT
     * @param serviceAddr   The new Service address
     * @return  An updated version of the argument
     */
    public static ReachabilityTestResult update(ReachabilityTestResult arg, long sessID, int rtt, InetAddress serviceAddr){
        if(sessID != arg.sessId || !serviceAddr.equals(arg.serviceAddr)){
            return emptySD();
        }
        long curTimeSeconds = System.nanoTime() / 1000000000;
        if(arg.sdStart < 0){
            arg.sdStart = curTimeSeconds;
        }
        arg.durationSeconds = (int)(curTimeSeconds - arg.sdStart);
        arg.recordRTT(rtt);
        arg.status = Status.PENDING;
        return arg;
    }



    /**
     * Deserialize a ReachabilityTestResult object from a json-formatted string
     * @param s a json-formatted string
     * @return the parsed object, or NULL_SD if unsuccessful
     */
    public static ReachabilityTestResult fromString(String s){
        try {
            JSONObject obj = new JSONObject(s);
            long sessID = obj.getLong(PARAM_SESS_ID);
            int configuredTimeout = Float.valueOf(obj.getString(PARAM_CONFIGURED_TIMEOUT)).intValue();
            String svcAddressStr = obj.getString(PARAM_SERVICE_ADDR);
            int numProbes = Integer.valueOf(obj.getInt(PARAM_NUM_PROBES));
            JSONArray probeRTTArr = obj.getJSONArray(PARAM_PROBE_RTTS);
            List<Integer> probeRTTs = new ArrayList<>();
            if(numProbes == probeRTTArr.length()){
                for(int i=0; i<numProbes; i++){
                    probeRTTs.add(Float.valueOf(String.valueOf(probeRTTArr.get(i))).intValue());
                }
            }
            int numAttempts = Integer.valueOf(obj.getInt(PARAM_NUM_ATTEMPTS));
            int durationSeconds = Integer.valueOf(obj.getInt(PARAM_DURATION_SECONDS));
            int numRequestedProbes = Integer.valueOf(obj.getInt(PARAM_NUM_REQUESTED_PROBES));
            int numHops = Integer.valueOf(obj.getInt(PARAM_NUM_HOPS));
            int pktSize = Integer.valueOf(obj.getInt(PARAM_PKT_SIZE));
            String discoveryMode = obj.getString(PARAM_DISCOVERY_MODE);
            Status status = Status.valueOf(obj.getString(PARAM_FAILURE_REASON));
            InetAddress svcAddress = NULL_ADDR;
            try{
                svcAddress = InetAddresses.forString(svcAddressStr);
            }catch (IllegalArgumentException e){
                Logger.logE(e.getLocalizedMessage());
            }
            ReachabilityTestResult ret = new ReachabilityTestResult(sessID, NULL_ADDR, svcAddress, NULL_ADDR, configuredTimeout, numRequestedProbes);
            for(int i=0; i<probeRTTs.size(); i++){
                ret.recordRTT(probeRTTs.get(i));
            }
            ret.status = status;
            ret.numAttempts = numAttempts;
            ret.durationSeconds = durationSeconds;
            ret.numHops = numHops;
            ret.packet_size = pktSize;
            try{
                ret.discoveryMode = DiscoveryMode.valueOf(discoveryMode);
            }catch (IllegalArgumentException e){

            }
            return ret;
        } catch (JSONException | IllegalArgumentException e) {
            Logger.logE(e.getLocalizedMessage());
        }
        return emptySD();
    }

    /**
     * Serialize to a json-formatted string
     * @return a json-formatted string
     */
    public String toJSONString(){
        JSONObject ret = new JSONObject();
        try {
            ret.put(PARAM_NUM_PROBES, getNumProbes());
            ret.put(PARAM_PROBE_RTTS, new JSONArray(probeRTTs));
            ret.put(PARAM_NUM_ATTEMPTS, getNumAttempts());
            ret.put(PARAM_DURATION_SECONDS, getDurationSeconds());
            ret.put(PARAM_NUM_REQUESTED_PROBES, getNumRequestedProbes());
            ret.put(PARAM_SERVICE_ADDR, String.valueOf(serviceAddr == null ? NULL_ADDR.getHostAddress() : serviceAddr.getHostAddress()));
            ret.put(PARAM_CONFIGURED_TIMEOUT, String.valueOf(configuredTimeout));
            ret.put(PARAM_FAILURE_REASON, status.name());
            ret.put(PARAM_SESS_ID, getSessId());
            ret.put(PARAM_DISCOVERY_MODE, getDiscoveryMode());
            ret.put(PARAM_NUM_HOPS, getNumHops());
            ret.put(PARAM_PKT_SIZE, getPacketSize());
        } catch (JSONException | IllegalArgumentException e) {
            Logger.logE(e.getLocalizedMessage());
        }
        return ret.toString();
    }


}