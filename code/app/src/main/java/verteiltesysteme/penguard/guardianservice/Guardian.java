package verteiltesysteme.penguard.guardianservice;


import android.util.Log;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class Guardian {

    private InetAddress address;
    private String ip;

    private int port;

    private String name;

    private long lastSeenTimestamp; //TODO #62

    Guardian() {
        name = null;
        ip = null;
        address = null;
        port = 0;
    }

    Guardian(String name, String ip, int port) {
        this.name = name;
        this.ip = ip;
        this.port = port;
    }

    Guardian(String name, InetAddress address, int port) {
        this.name = name;
        this.address = address;
        this.port = port;
        this.ip = address.getHostName();
    }

    void updateTime() {
        lastSeenTimestamp = System.currentTimeMillis();
    }

    void setIp(String ip) {
        this.ip = ip;
        try {
            this.address = InetAddress.getByName(ip);
        } catch (UnknownHostException e) {
            debug("Unknown Host " + ip);
        }
    }

    void setAddress(InetAddress address) {
        this.address = address;
        this.ip = address.getHostName();
    }

    public InetAddress getAddress() {
        if (address != null) {
            return address;
        }
        try {
            address = InetAddress.getByName(ip);
            return address;
        } catch (UnknownHostException e) {
            debug("Invalid address: " + e.getMessage());
        }
        return null;
    }

    public long getTimeStamp(){
        return  this.lastSeenTimestamp;
    }

    public String getIp() {
        return ip;
    }

    public int getPort() {
        return port;
    }

    void setPort(int port) {
        this.port = port;
    }

    public String getName() {
        return name;
    }

    void setName(String name) {
        this.name = name;
    }

    private void debug(String msg) {
        Log.d("Guardian", msg);
    }
}
