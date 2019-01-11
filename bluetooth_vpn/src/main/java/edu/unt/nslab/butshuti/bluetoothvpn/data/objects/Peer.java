package edu.unt.nslab.butshuti.bluetoothvpn.data.objects;

public final class Peer {

    public enum Status {
        DISCONNECTED, CONNECTING, CONNECTED
    }

    private String hostName, hostAddress;
    private Status status;

    public Peer(String hostName, String hostAddress, Status status){
        this.hostName = hostName;
        this.hostAddress = hostAddress;
        this.status = status;
    }

    public String getHostName(){
        return hostName != null ? hostName : "Name not available";
    }

    public String getHostAddress(){
        return hostAddress != null ? hostAddress : "N/A";
    }

    public Status getStatus(){
        return status;
    }

    public boolean isConnected(){
        return status.equals(Status.CONNECTED);
    }

    @Override
    public boolean equals(Object other){
        if(other instanceof Peer){
            Peer otherPeer = (Peer)other;
            if(hostName !=null && hostAddress != null){
                return hostName.equals(otherPeer.hostName) && hostAddress.equals(otherPeer.hostAddress);
            }else{
                return hostName == otherPeer.hostName && hostAddress == otherPeer.hostAddress;
            }
        }
        return false;
    }
}
