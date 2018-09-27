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

public class VPNFDController {
    private ParcelFileDescriptor fd;
    private InterfaceController interfaceController;
    private int mtu;
    private final Queue<byte[]> inputQueue;
    private final Lock inputQueueLock = new ReentrantLock();
    private boolean active = false;
    private long in = 0, out = 0, loopCounter = 0, startTs;
    private Thread readerThread, writerThread;

    public VPNFDController(ParcelFileDescriptor fd, InterfaceController interfaceController, int mtu){
        this.fd = fd;
        this.interfaceController = interfaceController;
        this.mtu = mtu;
        inputQueue = new LinkedList<>();
        readerThread = createReader(new FileInputStream(fd.getFileDescriptor()));
        readerThread.setDaemon(true);
        readerThread.setName("VPN_fd_thread::reader");
        readerThread.setPriority(Thread.MAX_PRIORITY);
        writerThread = createWriter(new FileOutputStream(fd.getFileDescriptor()));
        writerThread.setDaemon(true);
        writerThread.setName("VPN_fd_thread::writer");
        writerThread.setPriority(Thread.MAX_PRIORITY);
        startTs = SystemClock.uptimeMillis();
    }

    public boolean isActive(){
        return active
                && readerThread.isAlive() && !readerThread.isInterrupted()
                && writerThread.isAlive() && !writerThread.isInterrupted();
    }

    public void start(){
        readerThread.start();
        writerThread.start();
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
        readerThread.interrupt();
        writerThread.interrupt();
    }

    private Thread createWriter(FileOutputStream fos){
        return new Thread(){
            @Override
            public void run(){
                byte buf[] = new byte[mtu];
                Logger.logI("VPN_fd_thread starting... interface active()?/"+interfaceController.isActive());
                while (interfaceController.isActive()){
                    active = true;
                    loopCounter++;
                    if( (in + out) % 5000 <= 2){
                        //printStats();
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
        };
    }

    private Thread createReader(FileInputStream fis){
        return new Thread(){
            @Override
            public void run(){
                byte buf[] = new byte[mtu];
                Logger.logI("VPN_fd_thread: reader starting... interface active()?/"+interfaceController.isActive());
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
                }
                active = false;
                Logger.logI("VPN_fd_thread: reader terminating... ");
            }
        };
    }

    private void printStats(){
        long tsDiff = (SystemClock.uptimeMillis() - startTs)/1000;
        if(tsDiff == 0){
            return;
        }
        Logger.logE(String.format("freq: %d/s, IN: %d pkts/s, OUT: %d pkts/s, inQueueSize: %d", loopCounter/tsDiff, in/tsDiff, out/tsDiff, inputQueue.size()));
    }

    public boolean isAlive() {
        return readerThread.isAlive() && writerThread.isAlive();
    }

    public void join() throws InterruptedException {
        readerThread.join();
        writerThread.join();
    }
}
