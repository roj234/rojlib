/*
 * This file is a part of MoreItems
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2022 Roj234
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

import roj.net.misc.Shutdownable;
import roj.ui.UIUtil;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.*;
import java.util.concurrent.locks.LockSupport;

import static roj.net.cross.Util.PROTOCOL_VERSION;

/**
 * @author Roj233
 * @since 2022/1/9 5:58
 */
public class GuiChat extends JFrame {
    public interface ChatDispatcher {
        String sendMessage(int id, String message) throws Exception;
    }

    private class MyActionListener extends Thread implements ActionListener {
        public MyActionListener() {
            setName("Gui closer");
            setDaemon(true);
            start();
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            String text1 = cnt.getText();
            String targ = to.getText();
            if (text1.isEmpty() || targ.isEmpty()) {
                JOptionPane.showMessageDialog(GuiChat.this, "请输入内容!", "提示", JOptionPane.WARNING_MESSAGE);
                return;
            }
            try {
                String err = ((ChatDispatcher) s).sendMessage(Integer.parseInt(targ), text1);
                if (err != null) {
                    JOptionPane.showMessageDialog(GuiChat.this, err, "提示", JOptionPane.ERROR_MESSAGE);
                } else {
                    cnt.setText("");
                }
            } catch (Exception e1) {
                JOptionPane.showMessageDialog(GuiChat.this, "消息发送失败了 " + e1.getMessage(), "提示", JOptionPane.ERROR_MESSAGE);
            }
        }

        public void run() {
            while (!s.wasShutdown()) {
                LockSupport.parkNanos(100_000_000);
            }
            LockSupport.parkNanos(1000_000_000);
            System.exit(0);
        }
    }

    private class CloseHandler extends WindowAdapter implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            cnt.setText("");
        }

        @Override
        public void windowClosing(WindowEvent e) {
            s.shutdown();
        }
    }

    final Shutdownable s;
    final JTextField to;
    final JTextArea cnt;

    public GuiChat(String title, Shutdownable s) {
        UIUtil.setLogo(this, "logo.png");

        this.s = s;

        final JPanel panel1 = new JPanel();
        panel1.setLayout(new GridBagLayout());
        panel1.setBorder(
                BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "v" + PROTOCOL_VERSION + " By Roj234", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null,
                                                 null));

        JScrollPane scroll = new JScrollPane();
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.gridwidth = 3;
        gbc.gridheight = 3;
        gbc.weightx = 4.0;
        gbc.weighty = 4.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel1.add(scroll, gbc);
        JTextArea text = cnt = new JTextArea();
        text.setLineWrap(true);
        text.setText("在这里你甚至可以聊天\n在上方输入对方的ID就可以了\n房主的ID是0");
        text.setEditable(true);
        scroll.setViewportView(text);

        JButton btnClear = new JButton();
        btnClear.setText("清空");
        btnClear.addActionListener(new CloseHandler());
        gbc = new GridBagConstraints();
        gbc.gridx = 3;
        gbc.gridy = 4;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel1.add(btnClear, gbc);

        JButton benMute = new JButton();
        benMute.setText("禁言");
        gbc = new GridBagConstraints();
        gbc.gridx = 3;
        gbc.gridy = 5;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel1.add(benMute, gbc);

        JButton btnSend = new JButton();
        btnSend.setText("发送");
        gbc = new GridBagConstraints();
        gbc.gridx = 3;
        gbc.gridy = 6;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel1.add(btnSend, gbc);

        to = new JTextField();
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel1.add(to, gbc);
        setContentPane(panel1);

        setTitle(title);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        btnSend.addActionListener(new MyActionListener());

        addWindowListener(new CloseHandler());
        setBounds(0, 0, 360, 250);
        UIUtil.center(this);
        setVisible(true);
        setResizable(true);
        validate();
    }
}
