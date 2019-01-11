package edu.unt.nsllab.butshuti.bluetoothvpn.iptools.sdquery;

import android.os.SystemClock;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import edu.unt.nsllab.butshuti.bluetoothvpn.data.objects.ReachabilityTestResult;
import edu.unt.nsllab.butshuti.bluetoothvpn.utils.GlobalExecutorService;
import edu.unt.nsllab.butshuti.bluetoothvpn.utils.Logger;

import static edu.unt.nsllab.butshuti.bluetoothvpn.iptools.sdquery.PingSuite.sysPingAvailable;


/**
 * Created by butshuti on 9/10/18.
 *
 * This class implements a service discovery executor.
 * <p>
 *     Exactly one instance of {@link SDQueryExecutor} will exist per application instance (servicing all activities and services).
 *     This instance will handle the main thread pool for pending and new queries.
 * </p>
 * @author butshuti
 */
public class SDQueryExecutor {

    public final static int DEFAULT_SO_TIMEOUT_MS = 300; //Socket timeout
    private final static int ECHO_SERV_PORT = 7;
    protected volatile int so_timeout_ms;
    protected boolean useCustomSoTimeout;
    private DatagramSocket datagramSocket = null;
    protected Map<String, Long> pendingAcks = new HashMap<>();
    private volatile boolean cancelled = false, freshRestart = false;
    private long taskGroupBlockingTimerTs = -1;
    private String label;

    private boolean useICMP = sysPingAvailable(); // Use system's ping utility for ICMP, if available.
    private Random random = new Random(System.currentTimeMillis());

    public SDQueryExecutor(String label){
        so_timeout_ms = DEFAULT_SO_TIMEOUT_MS;
        useCustomSoTimeout = true;
        this.label = label;
    }

    /**
     * Sets the SO_TIMEOUT on sockets
     * @param timeout
     */
    public void setSoTimeoutMs(int timeout){
        if(timeout <= 0){
            useCustomSoTimeout = false;
        }else{
            so_timeout_ms = timeout;
            useCustomSoTimeout = true;
        }
    }

    /**
     * Return the configured SO_TIMEOUT value
     * @return the configured SO_TIMEOUT value
     */
    public int getSoTimeoutMs(){
        return so_timeout_ms;
    }

    public synchronized void cancelAll(){
        shutDown();
        cancelled = true;
        adjustTaskGroupBlockingTimer();
    }

    /**
     * Assert whether this executor has been cancelled.
     * @return
     */
    public synchronized boolean isCancelled(){
        //An executor will be considered cancelled if its cancelAll() method has been cancel since the last time it was (re-)started.
        //It thus means its event-loop must have run at least once.
        return cancelled && !freshRestart;
    }

    /**
     * Initialize server sockets and start listening
     * @return
     */
    protected boolean initializeSockets() throws IOException {
        if(datagramSocket == null || datagramSocket.isClosed()){
            datagramSocket = new DatagramSocket();
            if(useCustomSoTimeout){
                datagramSocket.setSoTimeout(so_timeout_ms);
            }
        }
        return true;
    }


    /**
     * Receive next packet and return address from which it was sent
     * @return
     */
    protected DatagramPacket receiveNext() throws IOException {
        adjustTaskGroupBlockingTimer();
        byte buf[] = new byte[EchoDataParcel.MAX_SIZE];
        DatagramPacket ret = new DatagramPacket(buf, EchoDataParcel.MAX_SIZE);
        datagramSocket.receive(ret);
        return ret;
    }

    /**
     * Send data to remote peer
     * @param ndp The data to send
     *@param remoteAddress The IP address of the remote peer if known
     *@return true
     */
    protected boolean send(EchoDataParcel ndp, InetSocketAddress remoteAddress) throws IOException {
        byte data[] = ndp.toBytes();
        DatagramPacket request = new DatagramPacket(data, data.length, remoteAddress.getAddress(), remoteAddress.getPort());
        datagramSocket.send(request);
        return true;
    }

    /**
     * Cleanup sockets and buffers
     */
    protected void shutDown() {
        if(datagramSocket != null){
            datagramSocket.close();
        }
    }

    /**
     *
     * @return the local address.
     */
    public InetAddress getLocalAddress() {
        return datagramSocket.getLocalAddress();
    }

    /**
     * Temporarily adjust so_timeout on the active sockets/pipes.
     * This is done in a last-second scavenging sweep to collect delayed SD replies. It does not affect the global default so_timeout configuration.
     * The timeout is restored at the beginning of every SD batch.
     * @param timeout
     */
    protected void adjustSoTimeout(int timeout){
        if(datagramSocket != null){
            try {
                datagramSocket.setSoTimeout(timeout);
            } catch (SocketException e) {

            }
        }
    }

    /**
     * Return the current timestamp, for timing purposes.
     * @return
     */
    protected long getCurrentTimestamp(){
        return SystemClock.elapsedRealtime();
    }

    /**
     * Adjust internal timers used to detect when an executor is blocked on a batch of unresponsive queries.
     */
    private void adjustTaskGroupBlockingTimer(){
        taskGroupBlockingTimerTs = getCurrentTimestamp();
    }

