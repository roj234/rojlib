/*
 * This file is a part of MoreItems
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

import roj.util.ByteWriter;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

/**
 * Your description here
 *
 * @author solo6975
 * @version 0.1
 * @since 2021/7/24 17:28
 */
public class DnsClientTest {
    public static void main(String[] args) throws IOException {
        ByteWriter w = new ByteWriter();
        w.writeShort(0).writeShort(0).writeShort(1).writeShort(0).writeShort(0).writeShort(0);
        DnsServer._w_domain_name(w, "www.baidu.com");
        w.writeShort(DnsServer.Q_ANY).writeShort(DnsServer.C_IN);

        DatagramPacket pkt = new DatagramPacket(w.list.list, w.list.pos());
        pkt.setPort(53);
        pkt.setAddress(InetAddress.getLoopbackAddress());
        DatagramSocket socket = new DatagramSocket(41, InetAddress.getLoopbackAddress());
        socket.send(pkt);


        pkt.setLength(w.list.list.length);
        socket.receive(pkt);
        w.list.pos(pkt.getLength());
        System.out.println(w.list);
    }
}
