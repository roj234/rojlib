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

import roj.config.data.CList;
import roj.io.IOUtil;
import roj.io.NonblockingUtil;
import roj.net.NetworkUtil;
import roj.net.tcp.serv.HttpServer;
import roj.net.tcp.serv.Reply;
import roj.net.tcp.serv.response.EmptyResponse;
import roj.net.tcp.serv.response.StringResponse;
import roj.net.tcp.util.ResponseCode;
import roj.text.TextUtil;
import roj.ui.TextAreaPrintStream;
import roj.ui.UIUtil;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.security.GeneralSecurityException;

/**
 * Your description here
 *
 * @author Roj233
 * @version 0.1
 * @since 2021/9/11 12:49
 */
public class AEGuiServer extends JFrame {
    private final JButton   btnToggle;
    private final JButton     chkSsl;
    private final JTextField inpAddr;
    private final JTextField inpMaxUser;

    static AEServer server;
    static Thread serverThread;

    public static void main(String[] args) {
        if(!NonblockingUtil.available()) {
            JOptionPane.showMessageDialog(null, "请使用Java8!");
            return;
        }
        new AEGuiServer();
    }

    public AEGuiServer() {
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new GridBagLayout());
        panel1.setBorder(
                BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "AbyssalEye 0.3.1 By Roj234", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null,
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
        JTextArea houses = new JTextArea();
        scroll.setViewportView(houses);
        btnToggle = new JButton();
        btnToggle.setText("启动");
        gbc = new GridBagConstraints();
        gbc.gridx = 3;
        gbc.gridy = 4;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel1.add(btnToggle, gbc);
        JButton btnKick = new JButton();
        btnKick.setEnabled(false);
        btnKick.setText("预留");
        gbc = new GridBagConstraints();
        gbc.gridx = 3;
        gbc.gridy = 2;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel1.add(btnKick, gbc);
        JButton btnHttp = new JButton();
        btnHttp.setText("后台");
        gbc = new GridBagConstraints();
        gbc.gridx = 3;
        gbc.gridy = 3;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel1.add(btnHttp, gbc);
        chkSsl = new JButton();
        chkSsl.setText("SSL");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        panel1.add(chkSsl, gbc);
        inpAddr = new JTextField();
        inpAddr.setText("0.0.0.0:3355");
        gbc = new GridBagConstraints();
        gbc.gridx = 3;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel1.add(inpAddr, gbc);
        final JLabel label1 = new JLabel();
        label1.setText("Addr");
        gbc = new GridBagConstraints();
        gbc.gridx = 2;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        panel1.add(label1, gbc);
        inpMaxUser = new JTextField();
        inpMaxUser.setText("512");
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
            String s = JOptionPane.showInputDialog("Http Manage Port\n配置[manage_port]项");
            if(s == null) return;
            int port = Integer.parseInt(s);
            btnHttp.setEnabled(false);

            try {
                HttpServer server = runServer(port);
                Thread t = new Thread(server);
                t.setDaemon(true);
                t.setName("Http Server");
                t.start();
            } catch (IOException exception) {
                exception.printStackTrace();
            }
        });

        System.setOut(new TextAreaPrintStream(houses, 65535));

        chkSsl.addActionListener(e -> {
            Util.SslDialog.sho1w();
        });

        btnToggle.addActionListener(this::toggle);

        setBounds(0, 0, 400, 300);
        UIUtil.center(this);
        pack();
        setVisible(true);
        setResizable(true);
        validate();
    }

    private HttpServer runServer(int port) throws IOException {
        return new HttpServer(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 64, (socket, request) -> {
            switch (request.path()) {
                case "/":
                    return new Reply(ResponseCode.OK, new StringResponse(IOUtil.readAsUTF(AEGuiClientOwn.class, "META-INF/ae_res/ae_s.html"), "text/html"));
                case "/user_list":
                    String roomId = request.getFields().get("room");
                    CList lx = new CList();
                    return new Reply(ResponseCode.UNAVAILABLE, new StringResponse("Unsupported case", "application/json"));
                case "/kick_room":
                case "/toggle_open":
                case "/lock_room":
                case "/kick_user":
                    return new Reply(ResponseCode.UNAVAILABLE, new StringResponse("Unsupported case", "application/json"));
            }
            return EmptyResponse.INSTANCE;
        });
    }

    private void toggle(ActionEvent event) {
        if (btnToggle.getText().equals("停止")) {
            btnToggle.setEnabled(false);

            btnToggle.setText("启动");
            btnToggle.setEnabled(true);

            chkSsl.setEnabled(true);
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
                JOptionPane.showMessageDialog(this, "Invalid port");
                return;
            }

            InetAddress addr;
            try {
                addr = text.length == 1 ? null : InetAddress.getByAddress(NetworkUtil.ip2bytes(text[0]));
            } catch (UnknownHostException e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(this, "Unknown host");
                return;
            }

            InetSocketAddress address;
            try {
                address = new InetSocketAddress(addr, Integer.parseInt(text[text.length - 1]));
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this, "Invalid port");
                return;
            }

            int maxUsers;
            try {
                maxUsers = Integer.parseInt(inpMaxUser.getText());
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this, "Invalid max connection");
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

            chkSsl.setEnabled(false);
            inpAddr.setEnabled(false);
            inpMaxUser.setEnabled(false);

        }
    }
}
