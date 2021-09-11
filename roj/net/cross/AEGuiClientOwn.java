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

import roj.net.NetworkUtil;
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
import java.util.concurrent.locks.LockSupport;

/**
 * Your description here
 *
 * @author Roj233
 * @version 0.1
 * @since 2021/9/11 2:00
 */
public class AEGuiClientOwn extends JFrame {
    private JTextField     inpUrl;
    private JButton        btnConnect;
    private JCheckBox      chkSsl;
    private JPasswordField inpServPass;
    private JTextField     inpHouse;
    private JTextField     inpPort;
    private JButton        btnSave;
    private JTextArea      stdout;
    private JButton        btnClear;
    private JButton        btnManage;
    JScrollPane scroll;
    private JPasswordField inpPass;

    public static void main(String[] args) {
        new AEGuiClientOwn();
    }

    public AEGuiClientOwn() {
        setTitle("AbyssalEye客户端服务器");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        btnConnect.addActionListener(this::toggle);
        System.setOut(new TextAreaPrintStream(stdout, 99999));
        Util.out = System.out;

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
                    boolean currState = client != null && clientThread.isAlive();
                    if(prevState != currState) {
                        prevState = currState;
                        if(!currState && !btnConnect.getText().equals("连接"))
                            toggle(null);
                    }
                    LockSupport.parkNanos(10000);
                }
            }
        };

        pack();
        setBounds(0, 0, 400, 320);
        UIUtil.center(this);
        setVisible(true);
        setResizable(true);
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
            inpServPass.setEnabled(true);
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

            int port;
            try {
                port = Integer.parseInt(inpPort.getText());
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this, "Invalid port");
                return;
            }
            client = new AEClientOwner(inpHouse.getText(), inpPass.getText(), address, new InetSocketAddress(InetAddress.getLoopbackAddress(), port), chkSsl.isSelected());

            Thread clientRunner = clientThread = new Thread(client);
            clientRunner.setName("Client Thread");
            clientRunner.setDaemon(true);
            clientRunner.start();

            btnConnect.setText("断开");

            chkSsl.setEnabled(false);
            inpHouse.setEnabled(false);
            inpPass.setEnabled(false);
            inpServPass.setEnabled(false);
            inpPort.setEnabled(false);
            inpUrl.setEnabled(false);
        }
    }

    {
// GUI initializer generated by IntelliJ IDEA GUI Designer
// >>> IMPORTANT!! <<<
// DO NOT EDIT OR ADD ANY CODE HERE!
        $$$setupUI$$$();
    }

    /**
     * Method generated by IntelliJ IDEA GUI Designer
     * >>> IMPORTANT!! <<<
     * DO NOT edit this method OR call it in your code!
     *
     * @noinspection ALL
     */
    private void $$$setupUI$$$() {
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new GridBagLayout());
        panel1.setBorder(
                BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "AbyssalEye 0.3.1 By Roj234", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
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
        label1.setText("服务器地址");
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
        label3.setToolTipText("eg: 80-88,1954");
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
        scroll = new JScrollPane();
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.gridwidth = 3;
        gbc.gridheight = 3;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel1.add(scroll, gbc);
        stdout = new JTextArea();
        stdout.setEditable(false);
        stdout.setEnabled(true);
        stdout.setLineWrap(true);
        scroll.setViewportView(stdout);
        btnSave = new JButton();
        btnSave.setEnabled(false);
        btnSave.setText("保存");
        gbc = new GridBagConstraints();
        gbc.gridx = 3;
        gbc.gridy = 4;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel1.add(btnSave, gbc);
        btnClear = new JButton();
        btnClear.setEnabled(false);
        btnClear.setText("踢出");
        gbc = new GridBagConstraints();
        gbc.gridx = 3;
        gbc.gridy = 5;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel1.add(btnClear, gbc);
        btnManage = new JButton();
        btnManage.setEnabled(false);
        btnManage.setText("管理");
        gbc = new GridBagConstraints();
        gbc.gridx = 3;
        gbc.gridy = 6;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel1.add(btnManage, gbc);
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
        inpServPass = new JPasswordField();
        gbc = new GridBagConstraints();
        gbc.gridx = 2;
        gbc.gridy = 1;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel1.add(inpServPass, gbc);
        final JLabel label5 = new JLabel();
        label5.setText("服务器密码");
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        panel1.add(label5, gbc);
        setContentPane(panel1);
    }
}
