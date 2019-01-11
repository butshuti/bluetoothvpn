package edu.unt.nslab.butshuti.bluetoothvpn.utils;

import java.util.HashMap;
import java.util.Map;

public class ThreadLifeMonitor {
    MovingAverage ma = new MovingAverage(5);
    int seq = 0;
    long in = 0, out = 0;
    static Map<String, ThreadLifeMonitor> THREAD_AGE_MA = new HashMap<>();
    static Map<String, Long> THREAD_START_TS = new HashMap<>();
    static long uptime = 0;

    public static String getNextAgeDescr(String key, boolean reset, long pktsIn, long pktsOut){
        long ts = System.currentTimeMillis();
        if(!THREAD_AGE_MA.containsKey(key)){
            THREAD_AGE_MA.put(key, new ThreadLifeMonitor());
            THREAD_START_TS.put(key, ts);
        }
        float tsDiff = (ts - THREAD_START_TS.get(key))/1000.0f;
        ThreadLifeMonitor mm = THREAD_AGE_MA.get(key);
        uptime  = (ts - THREAD_START_TS.get(key))/1000;
        mm.ma.pushValue(tsDiff);
        int n = mm.seq;
        if(reset){
            THREAD_START_TS.put(key, ts);
            mm.seq++;
        }
        mm.in = Math.max(mm.in, pktsIn);
        mm.out = Math.max(mm.out, pktsOut);
        return "n=" + n + "; uptime=" + Math.round(100*uptime)/100 + "; avg_age=" + Math.round(100*(mm.ma.getAverage()))/100 + "s; IN=" + mm.in + " ; OUT=" + mm.out;
    }
}
