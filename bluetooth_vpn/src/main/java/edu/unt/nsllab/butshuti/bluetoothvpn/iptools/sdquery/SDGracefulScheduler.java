package edu.unt.nsllab.butshuti.bluetoothvpn.iptools.sdquery;

/**
 * Created by butshuti on 9/10/18.
 *
 * This class implements a graceful scheduler for service discovery tasks.
 * It adjusts the number of probes and timeout parameters taking into account the nature of observed failures.
 * Considered conditions include network status and the frequency of discovery attempts.
 */

public class SDGracefulScheduler {

    /**
     * Connection states.
     * This wrapper specifies transition recommendations for state validation.
     * The actual state transitions are dictated by callbacks received from clients.
     */
    public enum ConnState {
        CONN_COMPLETE(Integer.MAX_VALUE, "Network connected"), CONN_START(-5, "Configuring network"),
        CONN_WAIT(10, "Waiting for network info"), CONN_EXPIRED(-1, "Network request expired");
        private int maxAge;
        private String expl;

        ConnState(int maxAge, String expl){
            this.maxAge = maxAge;
            this.expl = expl;
        }

        ConnState incr(){
            if(--maxAge > 0){
                return this;
            }
            return CONN_EXPIRED;
        }

        public String expl(){
            return expl;
        }

        public ConnState debug(String msg){
            expl = msg;
            return this;
        }
    }

    private static final int INITIAL_BACKOFF_REDUCTION = 10;
    private static final int MIN_PROBES = 5;
    private int configuredSoTimeoutMs, curSoTimeoutMs;
    private int configuredMaxProbes, maxProbes;
    private float lastSuccessRate;
    private ConnState connState;
    private boolean incrTimeout = false, incrNumProbes = false;


    /**
     * Construct an initial state for the scheduler.
     * @param soTimeout the default configured timeout, or a negative value if none
     * @param numProbes  the default configured MAX number of probes, or a negative value if none
     * @param connState the current connection state
     */
    public SDGracefulScheduler(int soTimeout, int numProbes, ConnState connState){
        configuredMaxProbes = maxProbes = numProbes;
        configuredSoTimeoutMs = curSoTimeoutMs = soTimeout;
        lastSuccessRate = 0;
        this.connState = connState;
    }

    /**
     * Get the last recorded connection state
     * @return the internal state
     */
    public ConnState getLastConnState(){
        return connState;
    }

    /**
     * Update the connection state. This method is typically called when a state change is observed from a client.
     * @param connState
     */
    private void setConnState(ConnState connState){
        this.connState = connState;
    }

    /**
     * Update the connection state. This method is typically called when a state change is observed from a client.
     * @param connState
     * @param debugMsg  A debug message for the connection state
     */
    public void setConnState(ConnState connState, String debugMsg){
        setConnState(connState.debug(debugMsg));
    }

    /**
     * Re-validate the internal connection status.
     * Invalid statuses transition to the expired state. This has the effect of cancelling subsequent probe requests.
     * @return  an update to the current state
     */
    private ConnState incr(){
        if(incrNumProbes && incrTimeout){
            incrNumProbes = false;
            incrTimeout = false;
            connState = connState.incr();
        }
        return connState;
    }

    /**
     * Validate timeout re-adjustments in the current state
     * @return an update to the current state
     */
    private ConnState incrTimeout(){
        incrTimeout = true;
        return incr();
    }

    /**
     * Validate re-adjustments of the number of probes in the current state
     * @return an update to the current state
     */
    private ConnState incrNumProbes(){
        incrNumProbes = true;
        return incr();
    }

    /**
     * Get the configured/reference SO_TIMEOUT value
     * @return the configured timeout value
     */
    public int getConfiguredSoTimeoutMs(){
        return configuredSoTimeoutMs;
    }

    /**
     * Get the current SO_TIMEOUT value.
     * This value is continuously re-adjusted internally for graceful retries.
     * @return the timeout value in milliseconds
     */
    public int getCurSoTimeoutMs(){
        if(curSoTimeoutMs > 0){
            if(connState.equals(ConnState.CONN_EXPIRED)){
                return 0;
            }else if(!connState.equals(ConnState.CONN_COMPLETE)){
                curSoTimeoutMs = Math.max(MIN_PROBES, configuredSoTimeoutMs / INITIAL_BACKOFF_REDUCTION);
            }else{
                incrTimeout();
                curSoTimeoutMs = (curSoTimeoutMs + INITIAL_BACKOFF_REDUCTION) % configuredSoTimeoutMs;
            }
        }
        return curSoTimeoutMs;
    }

    /**
     * (Re-)set the reference SO_TIMEOUT value
     * @param timeoutMs
     */
    public void setConfiguredSoTimeoutMs(int timeoutMs){
        configuredSoTimeoutMs = curSoTimeoutMs = timeoutMs;
    }

    /**
     * Get the number of probes for the next retry.
     * This value is continuously re-adjusted internally for graceful retries.
     * @return the number of probes for the next retry, or 0 if no more retries should be attempted.
     */
    public int getNumProbes(){
        if(connState.equals(ConnState.CONN_EXPIRED)){
            return 0;
        }else if(!connState.equals(ConnState.CONN_COMPLETE)){
            maxProbes = Math.max(MIN_PROBES, configuredMaxProbes / INITIAL_BACKOFF_REDUCTION);
        }else{
            incrNumProbes();
            maxProbes = (maxProbes + INITIAL_BACKOFF_REDUCTION) % configuredMaxProbes;
        }
        return maxProbes;
    }

    /**
     * (Re-)set the reference MAX value for the number of probes
     * @param numProbes
     */
    public void setConfiguredMaxProbes(int numProbes){
        configuredMaxProbes = maxProbes = numProbes;
    }

}
