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
package roj.mod;

import roj.concurrent.pool.TaskExecutor;
import roj.config.JSONParser;
import roj.config.ParseException;
import roj.config.data.CEntry;
import roj.config.data.CList;
import roj.config.data.CMapping;
import roj.io.DummyOutputStream;
import roj.io.FileUtil;
import roj.text.CharList;
import roj.text.TextUtil;
import roj.ui.UIUtil;
import roj.util.ByteList;
import roj.util.ByteReader;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import static roj.mod.MCLauncher.*;
import static roj.mod.Shared.*;

/**
 * FMD Install window
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/5/31 21:17
 */
public class FMDInstall extends JFrame {
    static final TaskExecutor waiter = new TaskExecutor();

    public FMDInstall(boolean auto) {
        super("FMD " + VERSION + " 安装");

        JButton terminal = new JButton("退出");
        terminal.addActionListener((e) -> System.exit(0));

        if(!auto) {
            JButton install = new JButton("安装");
            JButton quickInst = new JButton("一键安装");

            install.addActionListener((ev) -> {
                if(!waiter.sleeping())
                    return;
                waiter.execute(FMDInstall::install);
            });

            quickInst.addActionListener((ev) -> {
                if(!waiter.sleeping())
                    return;
                waiter.execute(() -> {
                    FMDInstall.quickInst(null);
                });
            });

            add(quickInst);
            add(install);
            add(terminal);
            add(new JTextArea("1. 安装功能已更新, 更好用了\n" +
                    "2. 但是你仍然需要一些基础知识\n" +
                    "3. 看不懂我也没救了\n" +
                    "4. 为啥有些方法是abc\n" +
                    "  => 因为映射表少了! (不是我干的,它自己就少)"));
        } else {
            add(terminal);
            final JLabel label = new JLabel("自动安装中... 出错请退出重来");
            MCLauncher.waitOut = new PrintStream(DummyOutputStream.INSTANCE) {
                @Override
                public void println(String x) {
                    label.setText(x);
                }
            };
            add(label);
        }

        pack();
        setLayout(new FlowLayout());
        setResizable(!auto);
        setBounds(700, 500, 320, auto ? 80 : 180);
        setVisible(true);

        validate();
    }

    private static void quickInst(String code) {
        if(code == null)
            code = JOptionPane.showInputDialog(activeWindow, "请输入一键安装代码\n示例: '1.16.5|36.0.42||1'", "询问", JOptionPane.QUESTION_MESSAGE);
        if(code == null)
            return;

        List<String> list = TextUtil.splitStringF(new ArrayList<>(4), new CharList(), code, '|', 8, true);
        if(list.size() < 4 || list.size() > 5) {
            error("安装代码无效");
            return;
        }

        MCLauncher.load();

        File mcRoot = getMcRoot();
        if(mcRoot == null)
            return;

        CList versions = MCLauncher.getMcVersionList(MAIN_CONFIG.get("启动器配置").asMap());
        if (versions == null) return;

        CMapping target = null;
        for(CEntry entry : versions) {
            CMapping des = entry.asMap();
            if(des.getString("id").equals(list.get(0))) {
                target = des;
                break;
            }
        }

        if(target == null) {
            error("MC版本不存在");
            return;
        }

        File mcJar = new File(mcRoot, "/versions/" + target.getString("id") + '/' + target.getString("id") + ".jar");
        if(!mcJar.isFile())
            MCLauncher.onClickInstall(target, false);

        File mcJson = new File(mcRoot, "/versions/" + target.getString("id") + '/' + target.getString("id") + ".json");
        if(!MCLauncher.installMinecraftClient(mcRoot, mcJson, false)) {
            error("下载native失败");
            return;
        }

        String mcVer = MCLauncher.config.getString("mc_version");

        CMapping cfgLan = MAIN_CONFIG.get("启动器配置").asMap();
        try {
            CharList out = new CharList(10000);
            ByteReader.decodeUTF(-1, out, new ByteList(FileUtil.downloadFileToMemory(cfgLan.getString("forge版本manifest地址").replace("<mc_ver>", mcVer))));

            versions = JSONParser.parse(out).asList();
        } catch (ParseException | IOException e) {
            error("获取数据出了点错...\n请查看控制台");
            e.printStackTrace();
            return;
        }

        target = null;
        for(CEntry entry : versions) {
            CMapping des = entry.asMap();
            if(des.getString("version").equals(list.get(1))) {
                target = des;
                break;
            }
        }

        if(target == null) {
            error("Forge版本不存在");
            return;
        }

        mcJson = new File(mcRoot, "/versions/" + mcVer + "-forge-" + target.getString("version") + '/' + mcVer + "-forge-" + target.getString("version") + ".json");

        if(!mcJson.isFile())
            MCLauncher.onClickInstall(target, true);

        if(!MCLauncher.installMinecraftClient(mcRoot, mcJson, false)) {
            error("下载forge的native失败");
            return;
        }

        doInstall(mcRoot, mcJson, list.subList(2, list.size()));
    }

