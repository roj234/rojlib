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
package roj.net.cross;

import roj.collect.RingBuffer;
import roj.config.JSONParser;
import roj.config.ParseException;
import roj.config.data.CList;
import roj.config.data.CMapping;
import roj.io.IOUtil;
import roj.io.NIOUtil;
import roj.net.NetworkUtil;
import roj.text.TextUtil;

import javax.swing.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.UnknownHostException;

/**
 * @author Roj233
 * @version 0.1
 * @since 2021/9/11 2:00
 */
public class AEGuiClient {
    static {
        NetworkUtil.MSSLoadClientRSAKey(new File("ae_client.key"));
    }

    public static void main(String[] args) throws IOException, ParseException {
        if(!NIOUtil.available()) {
            JOptionPane.showMessageDialog(null, "请使用Java8!");
            return;
        }

        if(args.length == 0) {
            args = new String[] { "asc.json" };
        }

        CMapping cfg = JSONParser.parse(IOUtil.readUTF(new FileInputStream(args[0])), JSONParser.LITERAL_KEY).asMap();

        String[] text = TextUtil.split(cfg.getString("url"), ':');
        if(text.length == 0) {
            JOptionPane.showMessageDialog(null, "服务器端口有误");
            return;
        }

        InetAddress host;
        try {
            host = text.length == 1 ? null : InetAddress.getByName(text[0]);
        } catch (UnknownHostException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, "未知的主机");
            return;
        }

        InetSocketAddress addr;
        try {
            addr = new InetSocketAddress(host, Integer.parseInt(text[text.length - 1]));
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(null, "服务器端口有误");
            return;
        }
        int port = cfg.getString("port").equals("random") ? Math.abs((int)System.nanoTime() % 60000) + 2000 : cfg.getInteger("port");
        do {
            try (ServerSocket local = new ServerSocket(port, 1)) {
                local.isBound();
                break;
            } catch (IOException ignored) {}
            port = (Math.abs((int)System.nanoTime() % 60000)) + 2000;
            System.out.println("检测到端口重复，自动重新分配了 " + port);
        } while (true);

        System.out.println("登录中......");

        LoggingStream.logger = new RingBuffer<>(1000);
        Util.out = new LoggingStream(true);
        System.setOut(Util.out);
        System.setErr(Util.out);

        Thread.currentThread().setName("Waiter");
        client = new AEClient(addr, cfg.getString("room"), cfg.getString("pass"));
        client.start();

        Util.registerShutdownHook(client);
        new GuiChat("AbyssalEye客户端", client);
        try {
            client.awaitLogin();
            CList list = cfg.getOrCreateList("ports");
            char[] ports = client.portMap;
            if (ports.length != list.size()) {
                CList list2 = new CList(ports.length);
                for (char p : ports) {
                    list2.add(p);
                }
                client.shutdown();
                System.out.println("端口映射不匹配: ");
                System.out.println("您的定义: " + list.toShortJSONb());
                System.out.println("服务端的定义: " + list2.toShortJSONb());
                return;
            }
            for (int i = 0; i < ports.length; i++) {
                ports[i] = (char) Math.max(list.get(i).asInteger(), 0);
            }
            client.notifyPortMapModified();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    static AEClient client;
}
