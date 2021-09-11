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
import roj.net.cross.AEServer.Room;
import roj.text.TextUtil;
import roj.ui.UIUtil;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.security.GeneralSecurityException;
import java.util.Vector;
import java.util.concurrent.locks.LockSupport;

/**
 * Your description here
 *
 * @author Roj233
 * @version 0.1
 * @since 2021/9/11 12:49
 */
public class AEGuiServer extends JFrame {
    private JButton       btnToggle;
    private JButton       btnKick;
    private JButton       btnManage;
    private JList<String> houses;
    private JButton     chkSsl;
    private JTextField    inpPass;
    private JTextField  inpAddr;
    private JScrollPane scroll;
    private JTextField  inpMaxUser;

    static AEServer server;
    static Thread serverThread;

    static String certFile;
    static char[] certPass;

    public static void main(String[] args) {
        new AEGuiServer();
    }

    public AEGuiServer() {
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new GridBagLayout());
        panel1.setBorder(
                BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "AbyssalEye 0.3.1 By Roj234", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null,
                                                 null));
        scroll = new JScrollPane();
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
        houses = new JList<>();
        scroll.setViewportView(houses);
        btnToggle = new JButton();
        btnToggle.setText("Start");
        gbc = new GridBagConstraints();
        gbc.gridx = 3;
        gbc.gridy = 4;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel1.add(btnToggle, gbc);
        btnKick = new JButton();
        btnKick.setEnabled(false);
        btnKick.setText("踢出");
        gbc = new GridBagConstraints();
        gbc.gridx = 3;
        gbc.gridy = 2;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel1.add(btnKick, gbc);
        btnManage = new JButton();
        btnManage.setEnabled(false);
        btnManage.setText("锁定");
        gbc = new GridBagConstraints();
        gbc.gridx = 3;
        gbc.gridy = 3;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel1.add(btnManage, gbc);
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
        label1.setText("Bind on");
        gbc = new GridBagConstraints();
        gbc.gridx = 2;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        panel1.add(label1, gbc);
        inpPass = new JTextField();
        gbc = new GridBagConstraints();
        gbc.gridx = 3;
        gbc.gridy = 1;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel1.add(inpPass, gbc);
        inpMaxUser = new JTextField();
        inpMaxUser.setText("512");
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel1.add(inpMaxUser, gbc);
        final JLabel label2 = new JLabel();
        label2.setText("Pass");
        gbc = new GridBagConstraints();
        gbc.gridx = 2;
        gbc.gridy = 1;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        panel1.add(label2, gbc);
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

        // todo
        inpPass.setEnabled(false);

        chkSsl.addActionListener(e -> {
            SslDialog.sho1w();
        });

        new Thread() {
            {
                setName("Updater");
                setDaemon(true);
                start();
            }

            @Override
            public void run() {
                while (true) {
                    if(server != null) {
                        Vector<String> vex = new Vector<>();
                        for (Room room : server.rooms.values()) {
                            vex.add(room.id + "(" + (room.slaves.size() + 1) + ")");
                        }
                        houses.setListData(vex);
                        houses.addListSelectionListener(new ListSelectionListener() {
                            @Override
                            public void valueChanged(ListSelectionEvent e) {
                                // todo drag
                            }
                        });
                    }
                    LockSupport.parkNanos(5000L * 1000 * 1000);
                }
            }
        };


        btnToggle.addActionListener(this::toggle);
        btnManage.addActionListener(this::roomManage);

        setBounds(0, 0, 400, 300);
        UIUtil.center(this);
        pack();
        setVisible(true);
        setResizable(true);
        validate();
    }

    private void roomManage(ActionEvent event) {

    }

    private void toggle(ActionEvent event) {
        if (btnToggle.getText().equals("Shutdown")) {
            btnToggle.setEnabled(false);

            btnToggle.setText("Start");
            btnToggle.setEnabled(true);

            chkSsl.setEnabled(true);
            inpAddr.setEnabled(true);
            inpPass.setEnabled(true);
            inpMaxUser.setEnabled(true);
            houses.removeAll();

            if(serverThread != null && serverThread.isAlive()) {
                server.shutdown();
                try {
                    serverThread.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
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
                server = certFile != null ? new AEServer(address, maxUsers, certFile, certPass) : new AEServer(address, maxUsers);
            } catch (IOException | GeneralSecurityException e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(this, "Invalid certificate / IO Error");
                return;
            }
            //server.password = inpPass.getText().toCharArray();

            Thread serverRunner = serverThread = new Thread(server);
            serverRunner.setName("Server Thread");
            serverRunner.setDaemon(true);
            serverRunner.start();

            btnToggle.setText("Shutdown");

            chkSsl.setEnabled(false);
            inpAddr.setEnabled(false);
            inpPass.setEnabled(false);
            inpMaxUser.setEnabled(false);
            houses.removeAll();

        }
    }

    public static class SslDialog extends JDialog {
        private final JPasswordField inpPass;
        private final JTextField     inpCert;
        static boolean x;

        public static void sho1w() {
            if(!x) {
                x = true;
                new SslDialog();
            }
        }

        public SslDialog() {
            JPanel contentPane = new JPanel();
            contentPane.setLayout(new GridBagLayout());
            final JPanel panel1 = new JPanel();
            panel1.setLayout(new GridBagLayout());
            GridBagConstraints gbc;
            gbc = new GridBagConstraints();
            gbc.gridx = 0;
            gbc.gridy = 1;
            gbc.weightx = 1.0;
            gbc.weighty = 1.0;
            gbc.fill = GridBagConstraints.BOTH;
            contentPane.add(panel1, gbc);
            final JPanel panel2 = new JPanel();
            panel2.setLayout(new GridBagLayout());
            gbc = new GridBagConstraints();
            gbc.gridx = 1;
            gbc.gridy = 0;
            gbc.weighty = 1.0;
            gbc.fill = GridBagConstraints.BOTH;
            panel1.add(panel2, gbc);
            JButton buttonOK = new JButton();
            buttonOK.setText("OK");
            gbc = new GridBagConstraints();
            gbc.gridx = 0;
            gbc.gridy = 0;
            gbc.weightx = 1.0;
            gbc.weighty = 1.0;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            panel2.add(buttonOK, gbc);
            JButton buttonCancel = new JButton();
            buttonCancel.setText("Cancel");
            gbc = new GridBagConstraints();
            gbc.gridx = 1;
            gbc.gridy = 0;
            gbc.weightx = 1.0;
            gbc.weighty = 1.0;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            panel2.add(buttonCancel, gbc);
            final JPanel panel3 = new JPanel();
            panel3.setLayout(new GridBagLayout());
            gbc = new GridBagConstraints();
            gbc.gridx = 0;
            gbc.gridy = 0;
            gbc.weightx = 1.0;
            gbc.weighty = 1.0;
            gbc.fill = GridBagConstraints.BOTH;
            contentPane.add(panel3, gbc);
            JButton btnBrowseCert = new JButton();
            btnBrowseCert.setText("Browse");
            gbc = new GridBagConstraints();
            gbc.gridx = 2;
            gbc.gridy = 0;
            gbc.weighty = 1.0;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            panel3.add(btnBrowseCert, gbc);
            inpPass = new JPasswordField();
            gbc = new GridBagConstraints();
            gbc.gridx = 1;
            gbc.gridy = 1;
            gbc.weightx = 1.0;
            gbc.weighty = 1.0;
            gbc.anchor = GridBagConstraints.WEST;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            panel3.add(inpPass, gbc);
            final JLabel label1 = new JLabel();
            label1.setText("Cert");
            gbc = new GridBagConstraints();
            gbc.gridx = 0;
            gbc.gridy = 0;
            gbc.weighty = 1.0;
            gbc.anchor = GridBagConstraints.WEST;
            panel3.add(label1, gbc);
            final JLabel label2 = new JLabel();
            label2.setText("Pass");
            gbc = new GridBagConstraints();
            gbc.gridx = 0;
            gbc.gridy = 1;
            gbc.weighty = 1.0;
            gbc.anchor = GridBagConstraints.WEST;
            panel3.add(label2, gbc);
            inpCert = new JTextField();
            gbc = new GridBagConstraints();
            gbc.gridx = 1;
            gbc.gridy = 0;
            gbc.weightx = 1.0;
            gbc.weighty = 1.0;
            gbc.anchor = GridBagConstraints.WEST;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            panel3.add(inpCert, gbc);

            setContentPane(contentPane);
            setModal(true);
            getRootPane().setDefaultButton(buttonOK);

            buttonOK.addActionListener(e -> onOK());

            ActionListener c = e -> {
                certFile = null;
                certPass = null;
                dispose();
            };
            buttonCancel.addActionListener(c);

            // call onCancel() when cross is clicked
            setDefaultCloseOperation(DISPOSE_ON_CLOSE);
            addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosed(WindowEvent e) {
                    x = false;
                }
            });

            btnBrowseCert.addActionListener(e -> {
                JFileChooser fc = new JFileChooser();
                fc.setDialogTitle("Certificate File");
                fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
                fc.setMultiSelectionEnabled(false);
                if (fc.showOpenDialog(SslDialog.this) == JFileChooser.APPROVE_OPTION) {
                    inpCert.setText(fc.getSelectedFile().getAbsolutePath());
                }
            });

            // call onCancel() on ESCAPE
            contentPane.registerKeyboardAction(c, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);

            pack();
            setBounds(400, 400, 200, 200);
            setVisible(true);
        }

        private void onOK() {
            File f = new File(inpCert.getText());
            if(!f.isFile()) {
                JOptionPane.showMessageDialog(this, "Invalid Certificate File");
                return;
            }
            certFile = inpCert.getText();
            certPass = inpPass.getPassword();

            dispose();
        }
    }
}
