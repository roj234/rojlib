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
package roj.net.cross.server;

import roj.collect.MyHashMap;
import roj.collect.RingBuffer;
import roj.config.data.CList;
import roj.config.data.CMapping;
import roj.io.IOUtil;
import roj.io.NIOUtil;
import roj.net.NetworkUtil;
import roj.net.cross.Util;
import roj.net.http.Code;
import roj.net.http.HttpServer;
import roj.net.http.serv.Reply;
import roj.net.http.serv.StringResponse;
import roj.net.mss.JPubKey;
import roj.net.mss.MSSServerEngineFactory;
import roj.net.mss.PreSharedPubKey;
import roj.text.CharList;
import roj.text.LoggingStream;
import roj.text.TextUtil;
import roj.util.FastLocalThread;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.util.Map;

/**
 * AbyssalEye Server GUI
 *
 * @author Roj233
 * @version 40
 * @since 2021/9/11 12:49
 */
public class AEGuiServer {
    static AEServer server;

    public static void main(String[] args) {
        if(!NIOUtil.available()) {
            JOptionPane.showMessageDialog(null, "NIO Native Helper is unavailable!\n请使用Java8!");
            return;
        }

        byte[] keyPass = null;
        String port = null, motd = "任务：拯救世界(1/1)";
        int webPort = -1, maxUsers = 100;
        boolean log = false, unshared = false;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-consolelog":
                    log = true;
                    break;
                case "-maxconn":
                    maxUsers = Integer.parseInt(args[++i]);
                    break;
                case "-port":
                    port = args[++i];
                    break;
                case "-webport":
                    webPort = Integer.parseInt(args[++i]);
                    break;
                case "-keypass":
                    keyPass = args[++i].getBytes(StandardCharsets.UTF_8);
                    break;
                case "-unshared":
                    unshared = true;
                    break;
                case "-motd":
                    motd = args[++i];
            }
        }

        if (keyPass == null) {
            JOptionPane.showMessageDialog(null,
                    "自40版本起不再支持非加密模式,\n" +
                    "请设定一个密码以生成加密的key");
            return;
        }

        String[] text = TextUtil.split(port, ':');

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

        if(maxUsers <= 1) {
            System.out.println("无效的最大连接数");
            return;
        }

        KeyPair pair = NetworkUtil.genAndStoreRSAKey(new File("ae_server.key"),
                                                     new File("ae_client.key"), keyPass);
        if (pair == null) return;

        try {
            MSSServerEngineFactory factory = new MSSServerEngineFactory(unshared ?
                       JPubKey.JAVARSA : new PreSharedPubKey(pair.getPublic()),
                       pair.getPublic(), pair.getPrivate());
            server = new AEServer(addr, maxUsers, factory);
        } catch (IOException | GeneralSecurityException e) {
            System.out.println("Invalid certificate / IO Error");
            e.printStackTrace();
            return;
        }
        server.setMOTD(motd);

        Thread tServer = new FastLocalThread(server);
        tServer.setName("AEServer Acceptor");
        tServer.start();

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

        LoggingStream.logger = new RingBuffer<>(1000);
        PrintStream out = Util.out = new LoggingStream(log);
        System.setOut(Util.out);
        System.setErr(Util.out);
        out.println("    ___    __                          ________         ");
        out.println("   /   |  / /_  __  ________________ _/ / ____/_  _____ ");
        out.println("  / /| | / __ \\/ / / / ___/ ___/ __ `/ / __/ / / / / _ \\");
        out.println(" / ___ |/ /_/ / /_/ (__  |__  ) /_/ / / /___/ /_/ /  __/");
        out.println("/_/  |_/_.___/\\__, /____/____/\\__,_/_/_____/\\__, /\\___/ ");
        out.println("             /____/                        /____/      ");
        out.println(" —— Version " + Util.PROTOCOL_VERSION);
        out.println();
        out.println("服务器已启动");

        Util.registerShutdownHook(server);
    }

    static MyHashMap<String, String> tmp = new MyHashMap<>(4);
    private static String res(String name) throws IOException {
        String v = tmp.get(name);
        if(v == null)
            tmp.put(name, v = IOUtil.readUTF("META-INF/ae/html/" + name));
        return v;
    }

    private static HttpServer runServer(int port) throws IOException {
        Reply DONE = new Reply(Code.OK, new StringResponse("{\"o\":1}", "application/json"));

        return new HttpServer(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 64,
                              (socket, request, requestHandler) -> {
            switch (request.path()) {
                case "/bundle.min.css":
                    return new Reply(Code.OK, new StringResponse(res("bundle.min.css"), "text/css"));
                case "/bundle.min.js":
                    return new Reply(Code.OK, new StringResponse(res("bundle.min.js"), "text/javascript"));
                case "/":
                    return new Reply(Code.OK, new StringResponse(res("server.html"), "text/html"));
                case "/api_g":
                    switch (request.getFields().getOrDefault("o", "")) {
                        case "main":
                            CMapping json = new CMapping();
                            String logs;
                            CharList cl = new CharList();
                            for (String s : LoggingStream.logger) {
                                cl.append(s).append("\r\n");
                            }
                            LoggingStream.logger.clear();
                            logs = cl.toString();
                            json.put("logs", logs);
                            if(server != null) {
                                CList rooms = new CList();
                                for (Room room : server.rooms.values()) {
                                    rooms.add(room.serialize());
                                }
                                json.put("rooms", rooms);
                            }
                            return new Reply(Code.OK, new StringResponse(json.toShortJSONb(), "application/json"));
                        case "users":
                            Room room = server.rooms.get(TextUtil.unescapeBytes(request.getFields().get("r")));
                            if(room == null) {
                                return new Reply(Code.OK, new StringResponse("\"房间不存在\"", "application/json"));
                            }
                            CList rooms = new CList();
                            synchronized (room.clients) {
                                for (Client w : room.clients.values()) {
                                    rooms.add(w.serialize());
                                }
                            }
                            return new Reply(Code.OK, new StringResponse(rooms.toShortJSONb(), "application/json"));
                        case "cfg":
                            String r = request.getFields().get("r");
                            if(r != null) {
                                room = server.rooms.get(TextUtil.unescapeBytes(r));
                                if (room == null) {
                                    r = "\"房间不存在\"";
                                } else {
                                    r = room.locked ? "1" : "0";
                                }
                            } else {
                                CList json2 = new CList();
                                json2.add(server != null);
                                if(server != null) {
                                    json2.add(server.canCreateRoom);
                                    json2.add(server.canJoinRoom);
                                }
                                r = json2.toShortJSON();
                            }
                            return new Reply(Code.OK, new StringResponse(r, "application/json"));
                    }
                    break;
                case "/api":
                    Map<String, String> post = request.postFields();
                    if(!"-1".equals(post.get("r"))) {
                        Room room = server.rooms.get(post.get("r"));
                        if(room == null) {
                            return new Reply(Code.OK, new StringResponse("{\"o\":0,\"r\":\"房间不存在\"}", "application/json"));
                        }
                        switch (post.get("i")) {
                            case "r_lock":
                                room.locked = post.get("v").equals("true");
                                return DONE;
                            case "r_kick_all":
                                synchronized (room.clients) {
                                    room.clients.clear();
                                }
                                return DONE;
                            case "r_kick":
                                String[] values = post.get("v").split(",");
                                synchronized (room.clients) {
                                    for (String s : values) {
                                        try {
                                            room.clients.remove(Integer.parseInt(s));
                                        } catch (NumberFormatException ignored) {}
                                    }
                                }
                                return DONE;
                            case "r_pass":
                                room.token = post.get("v");
                                return DONE;
                        }
                    } else {
                        switch (post.get("i")) {
                            case "power":
                                server.shutdown();
                                return DONE;
                            case "join":
                                server.canJoinRoom = post.get("v").equals("true");
                                return DONE;
                            case "create":
                                server.canCreateRoom = post.get("v").equals("true");
                                return DONE;
                        }
                    }
                    return new Reply(Code.OK, new StringResponse("未知操作"));
            }
            return null;
        });
    }
}
