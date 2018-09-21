package edu.unt.nsllab.butshuti.bluetoothvpn.data.objects;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ServiceStatusWrapper {
    private long pktCount;
    private long uptime;
    private String interfaceAddress;
    private String hostName;
    private boolean serviceActive;

    private Set<Peer> servers;
    private Set<Peer> clients;
    private Peer.Status status;

    public ServiceStatusWrapper(){
        servers = new HashSet<>();
        clients = new HashSet<>();
        pktCount = 0;
        uptime = 0;
        interfaceAddress = InetAddress.getLoopbackAddress().getHostAddress();
        hostName = InetAddress.getLoopbackAddress().getHostName();
        status = Peer.Status.DISCONNECTED;
        serviceActive = false;
    }

    public void setStatus(Peer.Status st){
        status = st;
    }

    public Peer describe(){
        return new Peer(hostName, interfaceAddress != null ? interfaceAddress : "Not available", status);
    }

    public List<Peer> getClients(){
        List<Peer> ret = new ArrayList<>();
        ret.addAll(clients);
        return ret;
    }

    public List<Peer> getServers(){
        List<Peer> ret = new ArrayList<>();
        ret.addAll(servers);
        return ret;
    }

    public List<Peer> getPeers(){
        List<Peer> ret = new ArrayList<>();
        ret.addAll(clients);
        ret.addAll(servers);
        return ret;
    }

    public long getPktCount(){
        return pktCount;
    }


    public ServiceStatusWrapper setServiceActive(boolean active){
        serviceActive = active;
        return this;
    }

    public void setPktCount(long pktCount) {
        this.pktCount = pktCount;
    }

    public void setUptime(long uptime) {
        this.uptime = uptime;
    }

    public void setInterfaceAddress(String interfaceAddress) {
        this.interfaceAddress = interfaceAddress;
    }

    public void setHostName(String hostName) {
        this.hostName = hostName;
    }

    public void setServers(Set<Peer> servers) {
        this.servers = servers;
    }

    public void setClients(Set<Peer> clients) {
        if(this.clients != null){
            this.clients.addAll(clients);
        }else{
            this.clients = clients;
        }
    }
}
