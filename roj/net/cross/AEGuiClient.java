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

import roj.config.JSONParser;
import roj.config.ParseException;
import roj.config.data.CMapping;
import roj.io.IOUtil;
import roj.io.NonblockingUtil;
import roj.text.TextUtil;
import roj.ui.UIUtil;
import roj.util.ByteWriter;
import roj.util.FastLocalThread;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

import static roj.net.cross.Util.PROTOCOL_VERSION;

/**
 * Your description here
 *
 * @author Roj233
 * @version 0.1
 * @since 2021/9/11 2:00
 */
public class AEGuiClient extends JFrame {
    private final JButton        btnConnect;
    private final JCheckBox      chkSsl;
    private final JTextField     inpUrl;
    private final JTextField     inpHouse;
    private final JTextField     inpPort;
    private final JPasswordField inpPass;

    public static void main(String[] args) throws IOException, ParseException {
        if(!NonblockingUtil.available()) {
            JOptionPane.showMessageDialog(null, "请使用Java8!");
            return;
        }
        if(args.length == 0 && new File("asc.json").isFile()) {
            args = new String[] { "asc.json" };
            System.out.println("检测到 asc.json");
        }
        if(args.length > 0) {
            CMapping cfg = JSONParser.parse(IOUtil.readUTF(new FileInputStream(args[0]))).asMap();

            String[] text = TextUtil.split(cfg.getString("url"), ':');
            if(text.length == 0) {
                JOptionPane.showMessageDialog(null, "服务器端口有误");
                return;
            }

            InetAddress addr;
            try {
                addr = text.length == 1 ? null : InetAddress.getByName(text[0]);
            } catch (UnknownHostException e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(null, "未知的主机");
                return;
            }

            InetSocketAddress address;
            try {
                address = new InetSocketAddress(addr, Integer.parseInt(text[text.length - 1]));
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(null, "服务器端口有误");
                return;
            }
            System.out.println("连接到 " + cfg.getString("url"));
            System.out.println("本地端口 127.0.0.1:" + cfg.getInteger("port"));
            System.out.println("使用SSL安全加密: " + cfg.getBool("ssl"));
            System.out.println("启用访问日志: " + !cfg.getBool("no_log"));
            System.out.println("房间号: " + cfg.getString("room"));

            if(!cfg.getBool("no_log"))
                Util.out = System.out;

            Thread.currentThread().setName("Waiter");
            client = new AEClient(cfg.getString("room"), cfg.getString("pass"), address, new InetSocketAddress(InetAddress.getLoopbackAddress(), cfg.getInteger("port")), cfg.getBool("ssl"));
            Thread runner = new FastLocalThread(client);
            runner.setDaemon(true);
            runner.setName("Client Thread");
            runner.start();
            JOptionPane.showMessageDialog(null, "按确认关闭客户端");
        } else {
            new AEGuiClient();
        }
    }

    public AEGuiClient() {
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new GridBagLayout());
        panel1.setBorder(
                BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "v" + PROTOCOL_VERSION + " By Roj234", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
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
        JButton btnSave = new JButton();
        btnSave.setText("保存");
        gbc = new GridBagConstraints();
        gbc.gridx = 3;
        gbc.gridy = 3;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel1.add(btnSave, gbc);
        chkSsl = new JCheckBox();
        chkSsl.setText("SSL");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        panel1.add(chkSsl, gbc);
        setContentPane(panel1);

        setTitle("AbyssalEye客户端");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        btnConnect.addActionListener(this::toggle);
        btnSave.addActionListener(this::save);

        pack();
        setBounds(0, 0, 360, 160);
        UIUtil.center(this);
        setVisible(true);
        setResizable(true);
        validate();
    }

    static Thread   clientThread;
    static AEClient client;

    private void save(ActionEvent event) {
        CMapping x = new CMapping();
        x.put("room", inpHouse.getText());
        x.put("pass", inpPass.getText());
        x.put("port", Integer.parseInt(inpPort.getText()));
        x.put("ssl", chkSsl.isSelected());
        x.put("url", inpUrl.getText());
        try (FileOutputStream fos = new FileOutputStream("asc.json")) {
            ByteWriter.encodeUTF(x.toJSONb()).writeToStream(fos);
            JOptionPane.showMessageDialog(this, "保存在asc.json");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

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

            if(clientThread != null && clientThread.isAlive()) {
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
            try {
                client = new AEClient(inpHouse.getText(), inpPass.getText(), address, new InetSocketAddress(InetAddress.getLoopbackAddress(), port), chkSsl.isSelected());
            } catch (IOException e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(this, "证书错误/端口被占用");
                return;
            }

            Thread clientRunner = clientThread = new Thread(client);
            clientRunner.setName("Client Thread");
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
