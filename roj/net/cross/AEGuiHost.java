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

import roj.collect.IntMap;
import roj.collect.MyHashMap;
import roj.collect.RingBuffer;
import roj.config.JSONParser;
import roj.config.ParseException;
import roj.config.data.CList;
import roj.config.data.CMapping;
import roj.io.IOUtil;
import roj.io.NIOUtil;
import roj.net.cross.AEHost.Client;
import roj.net.http.HttpServer;
import roj.net.http.serv.StringResponse;
import roj.net.misc.Pipe;
import roj.text.LoggingStream;
import roj.text.TextUtil;

import javax.swing.*;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

/**
 * @author Roj233
 * @since 2021/9/11 2:00
 */
public class AEGuiHost {
    public static void main(String[] args) throws IOException, ParseException {
        if(!NIOUtil.available()) {
            JOptionPane.showMessageDialog(null, "NIO Native Helper is unavailable!\n请使用Java8!");
            return;
        }

        String serv = null, cfgFile = "host.json";
        int webPort = -1;
        boolean ui = true;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-cfg":
                    cfgFile = args[++i];
                    break;
                case "-server":
                    serv = args[++i];
                    break;
                case "-webport":
                    webPort = Integer.parseInt(args[++i]);
                    break;
                case "-nogui":
                    ui = false;
                    break;
            }
        }
        CMapping cfg = JSONParser.parse(IOUtil.readUTF(new FileInputStream(cfgFile)), JSONParser.LITERAL_KEY).asMap();

        String[] text = TextUtil.split1(serv, ':');

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
        client.motd = cfg.getString("motd");
        client.setDaemon(false);
        client.start();

        if(webPort != -1) {
            try {
                runServer(webPort).start();
            } catch (BindException e) {
                System.err.println("HTTP端口(" + webPort + ")已被占用: " + e.getMessage());
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
        if (ui) new GuiChat("AbyssalEye房主", client);
    }

    static MyHashMap<String, String> tmp = new MyHashMap<>();
    private static String res(String name) throws IOException {
        String v = tmp.get(name);
        if(v == null)
            tmp.put(name, v = IOUtil.readUTF("META-INF/ae/html/" + name));
        return v;
    }

    private static HttpServer runServer(int port) throws IOException {
        return new HttpServer(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 64, (request, requestHandler) -> {
            switch (request.path()) {
                case "/bundle.min.css":
                    return new StringResponse(res("bundle.min.css"), "text/css");
                case "/bundle.min.js":
                    return new StringResponse(res("bundle.min.js"), "text/javascript");
                case "/":
                    return new StringResponse(res("client_owner.html"), "text/html");
                case "/user_list":
                    CList lx = new CList();
                    for (IntMap.Entry<Client> entry : client.clients.entrySet()) {
                        CMapping map = new CMapping();
                        map.put("id", entry.getKey());
                        map.put("ip", entry.getValue().addr);
                        map.put("time", entry.getValue().connect);
                        CList pipes = map.getOrCreateList("pipes");
                        for (Pipe pipe : client.socketsById.values()) {
                            SpAttach att = (SpAttach) pipe.att;
                            if (att.clientId == entry.getKey()) {
                                CMapping map1 = new CMapping();
                                map1.put("up", pipe.downloaded);
                                map1.put("down", pipe.uploaded);
                                map1.put("idle", pipe.idleTime);
                                map1.put("id", att.channelId);
                                map1.put("port", client.portMap[att.portId]);
                                pipes.add(map1);
                            }
                        }
                        lx.add(map);
                    }
                    return new StringResponse(lx.toShortJSONb(), "application/json");
                case "/kick_user":
                    int count = 0;
                    String[] arr = request.payloadFields().get("users").split(",");
                    int[] arrs = new int[arr.length];
                    for (int i = 0; i < arr.length; i++) {
                        arrs[i] = Integer.parseInt(arr[i]);
                    }
                    client.kickSome(arrs);
                    return new StringResponse("{\"count\":" + arr.length + "}", "application/json");
            }
            return null;
        });
    }

    static AEHost client;
}
