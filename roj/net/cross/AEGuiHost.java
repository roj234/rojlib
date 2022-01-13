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

import roj.collect.MyHashMap;
import roj.collect.RingBuffer;
import roj.config.JSONParser;
import roj.config.ParseException;
import roj.config.data.CList;
import roj.config.data.CMapping;
import roj.io.IOUtil;
import roj.io.NIOUtil;
import roj.net.http.Code;
import roj.net.http.HttpServer;
import roj.net.http.serv.Reply;
import roj.net.http.serv.StringResponse;
import roj.text.TextUtil;
import roj.util.FastLocalThread;

import javax.swing.*;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

/**
 * @author Roj233
 * @version 0.1
 * @since 2021/9/11 2:00
 */
public class AEGuiHost {
    public static void main(String[] args) throws IOException, ParseException {
        if(!NIOUtil.available()) {
            JOptionPane.showMessageDialog(null, "NIO Native Helper is unavailable!\n请使用Java8!");
            return;
        }

        String serv = null;
        CMapping cfg = null;
        int webPort = -1;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-cfg":
                    cfg = JSONParser.parse(IOUtil.readUTF(new FileInputStream(args[++i])), JSONParser.LITERAL_KEY).asMap();
                    break;
                case "-server":
                    serv = args[++i];
                    break;
                case "-webport":
                    webPort = Integer.parseInt(args[++i]);
                    break;
            }
        }

        String[] text = TextUtil.split(serv, ':');

        InetAddress host;
        try {
            host = text.length == 1 ? null : InetAddress.getByName(text[0]);
        } catch (UnknownHostException e) {
            System.out.println("未知的主机");
            return;
        }

        InetSocketAddress addr;
        try {
            addr = new InetSocketAddress(host, Integer.parseInt(text[text.length - 1]));
        } catch (NumberFormatException e) {
            System.out.println("无效的监听端口");
            return;
        }

        client = new AEHost(addr, cfg.getString("room"), cfg.getString("pass"));
        CList ports = cfg.getOrCreateList("ports");
        char[] chars = new char[ports.size()];
        for (int i = 0; i < chars.length; i++) {
            chars[i] = (char) ports.get(i).asInteger();
        }
        client.setPortMap(chars);
        client.setDaemon(false);
        client.start();

        if(webPort != -1) {
            try {
                Thread t = new FastLocalThread(runServer(webPort));
                t.setDaemon(true);
                t.setName("AEServer Http");
                t.start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        System.out.println("登录中......");
        LoggingStream.logger = new RingBuffer<>(1000);
        Util.out = new LoggingStream(cfg.getBool("log"));
        System.setOut(Util.out);
        System.setErr(Util.out);

        Util.registerShutdownHook(client);
        new GuiChat("AbyssalEye客户端服务器", client);
    }

    static MyHashMap<String, String> tmp = new MyHashMap<>();
    private static String res(String name) throws IOException {
        String v = tmp.get(name);
        if(v == null)
            tmp.put(name, v = IOUtil.readUTF("META-INF/ae/html/" + name));
        return v;
    }

    private static HttpServer runServer(int port) throws IOException {
        return new HttpServer(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 64, (socket, request) -> {
            switch (request.path()) {
                case "/bundle.min.css":
                    return new Reply(Code.OK, new StringResponse(res("bundle.min.css"), "text/css"));
                case "/bundle.min.js":
                    return new Reply(Code.OK, new StringResponse(res("bundle.min.js"), "text/javascript"));
                case "/":
                    return new Reply(Code.OK, new StringResponse(res("client_owner.html"), "text/html"));
                case "/user_list":
                    CList lx = new CList();
                    for (InetSocketAddress w : client.clients.values()) {
                        lx.add(w.toString());
                    }
                    return new Reply(Code.OK, new StringResponse(lx.toJSON(), "application/json"));
                case "/kick_user":
                    int count = 0;
                    String[] arr = request.postFields().get("users").split(",");
                    int[] arrs = new int[arr.length];
                    for (int i = 0; i < arr.length; i++) {
                        arrs[i] = Integer.parseInt(arr[i]);
                    }
                    client.kickSome(arrs);
                    return new Reply(Code.OK, new StringResponse("{\"count\":" + arr.length + "}", "application/json"));
            }
            return null;
        });
    }

    static AEHost client;
}
