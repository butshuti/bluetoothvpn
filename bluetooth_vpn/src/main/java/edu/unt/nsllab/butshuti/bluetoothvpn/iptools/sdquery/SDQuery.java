package edu.unt.nsllab.butshuti.bluetoothvpn.iptools.sdquery;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import java.net.InetAddress;

import edu.unt.nsllab.butshuti.bluetoothvpn.data.objects.ReachabilityTestResult;

import static edu.unt.nsllab.butshuti.bluetoothvpn.iptools.sdquery.SDQuery.SDQueryHandler.DEFAULT_NUM_HOPS;
import static edu.unt.nsllab.butshuti.bluetoothvpn.iptools.sdquery.SDQuery.SDQueryHandler.DEFAULT_PACKET_SIZE;
import static edu.unt.nsllab.butshuti.bluetoothvpn.iptools.sdquery.SDQuery.SDQueryHandler.MSG_PROGRESS;
import static edu.unt.nsllab.butshuti.bluetoothvpn.iptools.sdquery.SDQuery.SDQueryHandler.MSG_RESULT;


/**
 * @author butshuti
 *
 * An {@link SDQuery} instance represents a batch of probes in the same SD request.
 * Instances of this classes hold a reference to an {@link SDQueryHandler} for posting progress
 * and results to the main UI thread through a {@link SDProgressListener}.
 */
public class SDQuery {

    /**
     * Handler for posting SD progress and results to the UI through a {@link SDProgressListener}.
     */
    static class SDQueryHandler extends Handler {
        static final int MSG_PROGRESS = 1;
        static final int MSG_RESULT = 2;
        static final int DEFAULT_NUM_HOPS = 64;
        static final int DEFAULT_PACKET_SIZE = 128;

        private SDProgressListener listener;

        /**
         * Create a new handler instance. The caller must have prepared the main looper beforehand.
         * @param looper {@see {@link Handler#Handler(Looper)}}
         * @param listener a progress listener for updating the UI
         */
        SDQueryHandler(Looper looper, SDProgressListener listener){
            super(looper);
            this.listener = listener;
        }
        @Override
        public void handleMessage(Message msg) {
            if(listener == null){
                return;
            }
            switch (msg.what){
                case MSG_PROGRESS:
                    listener.updateProgress(msg.arg1, msg.arg2);
                    break;
                case MSG_RESULT:
                    listener.postResult((ReachabilityTestResult) msg.obj, msg.arg1);
                    break;
            }
        }
    }

    private SDQueryHandler sdQueryHandler; //The UI handler
    private int taskIndex; //The task index (for debugging purposes only)
    private int maxProbes; //The number of probes to send
    private int so_timeout_ms; //The timeout when receiving/waiting for replies

    private int max_ttl; //The maximum number of hops
    private int packet_size; //The maximum packet size
    private InetAddress hostAddress; //The destination address

    /**
     * Create a new instance of {@link SDQuery}
     * @param looper {@see {@link SDQueryHandler#SDQueryHandler(Looper, SDProgressListener)}}.
     * @param progressListerner {@see {@link SDQueryHandler#SDQueryHandler(Looper, SDProgressListener)}}
     * @param dst The destination host address.
     * @param taskIndex The task index (for debugging purposes).
     * @param maxProbes The number of probes to send.
     */
    public SDQuery(Looper looper, SDProgressListener progressListerner, InetAddress dst, int taskIndex, int maxProbes){
        sdQueryHandler = new SDQueryHandler(looper, progressListerner);
        this.taskIndex = taskIndex;
        this.maxProbes = maxProbes;
        this.hostAddress = dst;
        packet_size = DEFAULT_PACKET_SIZE;
        max_ttl = DEFAULT_NUM_HOPS;
    }

    public SDQuery setMaxProbes(int numProbes){
        maxProbes = numProbes;
        return this;
    }

    public SDQuery setSoTimeoutMs(int timeout){
        if(timeout > 0){
            so_timeout_ms = timeout;
        }
        return this;
    }

    public SDQuery setMax_ttl(int max_ttl) {
        this.max_ttl = max_ttl;
        return this;
    }

    public SDQuery setPacket_size(int packet_size) {
        this.packet_size = packet_size;
        return this;
    }

    public int getMaxProbes(){
        return maxProbes;
    }
    public int getSoTimeoutMs(){
        return so_timeout_ms;
    }
    public InetAddress getHostAddress(){
        return hostAddress;
    }
    public int getMaxTtl() {
        return max_ttl;
    }

    public int getPacketSize() {
        return packet_size;
    }
    /**
     * Update state when a SD reply is received.
     * @param progress The new progress, as a tuple (currentIndex, maxProbes).
     */
    public void updateSDProgress(Integer...progress){
        if(progress.length != 2){
            return;
        }
        Message message = sdQueryHandler.obtainMessage(MSG_PROGRESS, progress[0], progress[1]);
        message.sendToTarget();
    }

    /**
     * Post the final result for the SD session represented by this instance.
     * @param result A descriptor for the connectivity state as estimated from the batch of probes exchanged with the remote peer.
     */
    public void postSDResult(ReachabilityTestResult result) {
        Message message = sdQueryHandler.obtainMessage(MSG_RESULT, taskIndex, taskIndex, result);
        message.sendToTarget();
    }

    /**
     * An interface for posting updates and results from a background thread, typically back to the UI thread/
     * UI views that visualize SD details should implement this interface, then pass it when creating SD query instamces.
     */
    public interface SDProgressListener{

        /**
         * Update state when a SD reply is received.
         * @param progress The new progress, as a tuple (currentIndex, maxProbes).
         */
        void updateProgress(Integer... progress);

        /**
         * Post results to the handler.
         * @param result SD parameters and stats as estimated from the batch of probes exchanged during this SD session.
         * @param probeIdx The probe index.
         */
        void postResult(ReachabilityTestResult result, int probeIdx);
    }
}