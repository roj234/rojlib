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
