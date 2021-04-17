package roj.net.udp;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;

public class UDPClient {
    public static final InetAddress localHost;

    static {
        InetAddress localHost1;
        try {
            localHost1 = InetAddress.getLocalHost();
        } catch (UnknownHostException e) {
            e.printStackTrace();
            localHost1 = null;
        }
        localHost = localHost1;
    }

    final DatagramSocket socket;
    final int targetPort;

    public static void main(String[] args) throws IOException {
        System.out.println("本机数据传送测试");
        new UDPClient(2333, 6666).send("测试数据".getBytes(StandardCharsets.UTF_8));
    }

    public UDPClient(int selfPort, int targetPort) throws IOException {
        socket = new DatagramSocket(selfPort);
        this.targetPort = targetPort;
    }

    public void send(byte[] data) throws IOException {
        DatagramPacket packet = new DatagramPacket(data, data.length, localHost, targetPort);
        socket.send(packet);
    }

    @Override
    protected void finalize() {
        this.socket.close();
    }
}
