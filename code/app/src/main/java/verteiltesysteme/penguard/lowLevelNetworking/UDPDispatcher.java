package verteiltesysteme.penguard.lowLevelNetworking;

import android.os.AsyncTask;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class UDPDispatcher extends AsyncTask<Void, Void, Boolean> {
    private int port;
    private String ip;
    private String message;
    private DispatcherCallback onPostAction;

    public UDPDispatcher(String message, String ip, int port, DispatcherCallback onPostAction) {
        this.port = port;
        this.ip = ip;
        this.message = message;
        this.onPostAction = onPostAction;
    }

    @Override
    public Boolean doInBackground(Void... params){
        try {
            byte[] outData = message.getBytes();
            InetAddress receiverIP = InetAddress.getByName(ip);
            DatagramSocket socket = new DatagramSocket();
            DatagramPacket outPacket = new DatagramPacket(outData, outData.length, receiverIP, port);
            socket.send(outPacket);
            socket.close();
        }
        catch(java.io.IOException e){
            return false;
        }
        return true;
    }

    @Override
    protected void onPostExecute(Boolean success){
        onPostAction.setSuccess(success);
        onPostAction.start();
    }
}