    private static File getMcRoot() {
        CMapping cfgGen = MAIN_CONFIG.get("通用").asMap();
        File mcRoot = new File(cfgGen.getString("MC目录"));
        if(!mcRoot.isDirectory()) {
            JFileChooser fileChooser = new JFileChooser(BASE);
            fileChooser.setDialogTitle("选择MC安装位置");
            fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

            int status = fileChooser.showOpenDialog(activeWindow);
            //没有选打开按钮结果提示
            if (status == JFileChooser.APPROVE_OPTION) {
                MAIN_CONFIG.get("通用").asMap().put("MC目录", fileChooser.getSelectedFile().getAbsolutePath());
                return fileChooser.getSelectedFile();
            } else {
                error("用户取消操作.");
                return null;
            }
        }
        return mcRoot;
    }

    private static void install() {
        File mcRoot = getMcRoot();
        if(mcRoot == null)
            return;

        List<File> versions = MCLauncher.findVersions(new File(mcRoot, "/versions/"));

        if(versions.size() < 1) {
            versions = downloadMC(mcRoot);
            if (versions == null) return;
        }

        String[] obj = new String[versions.size()];
        int i = 0;
        for (; i < versions.size(); i++) {
            String s = versions.get(i).getName();
            final int index = s.lastIndexOf('.');
            obj[i] = index == -1 ? s : s.substring(0, index);
        }

        String s = (String) JOptionPane.showInputDialog(activeWindow,"请选择你的MC版本:\n", "询问", JOptionPane.QUESTION_MESSAGE, null, obj, obj[0]);
        if(s == null) {
            versions = downloadMC(mcRoot);
            if (versions == null) return;
        }
        for (i = 0; i < obj.length; i++) {
            if(s == obj[i])
                break;
        }

        doInstall(mcRoot, versions.get(i), null);
    }

