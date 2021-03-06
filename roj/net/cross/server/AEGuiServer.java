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
import roj.crypt.PSKFile;
import roj.io.IOUtil;
import roj.io.NIOUtil;
import roj.net.cross.Util;
import roj.net.http.HttpServer;
import roj.net.http.serv.StringResponse;
import roj.net.mss.JKeyPair;
import roj.net.mss.MSSKeyPair;
import roj.net.mss.SimpleEngineFactory;
import roj.text.CharList;
import roj.text.LoggingStream;
import roj.text.TextUtil;

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

    public static void main(String[] args) throws GeneralSecurityException, IOException {
        if(!NIOUtil.available()) {
            JOptionPane.showMessageDialog(null, "NIO Native Helper is unavailable!\n?????????Java8!");
            return;
        }

        byte[] keyPass = null;
        String port = null, motd = "?????????????????????(1/1)";
        int webPort = -1, maxUsers = 100;
        boolean log = false;
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
                case "-motd":
                    motd = args[++i];
                    break;
                case "-keyformat":
                    break;
            }
        }

        if (keyPass == null) {
            JOptionPane.showMessageDialog(null,
                    "???40????????????????????????????????????,\n" +
                    "???????????????????????????????????????key");
            return;
        }

        String[] text = TextUtil.split1(port, ':');

        InetAddress host;
        try {
            host = text.length == 1 ? null : InetAddress.getByName(text[0]);
        } catch (UnknownHostException e) {
            System.out.println("???????????????");
            return;
        }

        InetSocketAddress addr;
        try {
            addr = new InetSocketAddress(host, Integer.parseInt(text[text.length - 1]));
        } catch (NumberFormatException e) {
            System.out.println("?????????????????????");
            return;
        }

        if(maxUsers <= 1) {
            System.out.println("????????????????????????");
            return;
        }

        KeyPair pair = PSKFile.getInstance("RSA").loadOrGenerate(
                new File("ae_server.key"),
                new File("ae_client.key"), keyPass);
        if (pair == null) {
            System.out.println("??????????????????");
            return;
        }

        try {
            SimpleEngineFactory factory = new SimpleEngineFactory(pair);
            factory.setPSK(new MSSKeyPair[]{ new JKeyPair(0, pair) });
            server = new AEServer(addr, maxUsers, factory);
        } catch (GeneralSecurityException | IOException e) {
            System.out.println("Invalid certificate / IO Error");
            e.printStackTrace();
            return;
        }
        server.setMOTD(motd);
        server.start();

        if(webPort != -1) {
            runServer(webPort).start();
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
        out.println(" ?????? Version " + Util.PROTOCOL_VERSION);
        out.println();
        out.println("??????????????????");

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
        StringResponse DONE = new StringResponse("{\"o\":1}", "application/json");

        return new HttpServer(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 64, (request, requestHandler) -> {
            switch (request.path()) {
                case "/bundle.min.css":
                    return new StringResponse(res("bundle.min.css"), "text/css");
                case "/bundle.min.js":
                    return new StringResponse(res("bundle.min.js"), "text/javascript");
                case "/":
                    return new StringResponse(res("server.html"), "text/html");
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
                            return new StringResponse(json.toShortJSONb(), "application/json");
                        case "users":
                            Room room = server.rooms.get(TextUtil.decodeURI(request.getFields().get("r")));
                            if(room == null) {
                                return new StringResponse("\"???????????????\"", "application/json");
                            }
                            CList rooms = new CList();
                            synchronized (room.clients) {
                                for (Client w : room.clients.values()) {
                                    rooms.add(w.serialize());
                                }
                            }
                            return new StringResponse(rooms.toShortJSONb(), "application/json");
                        case "cfg":
                            String r = request.getFields().get("r");
                            if(r != null) {
                                room = server.rooms.get(TextUtil.decodeURI(r));
                                if (room == null) {
                                    r = "\"???????????????\"";
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
                            return new StringResponse(r, "application/json");
                    }
                    break;
                case "/api":
                    Map<String, String> post = request.payloadFields();
                    if(!"-1".equals(post.get("r"))) {
                        Room room = server.rooms.get(post.get("r"));
                        if(room == null) {
                            return new StringResponse("{\"o\":0,\"r\":\"???????????????\"}", "application/json");
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
                    return new StringResponse("????????????");
            }
            return null;
        });
    }
}
