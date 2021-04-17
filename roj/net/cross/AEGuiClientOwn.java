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
import roj.config.JSONParser;
import roj.config.ParseException;
import roj.config.data.CList;
import roj.config.data.CMapping;
import roj.io.IOUtil;
import roj.io.NonblockingUtil;
import roj.net.cross.AEClientOwner.Worker;
import roj.net.tcp.serv.HttpServer;
import roj.net.tcp.serv.Reply;
import roj.net.tcp.serv.response.EmptyResponse;
import roj.net.tcp.serv.response.StringResponse;
import roj.net.tcp.util.Code;
import roj.text.TextUtil;
import roj.ui.TextAreaPrintStream;
import roj.ui.UIUtil;
import roj.util.FastLocalThread;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.concurrent.locks.LockSupport;

import static roj.net.cross.Util.PROTOCOL_VERSION;

/**
 * Your description here
 *
 * @author Roj233
 * @version 0.1
 * @since 2021/9/11 2:00
 */
public class AEGuiClientOwn extends JFrame {
    private final JTextField inpUrl;
    private final JButton    btnConnect;
    private final JCheckBox  chkSsl;
    private final JTextField inpHouse;
    private final JTextField inpPort;
    private final JPasswordField inpPass;

    public static void main(String[] args) throws IOException, ParseException {
        if(!NonblockingUtil.available()) {
            JOptionPane.showMessageDialog(null, "请使用Java8!");
            return;
        }
        if(args.length > 0) {
            CMapping cfg = JSONParser.parse(IOUtil.readUTF(new FileInputStream(args[0]))).asMap();

            String[] text = TextUtil.split(cfg.getString("url"), ':');
            if(text.length == 0) {
                JOptionPane.showMessageDialog(null, "Invalid port");
                return;
            }

            InetAddress addr;
            try {
                addr = text.length == 1 ? null : InetAddress.getByName(text[0]);
            } catch (UnknownHostException e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(null, "Unknown host");
                return;
            }

            InetSocketAddress address;
            try {
                address = new InetSocketAddress(addr, Integer.parseInt(text[text.length - 1]));
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(null, "Invalid port");
                return;
            }

            if(cfg.containsKey("manage_port")) {
                HttpServer server = runServer(cfg.getInteger("manage_port"));
                Thread t = new Thread(server);
                t.setDaemon(true);
                t.setName("Http Server");
                t.start();
            }

            if(!cfg.getBool("no_log"))
                Util.out = System.out;

            client = new AEClientOwner(cfg.getString("room"), cfg.getString("pass"), address, new InetSocketAddress(InetAddress.getLoopbackAddress(), cfg.getInteger("port")), cfg.getBool("ssl"));

            Thread clientRunner = clientThread = new FastLocalThread(client);
            clientRunner.setName("Client Owner Thread");
            clientRunner.start();
        } else {
            Util.out = System.out;
            new AEGuiClientOwn();
        }
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
                    for (Worker w : client.channelById.values()) {
                        w.serialize(lx);
                    }
                    return new Reply(Code.OK, new StringResponse(lx.toJSON(), "application/json"));
                case "/kick_user":
                    int count = 0;
                    String[] arr = request.postFields().get("users").split(",");
                    for (String s : arr) {
                        try {
                            client.channelById.get(Integer.parseInt(s)).alive = false;
                            count++;
                        } catch (Throwable ignored) {}
                    }
                    return new Reply(Code.OK, new StringResponse("{\"count\":" + count + "}", "application/json"));
            }
            return EmptyResponse.INSTANCE;
        });
    }

    public AEGuiClientOwn() {
        UIUtil.setLogo(this, "logo.png");
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new GridBagLayout());
        panel1.setBorder(
                BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "v" + PROTOCOL_VERSION + " By Roj234", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null,
                                                 null));
        btnConnect = new JButton();
        btnConnect.setText("连接");
        GridBagConstraints gbc;
        gbc = new GridBagConstraints();
        gbc.gridx = 3;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel1.add(btnConnect, gbc);
        final JLabel label1 = new JLabel();
        label1.setText("地址");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        panel1.add(label1, gbc);
        chkSsl = new JCheckBox();
        chkSsl.setSelected(false);
        chkSsl.setText("SSL");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        panel1.add(chkSsl, gbc);
        final JLabel label2 = new JLabel();
        label2.setText("房间");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        panel1.add(label2, gbc);
        inpHouse = new JTextField();
        inpHouse.setDoubleBuffered(false);
        inpHouse.setEnabled(true);
        inpHouse.setOpaque(true);
        inpHouse.setRequestFocusEnabled(true);
        inpHouse.setVisible(true);
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 2;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel1.add(inpHouse, gbc);
        final JLabel label3 = new JLabel();
        label3.setText("端口");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        panel1.add(label3, gbc);
        inpPort = new JTextField();
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 3;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel1.add(inpPort, gbc);
        JScrollPane scroll = new JScrollPane();
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.gridwidth = 3;
        gbc.gridheight = 3;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel1.add(scroll, gbc);
        JTextArea text = new JTextArea();
        text.setEditable(false);
        scroll.setViewportView(text);
        JButton btnX = new JButton();
        btnX.setEnabled(false);
        btnX.setText(" ");
        gbc = new GridBagConstraints();
        gbc.gridx = 3;
        gbc.gridy = 4;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel1.add(btnX, gbc);
        JButton btnClear = new JButton();
        btnClear.setText("清空");
        gbc = new GridBagConstraints();
        gbc.gridx = 3;
        gbc.gridy = 5;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel1.add(btnClear, gbc);
        JButton btnHttp = new JButton();
        btnHttp.setText("后台");
        gbc = new GridBagConstraints();
        gbc.gridx = 3;
        gbc.gridy = 6;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel1.add(btnHttp, gbc);
        final JLabel label4 = new JLabel();
        label4.setText("密码");
        gbc = new GridBagConstraints();
        gbc.gridx = 2;
        gbc.gridy = 2;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        panel1.add(label4, gbc);
        inpUrl = new JTextField();
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel1.add(inpUrl, gbc);
        inpPass = new JPasswordField();
        gbc = new GridBagConstraints();
        gbc.gridx = 3;
        gbc.gridy = 2;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel1.add(inpPass, gbc);
        setContentPane(panel1);

        setTitle("AbyssalEye客户端服务器");
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
        btnConnect.addActionListener(this::toggle);
        Util.out = new TextAreaPrintStream(text, 66666);
        System.setErr(Util.out);
        System.setOut(Util.out);
        btnClear.addActionListener(e -> text.setText(""));

        new Thread() {
            {
                setName("Updater");
                setDaemon(true);
                start();
            }

            @Override
            public void run() {
                boolean prevState = false;
                while (true) {
                    boolean currState = client != null && clientThread != null && clientThread.isAlive();
                    if(prevState != currState) {
                        prevState = currState;
                        if(!currState && !btnConnect.getText().equals("连接"))
                            toggle(null);
                    }
                    LockSupport.parkNanos(10000);
                }
            }
        };

        setBounds(0, 0, 360, 250);
        UIUtil.center(this);
        setVisible(true);
        setResizable(false);
        validate();
    }

    static Thread clientThread;
    static AEClientOwner client;

    private void toggle(ActionEvent event) {
        if (btnConnect.getText().equals("断开")) {
            btnConnect.setEnabled(false);

            btnConnect.setText("连接");
            btnConnect.setEnabled(true);

            chkSsl.setEnabled(true);
            inpHouse.setEnabled(true);
            inpPass.setEnabled(true);
            inpPort.setEnabled(true);
            inpUrl.setEnabled(true);

            if(clientThread != null) {
                try {
                    client.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                try {
                    clientThread.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            clientThread = null;
            client = null;
        } else {
            String[] text = TextUtil.split(inpUrl.getText(), ':');
            if(text.length == 0) {
                JOptionPane.showMessageDialog(this, "服务器端口有误");
                return;
            }

            InetAddress addr;
            try {
                addr = text.length == 1 ? null : InetAddress.getByName(text[0]);
            } catch (UnknownHostException e) {
                JOptionPane.showMessageDialog(this, "未知的主机");
                return;
            }

            InetSocketAddress address;
            try {
                address = new InetSocketAddress(addr, Integer.parseInt(text[text.length - 1]));
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this, "服务器端口有误");
                return;
            }

            int port;
            try {
                port = Integer.parseInt(inpPort.getText());
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this, "本地端口有误");
                return;
            }
            client = new AEClientOwner(inpHouse.getText(), inpPass.getText(), address, new InetSocketAddress(InetAddress.getLoopbackAddress(), port), chkSsl.isSelected());

            Thread clientRunner = clientThread = new FastLocalThread(client);
            clientRunner.setName("Client Owner Thread");
            clientRunner.setDaemon(true);
            clientRunner.start();

            btnConnect.setText("断开");

            chkSsl.setEnabled(false);
            inpHouse.setEnabled(false);
            inpPass.setEnabled(false);
            inpPort.setEnabled(false);
            inpUrl.setEnabled(false);
        }
    }
}
