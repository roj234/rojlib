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
import roj.config.data.CList;
import roj.config.data.CMapping;
import roj.io.IOUtil;
import roj.io.NonblockingUtil;
import roj.net.cross.AEServer.Room;
import roj.net.cross.AEServer.Worker;
import roj.net.tcp.serv.HttpServer;
import roj.net.tcp.serv.Reply;
import roj.net.tcp.serv.response.EmptyResponse;
import roj.net.tcp.serv.response.StringResponse;
import roj.net.tcp.util.Code;
import roj.text.ACalendar;
import roj.text.CharList;
import roj.text.TextUtil;
import roj.ui.DelegatedPrintStream;
import roj.ui.UIUtil;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.security.GeneralSecurityException;
import java.util.Map;
import java.util.PrimitiveIterator.OfInt;
import java.util.concurrent.locks.LockSupport;

import static roj.net.cross.Util.PROTOCOL_VERSION;

/**
 * AbyssalEye Server GUI
 *
 * @author Roj233
 * @version 31
 * @since 2021/9/11 12:49
 */
public class AEGuiServer extends JFrame {
    private final JButton   btnToggle, btnSsl;
    private final JTextField inpAddr;
    private final JTextField inpMaxUser;

    static AEServer server;
    static Thread serverThread;
    static RingBuffer<String> logger;
    static AEGuiServer instance;

