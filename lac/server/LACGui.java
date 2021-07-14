/*
 * This file is a part of MI
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
package lac.server;

import roj.concurrent.pool.TaskExecutor;
import roj.ui.UIUtil;

import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import java.awt.*;
import java.io.File;

/**
 * LAC Main Gui
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/7/8 18:42
 */
public class LACGui extends JFrame {
    static final TaskExecutor handler = new TaskExecutor();

    static final String VERSION = "0.1.0beta";

    static JFrame frame;

    public LACGui(File modDir, File modInfo) {
        super("LAC " + VERSION);

        JButton terminal = new JButton("退出");
        terminal.addActionListener((e) -> System.exit(0));

        if(modInfo == null) {
            JButton inst = new JButton("安装");

            inst.addActionListener((ev) -> {
                inst.setEnabled(false);
                handler.execute(LACGui::install);
            });

            add(inst);
        } else{
            JButton btn1 = new JButton("更新");
            JButton btn2 = new JButton("卸载");

            btn1.addActionListener((ev) -> {
                btn1.setEnabled(false);
                btn2.setEnabled(false);
                handler.execute(LACGui::update);
            });

            btn2.addActionListener((ev) -> {
                btn1.setEnabled(false);
                btn2.setEnabled(false);
                handler.execute(LACGui::uninstall);
            });

            add(btn1);
            add(btn2);
        }


            add(terminal);
            add(new JTextArea("..."));

        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        pack();
        setLayout(new FlowLayout());
        setResizable(true);
        setBounds(700, 500, 320, 180);
        setVisible(true);

        validate();
    }

    private static void install() {

    }

    private static void update() {

    }

    private static void uninstall() {

    }

    public static void main(String[] args) {
        UIUtil.systemLook();
        JFileChooser fc = new JFileChooser(new File("."));
        fc.setDialogTitle("选择客户端mod存放位置");
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

        int status = fc.showOpenDialog(frame);
        //没有选打开按钮结果提示
        if (status != JFileChooser.APPROVE_OPTION) {
            error("用户取消操作.");
        } else {
            File modInfo = new File(fc.getSelectedFile(), "mod.info");
            if(!modInfo.isFile()) {
                fc.setDialogTitle("选择mod.info存放位置 (没安装过则取消)");
                fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
                fc.setFileFilter(new FileFilter() {
                    @Override
                    public boolean accept(File f) {
                        return f.getName().equalsIgnoreCase("mod.info");
                    }

                    @Override
                    public String getDescription() {
                        return "mod.info";
                    }
                });

                status = fc.showOpenDialog(frame);
                if (status != JFileChooser.APPROVE_OPTION) {
                    modInfo = null;
                } else {
                    modInfo = fc.getSelectedFile();
                }
            }

            frame = new LACGui(fc.getSelectedFile(), modInfo);
            handler.setName("阿巴阿巴");
            handler.start();
        }
    }

    public static void error(String s) {
        JOptionPane.showMessageDialog(frame, s, "错误", JOptionPane.ERROR_MESSAGE);
    }

    public static void info(String s) {
        JOptionPane.showMessageDialog(frame, s, "提示", JOptionPane.INFORMATION_MESSAGE);
    }
}