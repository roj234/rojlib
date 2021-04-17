/*
 * This file is a part of MI
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2021 Roj234
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package roj.net.udp;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketAddress;
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

    public UDPServer(SocketAddress addr, int bufferSize, MessageHandler consumer) throws SocketException {
        this.handler = consumer;
        this.socket = new DatagramSocket(addr);
        this.buffer = new byte[bufferSize];
    }

    public UDPServer(int port, int bufferSize, MessageHandler consumer) throws SocketException {
        this.handler = consumer;
        this.socket = new DatagramSocket(port);
        this.buffer = new byte[bufferSize];
    }

    @Override
    public void run() {
        final DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

        Thread self = Thread.currentThread();
        while (!self.isInterrupted()) {
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
}