    private static List<File> downloadMC(File mcRoot) {
        List<File> versions;
        MCLauncher.load();

        CMapping cfgLan = MAIN_CONFIG.get("启动器配置").asMap();

        CList vList = MCLauncher.getMcVersionList(cfgLan);
        if (vList == null) {
            error("manifest地址配置有误或者无网络连接\n请手动下载MC");
            return null;
        }

        VersionSelect wnd = new VersionSelect(vList.raw(), false);
        synchronized (wnd) {
            try {
                wnd.wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        versions = MCLauncher.findVersions(new File(mcRoot, "/versions/"));

        if(versions.isEmpty())
            return null;

        MCLauncher.installMinecraftClient(mcRoot, versions.get(0), false);

        String mcVer = MCLauncher.config.getString("mc_version");

        try {
            CharList out = new CharList(10000);
            ByteReader.decodeUTF(-1, out, new ByteList(FileUtil.downloadFileToMemory(cfgLan.getString("forge版本manifest地址").replace("<mc_ver>", mcVer))));

            vList = JSONParser.parseIntern(out).asList();
        } catch (ParseException | IOException e) {
            error("获取forge版本数据出错");
            e.printStackTrace();
            return null;
        }

        wnd = new VersionSelect(vList.raw(), true);
        synchronized (wnd) {
            try {
                wnd.wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        List<File> versions2 = MCLauncher.findVersions(new File(mcRoot, "/versions/"));
        versions2.remove(versions.get(0));

        if(versions2.isEmpty())
            return null;

        MCLauncher.installMinecraftClient(mcRoot, versions2.get(0), false);
        return versions;
    }

    private static void doInstall(File mcRoot, File mcJson, List<String> prefilledAnswers) {
        try {
            if(FMDMain.changeVersion(mcRoot, mcJson, new Gui(prefilledAnswers)) != 0) {
                error("安装工程中有错误发生, 请看控制台");
            } else {
                info("安装成功!");
                System.exit(0);
            }
        } catch (Throwable e) {
            e.printStackTrace();
            error("安装工程中有异常错误发生, 请看控制台");
        }
    }

    public static void main(String[] args) {
        UIUtil.systemLook();
        MAIN_CONFIG.size();
        activeWindow = new FMDInstall(args.length > 0);
        activeWindow.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        if(args.length > 0) {
            quickInst(args[0]);
            System.exit(0);
        } else {
            waiter.setName("Async Install");
            waiter.start();
        }
    }

    static final class Gui extends UIWarp {
        String msg;
        String[] objs;

        int call;
        final List<String> ans;

        public Gui(List<String> answer) {
            ans = answer;
        }

        @Override
        boolean getBoolean(String msg) {
            return JOptionPane.showConfirmDialog(activeWindow, "是否需要清除该死的ParametersAreNonnullByDefault之类注解", "询问", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION;
        }

        @Override
        int getNumberInRange(int min, int max) {
            if(ans != null) {
                return Integer.parseInt(ans.get(call++));
            }

            String[] obj = objs;
            if(obj == null) {
                obj = new String[max - min];
                for (int i = min; i < max; i++)
                    obj[i - min] = String.valueOf(i);
            }

            String s;
            do {
                s = (String) JOptionPane.showInputDialog(activeWindow, msg != null ? msg : "请选择", "询问", JOptionPane.QUESTION_MESSAGE, null, obj, obj[0]);
            } while (s == null);

            int i = 0;
            for (; i < obj.length; i++) {
                if(s == obj[i])
                    break;
            }

            this.objs = null;
            this.msg = null;
            return i + min;
        }

        @Override
        String userInput(String msg, boolean optional) {
            if(ans != null) {
                return ans.get(call++);
            }

            String[] obj = objs;
            if(obj == null) {
                String s;
                do {
                    s = JOptionPane.showInputDialog(activeWindow, this.msg != null ? (this.msg.startsWith("$") ? msg + this.msg.substring(1) : this.msg) : msg, "询问", JOptionPane.QUESTION_MESSAGE);
                } while (s == null && !optional);

                this.msg = null;
                return s == null ? "" : s;
            } else {
                String s;
                do {
                    s = (String) JOptionPane.showInputDialog(activeWindow, this.msg != null ? this.msg : msg, "询问", JOptionPane.QUESTION_MESSAGE, null, obj, obj[0]);
                } while (s == null && !optional);

                this.msg = null;
                this.objs = null;
                return s == null ? "" : s;
            }
        }

        @Override
        void stageInfo(int id, boolean... flags) {
            if(ans != null)
                return;

            switch (id) {
                case 0:
                    msg = "$\n如果不要修改请留空";
                    break;
                case 1:
                    msg = "请选择你使用的映射表类别";

                    String[] objs = new String[] {
                            "MCP", "<不可用>", "<不可用>", "自定义", "手动下载"
                    };
                    if(flags[0])
                        objs[1] = "MC官方";
                    if(flags[1])
                        objs[2] = "YARN (WIP)";

                    this.objs = objs;
                    break;
                case 2: {
                    this.objs = new String[] {
                            "MCP", "MC官方"
                    };
                }
                break;
                case 3:
                    info("请看控制台.");
                    break;
                case 4:
                    msg = "\n请输入你要下载的MCP版本\n稳定版(stable-<id>)格式: s-<id>\n快照版(snapshot-<date>)格式: <date>";
                    break;
            }
        }

        @Override
        boolean isConsole() {
            return false;
        }
    }
}
