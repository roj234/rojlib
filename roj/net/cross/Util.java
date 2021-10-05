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

import roj.io.NonblockingUtil;
import roj.net.ssl.SslEngineFactory;
import roj.net.tcp.util.InsecureSocket;
import roj.net.tcp.util.WrappedSocket;
import roj.ui.UIUtil;
import roj.util.ByteList;
import roj.util.FastThreadLocal;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.concurrent.locks.LockSupport;

/**
 * Your description here
 *
 * @author Roj233
 * @version 0.1
 * @since 2021/9/12 5:25
 */
public class Util {
    public static final String[] STATE_NAMES = {"握手", "连接", "运行", "错误", "断开", "结束", "cleanup"};
    public static final int WAIT        = 0;
    public static final int CONNECTED   = 1;
    public static final int ESTABLISHED = 2;
    public static final int ERROR       = 3;
    public static final int DISCONNECT  = 4;
    public static final int FINALIZED   = 5;
    public static final int SHUTDOWN    = 6;

    public static final int TIMEOUT_CONNECT     = 200000;
    public static final int TIMEOUT_TRANSFER    = 200000;

    public static final int PS_HEARTBEAT         = 1;
    public static final int PS_CONNECT           = 2;
    public static final int PS_DISCONNECT        = 3;
    public static final int PS_SERVER_HALLO      = 233;
    public static final int PS_LOGON             = 4;
    public static final int PS_DATA              = 5;
    public static final int PS_SERVER_DATA       = 6;
    public static final int PS_STATE             = 7;
    public static final int PS_KICK_SLAVE        = 8;
    public static final int PS_SERVER_SLAVE_DATA = 9;
    public static final int PS_SLAVE_CONNECT     = 10;
    public static final int PS_SLAVE_DISCONNECT  = 11;
    public static final int PS_RESET             = 12;
    public static final int PS_LINK_OVERFLOW     = 255;

    public static final String[] ERROR_NAMES = {"IO错误", "登录失败(密码无效/房间不存在/房间已有房主)", "已连接", "未连接", "未知数据包", "服务器关闭", "主机掉线", "系统限制", "超时"};
    public static final int PS_ERROR_IO = 0x20;
    public static final int PS_ERROR_AUTH = 0x21;
    public static final int PS_ERROR_CONNECTED = 0x22;
    public static final int PS_ERROR_NOT_CONNECT = 0x23;
    public static final int PS_ERROR_UNKNOWN_PACKET = 0x24;
    public static final int PS_ERROR_SHUTDOWN = 0x25;
    public static final int PS_ERROR_MASTER_DIE = 0x26;
    public static final int PS_ERROR_SYSTEM_LIMIT = 0x27;
    public static final int PS_ERROR_TIMEOUT = 0x28;

    public static final String[] RSTATE_NAMES = {"超时", "IO错误", "无接收者"};
    public static final int PS_STATE_TIMEOUT = 0;
    public static final int PS_STATE_IO_ERROR = 1;
    public static final int PS_STATE_DISCARD = 2;

    public static final int PROTOCOL_VERSION = 3_3;
    public static final ByteList CLIENT_HALLO = new ByteList(new byte[] {
            'A','E','C','L','I','E','N','T','H','A','L','L','O'
    });

    public static final boolean T_SERVER_HEARTBEAT = System.getProperty("ae.noHeartbeat") == null;
    public static final int T_SERVER_HEARTBEAT_INIT = 5000;
    public static final int T_SERVER_HEARTBEAT_RECV = 600000;

