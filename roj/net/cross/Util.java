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

import roj.io.NIOUtil;
import roj.net.SecureUtil;
import roj.net.tcp.WrappedSocket;
import roj.text.TextUtil;
import roj.ui.UIUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
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
    static {
        try {
            roj.misc.CpFilter.registerShutdownHook();
        } catch (Error ignored) {}
    }

    public static final int TIMEOUT          = 3000;
    public static final int TIMEOUT_TRANSFER = 6000;


    /**
     * client/host握手
     * {
     * u4 magic;
     * u1 PROTOCOL_VERSION;
     * CHANNEL_TYPE CHANNEL_TYPE;
     * [OPTIONAL when CHANNEL_TYPE == DATA
     *     u8 data_channel_pass
     * ]
     * [OPTIONAL when CHANNEL_TYPE == CONTROL
     *     ROLE role;
     * ]
     * }
     *
     * enum CHANNEL_TYPE {
     *     0=CONTROL, 1=DATA
     * }
     *
     * enum ROLE {
     *     0=Client, 1=Host
     * }
     */
    public static final int MAGIC = 0xAEAEAEAE;
    public static final int PROTOCOL_VERSION = 40;

    public static final int PCN_CONTROL   = 0;
    public static final int PCN_DATA      = 1;

    /**
     * 服务端握手完毕
     * {u1 id}
     */
    public static final int HS_OK        = 200;

    public static final String[] HS_ERROR_NAMES = {"人数过多", "客户端过期", "服务器过期", "系统限制", "超时", "协议错误"};
    public static final int HS_ERR_THROTTLING = 1;
    public static final int HS_ERR_VERSION_LOW = 2;
    public static final int HS_ERR_VERSION_HIGH = 3;
    public static final int HS_ERR_POLICY = 4;
    public static final int HS_ERR_TIMEOUT = 5;
    public static final int HS_ERR_PROTOCOL = 5;

    // region 控制频道数据包
    /**
     * 心跳
     * {u1 id}
     */
    public static final int P_HEARTBEAT = 1;
    /**
     * 客户端登录
     * {
     * u1 id;
     * u1 nameLen;
     * u1 passLen;
     * utf[nameLen] name;
     * utf[passLen] pass;
     * }
     */
    public static final int PS_LOGIN_C = 2;
    /**
     * 客户端登录完毕
     * {
     * u1 id;
     * u1 infoLen;
     * u1 motdLen;
     * u1 portLen;
     * u4 clientId;
     * utf[infoLen] info;
     * utf[motdLen] motd;
     * u2[<N>] ports;
     * }
     */
    public static final int PC_LOGON_C = 3;
    /**
     * 房主登录
     * {
     * u1 id;
     * u1 nameLen;
     * u1 passLen;
     * u1 motdLen;
     * u1 portLen;
     * utf[nameLen] name;
     * utf[passLen] pass;
     * utf[motdLen] motd;
     * u2[<N>] ports;
     * }
     */
    public static final int PS_LOGIN_H = 4;
    /**
     * 房主登录完毕
     * {
     * u1 id;
     * u1 infoLen;
     * utf[infoLen] info;
     * }
     */
    public static final int PC_LOGON_H = 5;
    /**
     * 退出登录（断开连接）
     * {u1 id}
     */
    public static final int P_LOGOUT = 6;
    /**
     * 服务器告知客户端加入
     * {
     * u1 id;
     * u4 ordinal;
     * u2 port;
     * u1 ipLen;
     * u1[ipLen] ip;
     * }
     */
    public static final int PH_CLIENT_LOGIN = 7;
    /**
     * 服务器告知客户端退出
     * {
     * u1 id;
     * u4 ordinal;
     * }
     */
    public static final int PH_CLIENT_LOGOUT = 8;
    /**
     * 房主踢出客户端
     * {
     * u1 id;
     * u4 ordinal;
     * }
     */
    public static final int PS_KICK_CLIENT = 9;
    /**
     * 客户端想开启一个数据频道，并提供加密密码第一部分
     * {
     * u1 id;
     * u1 portId;
     * u1[32] rnd1;
     * }
     */
    public static final int PS_REQUEST_CHANNEL = 10;
    /**
     * A. 客户端想开启一个数据频道，并提供加密密码第一部分后，服务端创建SocketPair并发给host
     * B. 房主同意开启，提供密码第二部分，服务端返回client
     * {
     * u1 id;
     * u1 portId; (only when to host)
     * u1[32] rnd;
     * ORIGIN origin; (only when to host)
     * DATA_CHANNEL data_channel_id;
     * }
     *
     * DATA_CHANNEL {
     *     u4 ordinal;
     *     u4 login_pass; // 之后不传
     * }
     */
    public static final int P_CHANNEL_RESULT  = 11;
    /**
     * 房主同意开启，并返回加密密码第二部分
     * {
     * u1 id;
     * ORIGIN destination;
     * u1[32] rnd;
     * }
     *
     * enum ORIGIN {
     *     -1=SERVER, 0=HOST, else = CLIENT
     * }
     */
    public static final int PS_CHANNEL_OPEN     = 12;
    /**
     * 房主或服务端不同意开启数据频道
     * {
     * u1 id;
     * ORIGIN origin;
     * u1 reasonLen;
     * utf[reasonLen] reason;
     * }
     */
    public static final int P_CHANNEL_OPEN_FAIL = 13;
    /**
     * 任意一方关闭数据频道
     * {
     * u1 id;
     * ORIGIN origin; (only when from server)
     * DATA_CHANNEL.ordinal data_channel_id;
     * }
     */
    public static final int PS_CHANNEL_CLOSE    = 14;
    /**
     * client/host对数据频道操作
     * {
     * u1 id;
     * DATA_CHANNEL.ordinal data_channel_id;
     * OP op;
     * }
     *
     * enum OP {
     *     0=SET_INACTIVE, 1=SET_ACTIVE
     * }
     */
    public static final int P_CHANNEL_OP    = 15;

    public static final int OP_SET_INACTIVE = 0, OP_SET_ACTIVE = 1;
    // endregion

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

    public static final int TIMEOUT_HEART_SERVER = 10000;

    public static final int T_CLIENT_HEARTBEAT_TIME  = 5000;
    public static final int T_CLIENT_HEARTBEAT_RETRY = 200;
    public static final int T_CLIENT_HEARTBEAT_TIMEOUT = 5000;

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
                    KeyStore ks = KeyStore.getInstance(SecureUtil.KEY_FORMAT);
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

    public static PrintStream out;
    public static String certFile;
    public static char[] certPass;

    public static void syncPrint(String msg) {
        if(out == null) return;
        synchronized (out) {
            out.println(msg);
        }
    }

    public static void initSocketPref(Socket client) throws SocketException {
        client.setTcpNoDelay(true);
        client.setTrafficClass(0b10010000);
    }

    public static void handshakeClient(WrappedSocket ch, Long pipeId) throws IOException {
        int wait = TIMEOUT;
        while (!ch.handShake()) {
            LockSupport.parkNanos(50);
            if(wait-- <= 0) {
                throw new IOException("握手超时");
            }
        }

        ByteBuffer buf = ch.buffer();
        buf.clear();
        buf.putInt(MAGIC).put((byte) PROTOCOL_VERSION);
        if (pipeId != null) {
            buf.put((byte) PCN_DATA).putLong(pipeId);
        } else {
            buf.put((byte) PCN_CONTROL);
        }
        buf.flip();
        wait = writeAndFlush(ch, buf, wait);
        buf.clear();

        int read;
        while ((read = ch.read(1)) == 0) {
            LockSupport.parkNanos(50);
            if(wait-- <= 0) {
                throw new IOException("读取超时");
            }
        }
        if(read < 0) throw new IOException("未预料的连接断开");
        read = buf.get(0) & 0xFF;
        try {
            if (read != HS_OK) {
                try {
                    throw new IOException("握手失败: " + HS_ERROR_NAMES[read - 1]);
                } catch (ArrayIndexOutOfBoundsException e) {
                    throw new IOException("协议错误: " + dumpBuffer(buf));
                }
            }
        } finally {
            buf.clear();
        }
    }

    public static int writeAndFlush(WrappedSocket channel, ByteBuffer buf, int T) throws IOException {
        do {
            int w = channel.write(buf);
            if(w < 0)
                return w;
            if (!buf.hasRemaining())
                break;
            LockSupport.parkNanos(20);
            if (T-- <= 0) {
                return -7;
            }
        } while (true);
        while (!channel.dataFlush()) {
            LockSupport.parkNanos(20);
            if (T-- <= 0) {
                return -7;
            }
        }
        return T;
    }

    public static int write1(WrappedSocket channel, byte buf) throws IOException {
        ByteBuffer nx = NIOUtil.getSharedDirectBuffer();
        nx.position(0).limit(1);
        nx.put(0, buf);

        int T = 100;
        int wrote;
        while ((wrote = channel.write(nx)) == 0 && T-- > 0) {
            LockSupport.parkNanos(20);
        }
        while (!channel.dataFlush() && T-- > 0) {
            LockSupport.parkNanos(20);
        }
        nx.clear();
        return wrote < 0 ? wrote : T <= 0 ? -7 : 0;
    }

    public static int write1Direct(WrappedSocket channel, byte buf) throws IOException {
        ByteBuffer nx = NIOUtil.getSharedDirectBuffer();
        nx.position(0).limit(1);
        nx.put(0, buf);

        int w;
        do {
            w = NIOUtil.writeFromNativeBuffer(channel.fd(), nx, NIOUtil.SOCKET_FD);
        } while (w == NIOUtil.INTERRUPTED);
        return w;
    }

    public static boolean readSome(WrappedSocket channel, int count, int time) throws IOException {
        while (count > 0) {
            int r = channel.read(count);
            if (r < 0) return false;
            count -= r;
            LockSupport.parkNanos(100);
            if (time-- <= 0) {
                return false;
            }
        }
        return true;
    }

    public static String dumpBuffer(ByteBuffer rb) {
        int p = rb.position();
        rb.position(0);
        byte[] tmp = new byte[rb.limit()];
        rb.get(tmp).position(p);
        return TextUtil.dumpBytes(tmp);
    }
}