    /**
     * Assert if this executor is taking too long waiting for a reply.
     * <p>
     *     This is used in attempts to avoid blocking.
     * </p>
     * @return
     */
    private boolean isBlocked(){
        return !isCancelled() && getCurrentTimestamp() > taskGroupBlockingTimerTs + (so_timeout_ms * 10);
    }

    /**
     * Test SD by sending a batch of probes to the remote peer.
     * @param sdQuery The result, describing characteristics of the connectivity between the client and remote peer.
     */

    private void runQuery(SDQuery sdQuery){
        if(useICMP){
            runPingQuery(sdQuery);
        }else{
            runUDPQuery(sdQuery);
        }
    }

    private void runPingQuery(SDQuery sdQuery){
        PingSuite.ping(sdQuery, random.nextLong());
    }

    private void runUDPQuery(SDQuery sdQuery) {
        ReachabilityTestResult reachabilityTestResult = null;
        try{
            InetAddress serviceAddress = sdQuery.getHostAddress();
            initializeSockets();
            setSoTimeoutMs(sdQuery.getSoTimeoutMs());
            int probeIdx = 0;
            long sessID = new Random(SystemClock.currentThreadTimeMillis()).nextLong();
            pendingAcks.clear();
            adjustSoTimeout(so_timeout_ms);
            while(/*!isCancelled() && */probeIdx < sdQuery.getMaxProbes()){
                probeIdx++;
                EchoDataParcel ndp = new EchoDataParcel(sessID, UUID.randomUUID().toString());
                pendingAcks.put(ndp.getDataStr(), getCurrentTimestamp());
                if(reachabilityTestResult != null){
                    sessID = reachabilityTestResult.getSessId();
                }
                send(ndp, new InetSocketAddress(serviceAddress, ECHO_SERV_PORT));
                reachabilityTestResult = waitForEcoResponse(sdQuery, reachabilityTestResult, serviceAddress, probeIdx);
            }
            //Do a last-time sweep to wait for delayed responses
            if(pendingAcks.keySet().size() > 0) {
                adjustSoTimeout(Math.min(100, datagramSocket.getSoTimeout() / pendingAcks.keySet().size()));
                for (int i = 0; i < pendingAcks.keySet().size(); i++) {
                    reachabilityTestResult = waitForEcoResponse(sdQuery, reachabilityTestResult, serviceAddress, probeIdx);
                }
            }
            if(reachabilityTestResult == null){
                reachabilityTestResult = ReachabilityTestResult.emptySD();
            }
            reachabilityTestResult.setCompleted(ReachabilityTestResult.DiscoveryMode.ECHO, sdQuery.getPacketSize(), sdQuery.getMaxTtl());
        } catch (IOException e){
            Logger.logE("IOException: " + e.getMessage());
        }
        sdQuery.postSDResult(reachabilityTestResult);
    }


    /**
     * Receive a SD reply.
     * This receives the next available SD reply and matches it against the corresponding queries in {@link #pendingAcks}
     * @param sdQuery The {@link SDQuery} we are executing.
     * @param reachabilityTestResult The incremental descriptor for SD state.
     * @param broadcastAddress The address we sent probes to.
     * @param probeIdx The index of the current query.
     * @return An updated SD descriptor.
     */
    private ReachabilityTestResult waitForEcoResponse(SDQuery sdQuery, ReachabilityTestResult reachabilityTestResult, InetAddress broadcastAddress, int probeIdx){
        DatagramPacket packet;
        try{
            packet = receiveNext();
            if(packet == null || packet.getSocketAddress() == null){
                return reachabilityTestResult;
            }
        }catch(SocketTimeoutException e){
            return reachabilityTestResult;
        } catch (IOException e) {
            Logger.logE(e.getMessage());
            return reachabilityTestResult;
        }
        InetSocketAddress sender = (InetSocketAddress) packet.getSocketAddress();
        EchoDataParcel ndp = EchoDataParcel.fromBytes(packet.getData(), packet.getLength());
        if(ndp != null && sender != null){
            String echoStr = ndp.getDataStr();
            if(echoStr != null && pendingAcks.containsKey(echoStr)){
                long rtt = getCurrentTimestamp() - pendingAcks.get(echoStr);
                pendingAcks.remove(echoStr);
                if(reachabilityTestResult == null){
                    reachabilityTestResult = new ReachabilityTestResult(ndp.getSessionCookie(), getLocalAddress(), sender.getAddress(), broadcastAddress, so_timeout_ms, sdQuery.getMaxProbes());
                }
                reachabilityTestResult = ReachabilityTestResult.update(reachabilityTestResult, ndp.getSessionCookie(), (int)rtt, sender.getAddress());
                sdQuery.updateSDProgress(probeIdx, sdQuery.getMaxProbes());
                return reachabilityTestResult;
            }
        }
        return reachabilityTestResult;
    }

    public void schedule(SDQuery sdQuery){
        GlobalExecutorService.submit(new Runnable() {
            @Override
            public void run() {
                runQuery(sdQuery);
            }
        }, "SDQueryExecutor#"+label);
    }
}