package roj.net.udp;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;

public class UDPServer implements Runnable {
    private final DatagramSocket socket;
    private final byte[] buffer;
    private final MessageHandler handler;

    public static void main(String[] args) throws SocketException {
        System.out.println("本机数据传送测试-Server");
        new UDPServer(6666, 4096, new MessageHandler() {
            @Nullable
            @Override
            public byte[] onMessage(byte[] list, int length, int fromPort) {
                System.out.println(fromPort + "接收到的数据: " + new String(list));

                return null;
            }
        }).run();
    }

    public UDPServer(int port, int bufferSize, MessageHandler consumer) throws SocketException {
        this.handler = consumer;
        this.socket = new DatagramSocket(port);
        this.buffer = new byte[bufferSize];
    }

    @Override
    public void run() {
        final DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

        Thread thread = Thread.currentThread();

        while (!thread.isInterrupted()) {
            try {
                this.socket.receive(packet);
            } catch (IOException e) {
                e.printStackTrace();
            }
            byte[] reply = handler.onMessage(buffer, packet.getLength(), packet.getPort());
            if (reply != null) {
                packet.setData(reply);
                try {
                    this.socket.send(packet);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                packet.setData(buffer);
            }
        }

        this.socket.close();
    }

    @Override
    protected void finalize() {
        this.socket.close();
    }
}
