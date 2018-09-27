package edu.unt.nsllab.butshuti.bluetoothvpn.iptools.sdquery;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;

import edu.unt.nsllab.butshuti.bluetoothvpn.data.objects.ReachabilityTestResult;
import edu.unt.nsllab.butshuti.bluetoothvpn.utils.CMDUtils;
import edu.unt.nsllab.butshuti.bluetoothvpn.utils.Logger;

/**
 * Created by butshuti on 9/12/18.
 */

public class PingSuite {
    private final static String SYSTEM_PING_BINARY_PATH = "/system/bin/ping";
    private final static String IPv4 = Pattern.compile("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}").pattern();

    /**
     * Check if the ping utility binary is available on the system.
     * @return
     */
    public static boolean sysPingAvailable(){
        CMDUtils.CMDResult result = CMDUtils.exec(String.format("%s -c 1 -A 0.0.0.0", SYSTEM_PING_BINARY_PATH));
        return !result.failed();
    }

    /**
     * Execute a discovery query using the system's ping utility.
     * @param sdQuery the query results interface
     * @param sessID the session cookie for result aggregation
     */
    public static void ping(SDQuery sdQuery, long sessID){
        String cmd = String.format("%s -A -R -s %d -c %d -i %f %s",
                SYSTEM_PING_BINARY_PATH, sdQuery.getPacketSize()-8, sdQuery.getMaxProbes(), sdQuery.getSoTimeoutMs()/1000.0, sdQuery.getHostAddress().getHostAddress());
        ReachabilityTestResult result = null;
        Runtime runtime = Runtime.getRuntime();
        try {
            Process process = runtime.exec(cmd);
            BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String nextLine;
            InetAddress inetAddress = sdQuery.getHostAddress();
            result = new ReachabilityTestResult(sessID, inetAddress, inetAddress, inetAddress, sdQuery.getSoTimeoutMs(), sdQuery.getMaxProbes());
            try {
                int idx = 0;
                boolean routeRecord = false;
                List<String> route = new LinkedList<>();
                while ((nextLine = br.readLine()) != null){
                    nextLine = nextLine.trim();
                    if(idx > 0 && idx <= sdQuery.getMaxProbes()){
                        if(nextLine.startsWith("RR") || (routeRecord && nextLine.matches(IPv4))){
                            if(routeRecord){
                                route.add(nextLine);
                            }else{
                                route.add(nextLine.substring(2).trim());
                            }
                            routeRecord = true;
                            continue;
                        }
                        result = parsePingResult(result, nextLine, idx, idx == sdQuery.getMaxProbes());
                        if(result != null && !result.isEmpty()){
                            sdQuery.updateSDProgress(idx++, sdQuery.getMaxProbes());
                        }
                        result = ReachabilityTestResult.update(result, sessID, (int)result.getAvgRTT(), result.getServiceAddr());
                        if(!route.isEmpty()){
                            result.recordRoute(route);
                        }
                        routeRecord = false;
                        route.clear();
                    }
                    idx++;
                }
                result.setCompleted(ReachabilityTestResult.DiscoveryMode.ICMP, sdQuery.getPacketSize(), sdQuery.getMaxTtl());
                process.destroy();
            } catch (IOException e) {
                Logger.logE(e.getMessage());
                result.setFailed(ReachabilityTestResult.DiscoveryMode.ICMP, sdQuery.getPacketSize(), sdQuery.getMaxTtl());
            }
        } catch (IOException e) {
            Logger.logE(e.getMessage());
            result.setFailed(ReachabilityTestResult.DiscoveryMode.ICMP, sdQuery.getPacketSize(), sdQuery.getMaxTtl());
        }
        sdQuery.postSDResult(result);
    }

    private static ReachabilityTestResult parsePingResult(ReachabilityTestResult reachabilityTestResult, String str, int idx, boolean last){
        if(str != null) {
            int seq = parseSeq(str);
            String address = parseAddress(str);
            float rtt = parseRTT(str);
            if(seq > 0 && address != null && rtt > 0){
                try {
                    InetAddress ipAddr = InetAddress.getByName(address);
                    reachabilityTestResult = ReachabilityTestResult.update(reachabilityTestResult, reachabilityTestResult.getSessId(), (int)rtt, ipAddr);
                } catch (UnknownHostException e) {
                    Logger.logE(e.getMessage());
                }
            }
        }
        return reachabilityTestResult;
    }

    private static int parseSeq(String str){
        if(str.contains("icmp_seq=")){
            try{
                str = str.substring(str.indexOf("icmp_seq=") + 9);
                return Integer.parseInt(str.substring(0, str.indexOf(" ")));
            }catch (NumberFormatException e){
                Logger.logE(e.getMessage());
            }
        }
        return -1;
    }

    private static String parseAddress(String str){
        if(str.contains("from ")){
            String host = str.substring(str.indexOf("from ") + 5, str.indexOf(":"));
            String toks[] = host.split(" ");
            if(toks.length == 2){
                return toks[1];
            }else if(toks.length == 1){
                return toks[0];
            }
        }
        return null;
    }

    private static float parseRTT(String str){
        if(str.contains("time=")){
            try{
                str = str.substring(str.indexOf("time=") + 5);
                return Float.parseFloat(str.substring(0, str.indexOf(" ms")));
            }catch (NumberFormatException e){
                Logger.logE(e.getMessage());
            }
        }
        return -1;
    }
}
