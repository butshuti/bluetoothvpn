package edu.unt.nsllab.butshuti.bluetoothvpn.tunnel;

import android.os.ParcelFileDescriptor;
import android.os.SystemClock;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import edu.unt.nsllab.butshuti.bluetoothvpn.utils.Logger;

/**
 * Created by butshuti on 5/17/18.
 */

public class ControllerThread extends Thread {
    private ParcelFileDescriptor fd;
    private InterfaceController interfaceController;
    private int mtu;
    private final Queue<byte[]> inputQueue;
    private final Lock inputQueueLock = new ReentrantLock();
    private boolean active = false;
    private long in = 0, out = 0, loopCounter = 0, startTs;

    public ControllerThread(ParcelFileDescriptor fd, InterfaceController interfaceController, int mtu){
        this.fd = fd;
        this.interfaceController = interfaceController;
        this.mtu = mtu;
        inputQueue = new LinkedList<>();
        setDaemon(true);
        setName("VPN_fd_thread");
        setPriority(Thread.MAX_PRIORITY);
        startTs = SystemClock.uptimeMillis();
    }

    public boolean isActive(){
        return active && isAlive() && !isInterrupted();
    }

    public boolean deliver(byte pkt[]){
        if(pkt == null){
            return false;
        }
        boolean ret = false;
        try {
            if(inputQueueLock.tryLock(300, TimeUnit.MILLISECONDS)){
                ret = inputQueue.offer(pkt);
                inputQueueLock.unlock();
            }
        } catch (InterruptedException e) {
            Logger.logE(e.getMessage());
        }
        return ret;
    }

    public void terminate(){
        if(fd != null){
            try {
                fd.close();
            } catch (IOException e) {
                Logger.logE(e.getMessage());
            }
        }
        active = false;
        interrupt();
    }

    @Override
    public void run(){
        FileInputStream fis = new FileInputStream(fd.getFileDescriptor());
        FileOutputStream fos = new FileOutputStream(fd.getFileDescriptor());
        byte buf[] = new byte[mtu];
        Logger.logI("VPN_fd_thread starting... interface active()?/"+interfaceController.isActive());
        while (interfaceController.isActive()){
            active = true;
            loopCounter++;
            if( (in + out) % 5000 <= 2){
                //printStats();
            }
            try {
                int len = fis.read(buf);
                if(len > 0){
                    byte data[] = new byte[len];
                    System.arraycopy(buf, 0, data, 0, len);
                    interfaceController.send(data, false);
                    out++;
                }
            } catch (IOException e) {
                Logger.logE(e.getMessage());
                terminate();
            }
            byte pkt[] = null;
            if(inputQueueLock.tryLock()) {
                if (inputQueue.peek() != null) {
                    pkt = inputQueue.poll();
                }
                inputQueueLock.unlock();
            }
            if(pkt != null){
                try {
                    fos.write(pkt);
                    fos.flush();
                    in++;
                } catch (IOException e) {
                    Logger.logE(e.getMessage());
                    break;
                }
            }
        }
        active = false;
        Logger.logI("VPN_fd_thread terminating... ");
    }

    private void printStats(){
        long tsDiff = (SystemClock.uptimeMillis() - startTs)/1000;
        if(tsDiff == 0){
            return;
        }
        Logger.logE(String.format("freq: %d/s, IN: %d pkts/s, OUT: %d pkts/s, inQueueSize: %d", loopCounter/tsDiff, in/tsDiff, out/tsDiff, inputQueue.size()));
    }
}