    public static final int T_CLIENT_HEARTBEAT_INIT = 5;
    public static final int T_CLIENT_HEARTBEAT_RECV = 800;
    public static final int T_CLIENT_HEARTBEAT_RETRY = 800;
    public static final int T_CLIENT_HEARTBEAT_TIMEOUT = 6000;

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
            buttonCancel.setText("取消");
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
            btnBrowseCert.setText("浏览");
            gbc = new GridBagConstraints();
            gbc.gridx = 2;
            gbc.gridy = 0;
            gbc.weighty = 1.0;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            panel3.add(btnBrowseCert, gbc);
            inpPass = new JPasswordField();
            if(certPass != null)
                inpPass.setText(new String(certPass));
            gbc = new GridBagConstraints();
            gbc.gridx = 1;
            gbc.gridy = 1;
            gbc.weightx = 1.0;
            gbc.weighty = 1.0;
            gbc.anchor = GridBagConstraints.WEST;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            panel3.add(inpPass, gbc);
            final JLabel label1 = new JLabel();
            label1.setText("证书");
            label1.setToolTipText("格式PKCS12");
            gbc = new GridBagConstraints();
            gbc.gridx = 0;
            gbc.gridy = 0;
            gbc.weighty = 1.0;
            gbc.anchor = GridBagConstraints.WEST;
            panel3.add(label1, gbc);
            final JLabel label2 = new JLabel();
            label2.setText("密码");
            gbc = new GridBagConstraints();
            gbc.gridx = 0;
            gbc.gridy = 1;
            gbc.weighty = 1.0;
            gbc.anchor = GridBagConstraints.WEST;
            panel3.add(label2, gbc);
            inpCert = new JTextField();
            if(certFile != null)
                inpCert.setText(certFile);
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
                fc.setDialogTitle("选择证书");
                fc.setCurrentDirectory(new File("."));
                fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
                fc.setMultiSelectionEnabled(false);
                if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                    inpCert.setText(fc.getSelectedFile().getAbsolutePath());
                }
            });

            // call onCancel() on ESCAPE
            contentPane.registerKeyboardAction(c, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);

            pack();
            setBounds(0, 0, 200, 120);
            UIUtil.center(this);
            setVisible(true);
            setResizable(false);
        }

        private void onOK() {
            if(inpCert.getText().equals("")) {
                certFile = null;
                certPass = null;
            } else {
                File f = new File(inpCert.getText());
                if (!f.isFile()) {
                    JOptionPane.showMessageDialog(this, "无效的证书");
                    return;
                }
                certFile = inpCert.getText();
                certPass = inpPass.getPassword();

                try {
                    KeyStore ks = KeyStore.getInstance(SslEngineFactory.KEY_FORMAT);
                    try (InputStream in = new FileInputStream(certFile)) {
                        ks.load(in, certPass);
                    }
                } catch (GeneralSecurityException | IOException e) {
                    e.printStackTrace();
                    JOptionPane.showMessageDialog(this, "无效的证书");
                    return;
                }
            }

            dispose();
        }
    }

    static PrintStream out;
    static String certFile;
    static char[] certPass;

    public static void syncPrint(String msg) {
        if(out == null) return;
        synchronized (out) {
            out.println(msg);
        }
    }

    static void initSocketPref(Socket client) throws SocketException {
        client.setTcpNoDelay(true);
        client.setTrafficClass(0b10010000);
    }

    static int handshakeClient(WrappedSocket channel) throws IOException {
        int wait = TIMEOUT_CONNECT;
        while (!channel.handShake()) {
            LockSupport.parkNanos(200);
            if(wait-- <= 0) {
                return 1;
            }
        }

        ByteList buf = channel.buffer();
        buf.clear();
        buf.addAll(CLIENT_HALLO.list);
        buf.add((byte) PROTOCOL_VERSION);
        wait = writeAndFlush(channel, buf, wait);
        buf.clear();

        int read;
        while ((read = channel.read(1)) == 0) {
            LockSupport.parkNanos(1000);
            if(wait-- <= 0) {
                return 3;
            }
        }
        if(read < 0)
            return 4;
        if(buf.getU(0) != PS_SERVER_HALLO) {
            if (buf.getU(0) == PS_LINK_OVERFLOW) {
                syncPrint("服务端报告: 超过最大连接数");
                return 4;
            } else
                throw new SocketException("协议错误: " + buf);
        }
        buf.clear();
        return 0;
    }

    static int writeAndFlush(WrappedSocket channel, ByteList buf, int timeout) throws IOException {
        Thread t = Thread.currentThread();
        do {
            int w = channel.write(buf);
            if(w < 0)
                return w;
            if (buf.writePos() == buf.pos()) {
                channel.dataFlush();
                break;
            }
            LockSupport.parkNanos(20);
            if(t.isInterrupted())
                return NonblockingUtil.INTERRUPTED;
            if (timeout-- <= 0) {
                return -7;
            }
        } while (true);
        return timeout;
    }

    private static final FastThreadLocal<ByteList> FCG = new FastThreadLocal<>();
    static int writeEx(WrappedSocket channel, byte buf) throws IOException {
        int state;
        if(channel.getClass() == InsecureSocket.class) {
            ByteBuffer nx = NonblockingUtil.getNativeDirectBuffer();
            nx.position(0).limit(1);
            nx.put(buf).position(0);
            int timeout = 10;
            while ((state = NonblockingUtil.writeFromNativeBuffer(channel.fd(), nx, NonblockingUtil.SOCKET_FD)) == 0 && timeout > 0) {
                LockSupport.parkNanos(20);
                timeout--;
            }
            return state < 0 ? state : timeout <= 0 ? -7 : 0;
        } else {
            ByteList tmp = FCG.get();
            if(tmp == null)
                FCG.set(tmp = new ByteList(1));
            tmp.clear();
            tmp.add(buf);
            return (state = writeAndFlush(channel, tmp, 200)) > 0 ? 0 : state;
        }
    }
}
