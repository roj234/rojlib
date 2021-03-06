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

import roj.concurrent.TaskExecutor;
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
 * @since 2021/5/31 21:17
 */
public class FMDInstall extends JFrame {
    static final TaskExecutor waiter = new TaskExecutor();

    public FMDInstall(boolean auto) {
        super("FMD " + VERSION + " ??????");

        JButton terminal = new JButton("??????");
        terminal.addActionListener((e) -> System.exit(0));

        if(!auto) {
            JButton install = new JButton("??????");
            JButton quickInst = new JButton("????????????");

            install.addActionListener((ev) -> {
                if(waiter.busy()) return;
                waiter.execute(FMDInstall::install);
            });

            quickInst.addActionListener((ev) -> {
                if(waiter.busy()) return;
                waiter.execute(() -> {
                    FMDInstall.quickInst(null);
                });
            });

            add(quickInst);
            add(install);
            add(terminal);
            add(new JTextArea("1. ?????????????????????, ????????????\n" +
                    "2. ???????????????????????????????????????\n" +
                    "3. ????????????????????????\n" +
                    "4. ?????????????????????abc\n" +
                    "  => ?????????????????????! (???????????????,???????????????)"));
        } else {
            add(terminal);
            final JLabel label = new JLabel("???????????????... ?????????????????????");
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
            code = JOptionPane.showInputDialog(activeWindow, "???????????????????????????\n??????: '1.16.5|36.0.42||1'", "??????", JOptionPane.QUESTION_MESSAGE);
        if(code == null)
            return;

        List<String> list = TextUtil.split(new ArrayList<>(4), code, '|', 8, true);
        if(list.size() < 4 || list.size() > 5) {
            error("??????????????????");
            return;
        }

        MCLauncher.load();

        File mcRoot = getMcRoot();
        if(mcRoot == null)
            return;

        CList versions = MCLauncher.getMcVersionList(CONFIG.get("???????????????").asMap());
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
            error("MC???????????????");
            return;
        }

        File mcJar = new File(mcRoot, "/versions/" + target.getString("id") + '/' + target.getString("id") + ".jar");
        if(!mcJar.isFile())
            MCLauncher.onClickInstall(target, false);

        File mcJson = new File(mcRoot, "/versions/" + target.getString("id") + '/' + target.getString("id") + ".json");
        if(!MCLauncher.installMinecraftClient(mcRoot, mcJson, false)) {
            error("??????native??????");
            return;
        }

        String mcVer = MCLauncher.config.getString("mc_version");

        CMapping cfgLan = CONFIG.get("???????????????").asMap();
        try {
            CharList out = new CharList(10000);
            ByteList.decodeUTF(-1, out, FileUtil.downloadFileToMemory(cfgLan.getString("forge??????manifest??????").replace("<mc_ver>", mcVer)));

            versions = JSONParser.parse(out).asList();
        } catch (ParseException | IOException e) {
            error("????????????????????????...\n??????????????????");
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
            error("Forge???????????????");
            return;
        }

        mcJson = new File(mcRoot, "/versions/" + mcVer + "-forge-" + target.getString("version") + '/' + mcVer + "-forge-" + target.getString("version") + ".json");

        if(!mcJson.isFile())
            MCLauncher.onClickInstall(target, true);

        if(!MCLauncher.installMinecraftClient(mcRoot, mcJson, false)) {
            error("??????forge???native??????");
            return;
        }

        doInstall(mcRoot, mcJson, list.subList(2, list.size()));
    }

    private static File getMcRoot() {
        CMapping cfgGen = CONFIG.get("??????").asMap();
        File mcRoot = new File(cfgGen.getString("MC??????"));
        if(!mcRoot.isDirectory()) {
            JFileChooser fileChooser = new JFileChooser(BASE);
            fileChooser.setDialogTitle("??????MC????????????");
            fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

            int status = fileChooser.showOpenDialog(activeWindow);
            //?????????????????????????????????
            if (status == JFileChooser.APPROVE_OPTION) {
                CONFIG.get("??????").asMap().put("MC??????", fileChooser.getSelectedFile().getAbsolutePath());
                return fileChooser.getSelectedFile();
            } else {
                error("??????????????????.");
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

        String s = (String) JOptionPane.showInputDialog(activeWindow,"???????????????MC??????:\n", "??????", JOptionPane.QUESTION_MESSAGE, null, obj, obj[0]);
        if(s == null) {
            versions = downloadMC(mcRoot);
            if (versions == null) return;
        }
        for (i = 0; i < obj.length; i++) {
            if(obj[i].equals(s))
                break;
        }

        doInstall(mcRoot, versions.get(i), null);
    }

    private static List<File> downloadMC(File mcRoot) {
        List<File> versions;
        MCLauncher.load();

        CMapping cfgLan = CONFIG.get("???????????????").asMap();

        CList vList = MCLauncher.getMcVersionList(cfgLan);
        if (vList == null) {
            error("manifest???????????????????????????????????????\n???????????????MC");
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
            ByteList.decodeUTF(-1, out, FileUtil.downloadFileToMemory(cfgLan.getString("forge??????manifest??????").replace("<mc_ver>", mcVer)));

            vList = JSONParser.parse(out, JSONParser.INTERN).asList();
        } catch (ParseException | IOException e) {
            error("??????forge??????????????????");
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
                error("??????????????????????????????, ???????????????");
            } else {
                info("????????????!");
                System.exit(0);
            }
        } catch (Throwable e) {
            e.printStackTrace();
            error("????????????????????????????????????, ???????????????");
        }
    }

    public static void main(String[] args) {
        UIUtil.systemLook();
        CONFIG.size();
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
            return JOptionPane.showConfirmDialog(activeWindow, "???????????????????????????ParametersAreNonnullByDefault????????????", "??????", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION;
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
                s = (String) JOptionPane.showInputDialog(activeWindow, msg != null ? msg : "?????????", "??????", JOptionPane.QUESTION_MESSAGE, null, obj, obj[0]);
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
                    s = JOptionPane.showInputDialog(activeWindow, this.msg != null ? (this.msg.startsWith("$") ? msg + this.msg.substring(1) : this.msg) : msg, "??????", JOptionPane.QUESTION_MESSAGE);
                } while (s == null && !optional);

                this.msg = null;
                return s == null ? "" : s;
            } else {
                String s;
                do {
                    s = (String) JOptionPane.showInputDialog(activeWindow, this.msg != null ? this.msg : msg, "??????", JOptionPane.QUESTION_MESSAGE, null, obj, obj[0]);
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
                    msg = "$\n???????????????????????????";
                    break;
                case 1:
                    msg = "????????????????????????????????????";

                    String[] objs = new String[] {
                            "MCP", "<?????????>", "<?????????>", "?????????", "????????????"
                    };
                    if(flags[0])
                        objs[1] = "MC??????";
                    if(flags[1])
                        objs[2] = "YARN (WIP)";

                    this.objs = objs;
                    break;
                case 2: {
                    this.objs = new String[] {
                            "MCP", "MC??????"
                    };
                }
                break;
                case 3:
                    info("???????????????.");
                    break;
                case 4:
                    msg = "\n????????????????????????MCP??????\n?????????(stable-<id>)??????: s-<id>\n?????????(snapshot-<date>)??????: <date>";
                    break;
            }
        }

        @Override
        boolean isConsole() {
            return false;
        }
    }
}