    public static void main(String[] args) {
        if(!NonblockingUtil.available()) {
            JOptionPane.showMessageDialog(null, "请使用Java8!");
            return;
        }
        boolean nolog = false;
        boolean nogui = false;
        String port = null;
        int webPort = -1, maxUsers = 100;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-nolog":
                    nolog = true;
                    break;
                case "-nogui":
                    nogui = true;
                    break;
                case "-maxUsers":
                    maxUsers = Integer.parseInt(args[++i]);
                    break;
                case "-port":
                    port = args[++i];
                    break;
                case "-web":
                    webPort = Integer.parseInt(args[++i]);
                    break;
                case "-ssl":
                    Util.certFile = args[++i];
                    Util.SslDialog.sho1w();
                    break;
            }
        }
        if(!nogui) {
            instance = new AEGuiServer();
        } else {
            String[] text = TextUtil.split(port, ':');
            if(text.length == 0) {
                System.out.println("无效的监听端口");
                return;
            }

            InetAddress addr;
            try {
                addr = text.length == 1 ? null : InetAddress.getByName(text[0]);
            } catch (UnknownHostException e) {
                System.out.println("未知的主机");
                return;
            }

            InetSocketAddress address;
            try {
                address = new InetSocketAddress(addr, Integer.parseInt(text[text.length - 1]));
            } catch (NumberFormatException e) {
                System.out.println("无效的监听端口");
                return;
            }

            if(maxUsers <= 1) {
                System.out.println("无效的最大连接数");
                return;
            }

            try {
                server = Util.certFile != null ? new AEServer(address, maxUsers, Util.certFile, Util.certPass) : new AEServer(address, maxUsers);
            } catch (IOException | GeneralSecurityException e) {
                e.printStackTrace();
                System.out.println("Invalid certificate / IO Error");
                return;
            }

            Thread serverRunner = serverThread = new Thread(server);
            serverRunner.setName("Server Thread");
            serverRunner.start();

            if(webPort != -1) {
                try {
                    HttpServer server = runServer(webPort);
                    Thread t = new Thread(server);
                    t.setDaemon(true);
                    t.setName("Http Server");
                    t.start();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        if (!nolog) {
            RingBuffer<String> logger = AEGuiServer.logger = new RingBuffer<>(Integer.parseInt(System.getProperty("ae.logLines", "1000")));
            ACalendar cl = new ACalendar();
            Util.out = new DelegatedPrintStream(2000) {
                @Override
                protected void newLine() {
                    logger.push(cl.formatDate("[H:i:s] ", System.currentTimeMillis()) + sb);
                    sb.clear();
                }
            };
            System.setOut(Util.out);
            System.setErr(Util.out);
        }
    }

    public AEGuiServer() {
        UIUtil.setLogo(this, "logo.png");
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new GridBagLayout());
        panel1.setBorder(
                BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "v" + PROTOCOL_VERSION + " By Roj234", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null,
                                                 null));
        JScrollPane scroll = new JScrollPane();
        GridBagConstraints gbc;
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 3;
        gbc.gridheight = 3;
        gbc.weightx = 1.0;
        gbc.weighty = 2.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel1.add(scroll, gbc);
        JTextArea text = new JTextArea();
        text.setLineWrap(true);
        text.setEditable(false);
        text.setText("这里本来是放日志的\n现在请使用【后台】按钮");
        scroll.setViewportView(text);
        btnToggle = new JButton();
        btnToggle.setText("启动");
        gbc = new GridBagConstraints();
        gbc.gridx = 3;
        gbc.gridy = 4;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel1.add(btnToggle, gbc);
        JButton btnHttp = new JButton();
        btnHttp.setText("后台");
        gbc = new GridBagConstraints();
        gbc.gridx = 3;
        gbc.gridy = 3;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel1.add(btnHttp, gbc);
        btnSsl = new JButton();
        btnSsl.setText("加密");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        panel1.add(btnSsl, gbc);
        inpAddr = new JTextField();
        inpAddr.setText("0.0.0.0:3355");
        gbc = new GridBagConstraints();
        gbc.gridx = 2;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel1.add(inpAddr, gbc);
        final JLabel label1 = new JLabel();
        label1.setText("Addr");
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        panel1.add(label1, gbc);
        inpMaxUser = new JTextField();
        inpMaxUser.setText("50");
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel1.add(inpMaxUser, gbc);
        final JLabel label3 = new JLabel();
        label3.setText("最大连接");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.anchor = GridBagConstraints.WEST;
        panel1.add(label3, gbc);

        setContentPane(panel1);

        setTitle("AbyssalEye服务器");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        btnHttp.addActionListener(e -> {
            String s = JOptionPane.showInputDialog("Http 管理端口\n配置[manage_port]项\n只能本地访问");
            if(s == null) return;
            int port = Integer.parseInt(s);

            try {
                HttpServer server = runServer(port);
                Thread t = new Thread(server);
                t.setDaemon(true);
                t.setName("Http Server");
                t.start();
            } catch (IOException e1) {
                e1.printStackTrace();
            }
            btnHttp.setEnabled(false);
            btnHttp.setText(":" + port);
        });

        btnSsl.addActionListener(e -> {
            Util.SslDialog.sho1w();
        });

        btnToggle.addActionListener(this::toggle);

        setBounds(0, 0, 280, 200);
        UIUtil.center(this);
        setVisible(true);
        setResizable(false);
        validate();
    }

    static MyHashMap<String, String> tmp = new MyHashMap<>();
    private static String res(String name) throws IOException {
        String v = tmp.get(name);
        if(v == null)
            tmp.put(name, v = IOUtil.readUTF("META-INF/ae/html/" + name));
        return v;
    }

    private static HttpServer runServer(int port) throws IOException {
        Reply NOT_RUNNING = new Reply(Code.OK, new StringResponse("{\"o\":0,\"r\":\"服务器没有启动\"}", "application/json"));
        Reply DONE = new Reply(Code.OK, new StringResponse("{\"o\":1}", "application/json"));
        Reply UNCHANGED = new Reply(Code.OK, new StringResponse("{\"o\":1,\"r\":\"状态相同未更改\"}", "application/json"));

        return new HttpServer(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 64, (socket, request) -> {
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
                            if(logger == null) {
                                logs = "服务器未开启日志缓冲";
                                logger = new RingBuffer<>(1);
                            } else {
                                CharList cl = new CharList();
                                for (String s : logger) {
                                    cl.append(s).append("\r\n");
                                }
                                logger.clear();
                                logs = cl.toString();
                            }
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
                            if(server == null)
                                return NOT_RUNNING;
                            Room room = server.rooms.get(TextUtil.unescapeBytes(request.getFields().get("r")));
                            if(room == null) {
                                return new Reply(Code.OK, new StringResponse("\"房间不存在\"", "application/json"));
                            }
                            CList rooms = new CList();
                            synchronized (room.slaves) {
                                for (Worker w : room.slaves.values()) {
                                    rooms.add(w.serialize());
                                }
                            }
                            return new Reply(Code.OK, new StringResponse(rooms.toShortJSONb(), "application/json"));
                        case "cfg":
                            String r = request.getFields().get("r");
                            if(r != null) {
                                if (server == null) return NOT_RUNNING;
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
                        if(server == null)
                            return NOT_RUNNING;
                        Room room = server.rooms.get(post.get("r"));
                        if(room == null) {
                            return new Reply(Code.OK, new StringResponse("{\"o\":0,\"r\":\"房间不存在\"}", "application/json"));
                        }
                        switch (post.get("i")) {
                            case "r_lock":
                                room.locked = post.get("v").equals("true");
                                return DONE;
                            case "r_close":
                                try {
                                    server.m_KickRoom(room);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                                return DONE;
                            case "r_kick_all":
                                while (!room.compareAndSet(0, 1)) {
                                    LockSupport.parkNanos(10);
                                    if(room.master == null) return UNCHANGED;
                                }
                                synchronized (room.slaves) {
                                    for (OfInt itr = room.slaves.keySet().iterator(); itr.hasNext(); ) {
                                        room.kicked.add(itr.nextInt());
                                    }
                                }
                                room.set(0);
                                return DONE;
                            case "r_kick":
                                String[] values = post.get("v").split(",");
                                while (!room.compareAndSet(0, 1)) {
                                    LockSupport.parkNanos(10);
                                    if(room.master == null) return UNCHANGED;
                                }
                                for (String s : values) {
                                    try {
                                        room.kicked.add(Integer.parseInt(s));
                                    } catch (NumberFormatException ignored) {}
                                }
                                room.set(0);
                                return DONE;
                            case "r_pass":
                                room.token = post.get("v");
                                return DONE;
                        }
                    } else {
                        switch (post.get("i")) {
                            case "power":
                                if((server == null) == post.get("v").equals("true")) {
                                    instance.toggle(null);
                                    return DONE;
                                }
                                return UNCHANGED;
                            case "join":
                                if(server == null)
                                    return NOT_RUNNING;
                                server.canJoinRoom = post.get("v").equals("true");
                                return DONE;
                            case "create":
                                if(server == null)
                                    return NOT_RUNNING;
                                server.canCreateRoom = post.get("v").equals("true");
                                return DONE;
                        }
                    }
                    return new Reply(Code.OK, new StringResponse("未知操作"));
            }
            return EmptyResponse.INSTANCE;
        });
    }

    private void toggle(ActionEvent event) {
        if (btnToggle.getText().equals("停止")) {
            btnToggle.setEnabled(false);

            btnToggle.setText("启动");
            btnToggle.setEnabled(true);

            btnSsl.setEnabled(true);
            inpAddr.setEnabled(true);
            inpMaxUser.setEnabled(true);

            if(serverThread != null) {
                server.shutdown();
            }
            serverThread = null;
            server = null;
        } else {
            String[] text = TextUtil.split(inpAddr.getText(), ':');
            if(text.length == 0) {
                JOptionPane.showMessageDialog(this, "无效的监听端口");
                return;
            }

            InetAddress addr;
            try {
                addr = text.length == 1 ? null : InetAddress.getByName(text[0]);
            } catch (UnknownHostException e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(this, "未知的主机");
                return;
            }

            InetSocketAddress address;
            try {
                address = new InetSocketAddress(addr, Integer.parseInt(text[text.length - 1]));
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this, "无效的监听端口");
                return;
            }

            int maxUsers;
            try {
                maxUsers = Integer.parseInt(inpMaxUser.getText());
                if(maxUsers <= 1) {
                    JOptionPane.showMessageDialog(this, "无效的最大连接数");
                    return;
                }
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this, "无效的最大连接数");
                return;
            }
            try {
                server = Util.certFile != null ? new AEServer(address, maxUsers, Util.certFile, Util.certPass) : new AEServer(address, maxUsers);
            } catch (IOException | GeneralSecurityException e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(this, "Invalid certificate / IO Error");
                return;
            }

            Thread serverRunner = serverThread = new Thread(server);
            serverRunner.setName("Server Thread");
            serverRunner.setDaemon(true);
            serverRunner.start();

            btnToggle.setText("停止");

            btnSsl.setEnabled(false);
            inpAddr.setEnabled(false);
            inpMaxUser.setEnabled(false);
        }
    }
}
