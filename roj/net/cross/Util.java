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

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.PrintStream;

/**
 * Your description here
 *
 * @author Roj233
 * @version 0.1
 * @since 2021/9/12 5:25
 */
public class Util {
    public static final String[] STATE_NAMES = {"握手", "连接", "运行", "错误", "断开", "结束"};
    public static final int WAIT        = 0;
    public static final int CONNECTED   = 1;
    public static final int ESTABLISHED = 2;
    public static final int ERROR       = 3;
    public static final int DISCONNECT  = 4;
    public static final int FINALIZED   = 5;
    public static final int SHUTDOWN    = 6;

    public static final int TIMEOUT_CONNECT     = 5000;
    public static final int TIMEOUT_ESTABLISHED = 10000;
    public static final int TIMEOUT_TRANSFER    = 15000;

    public static final int PS_HEARTBEAT = 1;
    public static final int PS_CONNECT = 2;
    public static final int PS_DISCONNECT = 3;
    public static final int PS_DATA = 4;
    public static final int PS_SERVER_DATA = 5;
    public static final int PS_ERROR = 6;
    public static final int PS_TIMEOUT = 7;
    public static final int PS_STATE = 8;
    public static final int PS_STATE_SLAVE = 9;
    public static final int PS_SERVER_SLAVE_DATA = 10;
    public static final int PS_LOGON             = 11;
    public static final int PS_SLAVE_CONNECT     = 12;
    public static final int PS_SLAVE_DISCONNECT  = 13;
    public static final int PS_RESET             = 14;
    public static final int PS_KICK_SLAVE        = 15;
    public static final int PS_SERVER_HALLO      = 233;
    public static final int PS_LINK_OVERFLOW = 255;

    public static final String[] ERROR_NAMES = {"IO错误", "密码无效/房间不存在/房间已有房主", "已连接", "未连接", "未知数据包", "服务器关闭", "主机掉线", "系统限制"};
    public static final int PS_ERROR_IO = 0;
    public static final int PS_ERROR_AUTH = 1;
    public static final int PS_ERROR_CONNECTED = 2;
    public static final int PS_ERROR_NOT_CONNECT = 3;
    public static final int PS_ERROR_UNKNOWN_PACKET = 4;
    public static final int PS_ERROR_SHUTDOWN = 5;
    public static final int PS_ERROR_MASTER_DIE = 6;
    public static final int PS_ERROR_SYSTEM_LIMIT = 7;

    public static final String[] RSTATE_NAMES = {"正常", "超时", "IO错误", "无接收者"};
    public static final int PS_STATE_OK = 0;
    public static final int PS_STATE_TIMEOUT = 1;
    public static final int PS_STATE_IO_ERROR = 2;
    public static final int PS_STATE_DISCARD = 3;

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

    static PrintStream out = System.out;
    static String certFile;
    static char[] certPass;

    public static void syncPrint(String msg) {
        if(out == null) return;
        synchronized (out) {
            out.println(msg);
        }
    }

}
