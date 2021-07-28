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

import roj.collect.MyHashMap;
import roj.collect.MyHashSet;
import roj.collect.SimpleList;
import roj.collect.TrieTreeSet;
import roj.concurrent.TaskHandler;
import roj.concurrent.WaitingIOFuture;
import roj.concurrent.pool.TaskExecutor;
import roj.concurrent.task.AbstractCalcTask;
import roj.concurrent.task.ITask;
import roj.concurrent.task.ITaskNaCl;
import roj.config.JSONParser;
import roj.config.ParseException;
import roj.config.data.CEntry;
import roj.config.data.CList;
import roj.config.data.CMapping;
import roj.config.word.AbstLexer;
import roj.io.BOMInputStream;
import roj.io.FileUtil;
import roj.io.IOUtil;
import roj.io.down.IProgressHandler;
import roj.io.down.STDProgress;
import roj.math.Version;
import roj.text.CharList;
import roj.text.TextUtil;
import roj.ui.CmdUtil;
import roj.ui.UIUtil;
import roj.util.ByteList;
import roj.util.ByteReader;
import roj.util.ByteWriter;
import roj.util.OS;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;
import java.util.jar.Manifest;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static roj.mod.Shared.*;

/**
 * Roj234's Minecraft Launcher
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/5/31 21:17
 */
public class MCLauncher extends JFrame {
    @Nullable
    static JFrame activeWindow;

    public static void main(String[] args) {
        UIUtil.systemLook();
        load();

        CMapping cfgGen = MAIN_CONFIG.get("通用").asMap();

        File mcRoot = new File(cfgGen.getString("MC目录"));

        if(!mcRoot.isDirectory() && !mcRoot.mkdirs()) {
            error("无法创建minecraft目录");
            return;
        }

        activeWindow = new MCLauncher();
        activeWindow.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    }

    public MCLauncher() {
        super("Roj234的启动器 " + VERSION);
        UIUtil.setLogo(this, "FMD_logo.png");

        JButton button = new JButton("启动");
        button.addActionListener(MCLauncher::runClient);
        add(button);

        button = new JButton("调试");
        button.addActionListener(MCLauncher::debug);
        add(button);

        button = new JButton("选择核心");
        button.addActionListener(MCLauncher::selectVersion);
        button.setToolTipText("选择MC版本");
        add(button);

        button = new JButton("下载中心");
        button.addActionListener(MCLauncher::download);
        button.setToolTipText("下载Minecraft和Forge");
        add(button);

        button = new JButton("配置");
        button.addActionListener(MCLauncher::config);
        button.setToolTipText("配置<当前版本>的玩家名等");
        add(button);

        button = new JButton("终止");
        button.addActionListener((e) -> {
            if (task != null && !task.isDone()) {
                task.cancel(true);
                info("已结束");
            } else {
                error("MC没有启动");
            }
        });
        button.setToolTipText("终止MC进程");
        add(button);

        pack();
        setLayout(new FlowLayout());
        setResizable(true);
        setBounds(700, 500, 320, 100);
        setVisible(true);

        validate();
    }

    // region Select version

    private static void selectVersion(ActionEvent event) {
        if (checkMCRun()) return;

        CMapping cfgGen = MAIN_CONFIG.get("通用").asMap();

        File mcRoot = new File(cfgGen.getString("MC目录"));
        if(!mcRoot.isDirectory() && !mcRoot.mkdirs()) {
            error("无法创建minecraft目录");
            return;
        }

        List<File> versions = findVersions(new File(mcRoot, "/versions/"));

        File mcJson;
        if(versions.size() < 1) {
            error("没有找到版本, 请下载");
            return;
        } else {
            String[] obj = new String[versions.size()];
            int i = 0;
            for (; i < versions.size(); i++) {
                String s = versions.get(i).getName();
                final int index = s.lastIndexOf('.');
                obj[i] = index == -1 ? s : s.substring(0, index);
            }

            String cv = config.getString("mc_version");
            for (i = 0; i < obj.length; i++) {
                if(obj[i].equals(cv)) {
                    break;
                }
            }
            if(i >= obj.length)
                i = 0;

            String s = (String) JOptionPane.showInputDialog(activeWindow,"请选择你的MC版本:\n", "询问", JOptionPane.QUESTION_MESSAGE, null, obj, obj[i]);
            if(s == null)
                return;
            for (i = 0; i < obj.length; i++) {
                if(s == obj[i])
                    break;
            }
            mcJson = versions.get(i);

            /*if(!launcher_conf.getBool("我看过提示二了") && mcJson.getName().contains("forge")) {
                info("若您【选择】forge版本之前并未【选择】过对应原版\n请先【选择】原版再【选择】forge，否则可能导致MC无法启动\n是的，大部分启动器都是启动的时候检查native等，但是FMD不是\n本消息只显示一次");
                launcher_conf.put("我看过提示二了", true);
                save();
            }*/
        }

        installMinecraftClient(mcRoot, mcJson, true);
    }

    public static List<File> findVersions(File file) {
        return findVersions(file, new ArrayList<>());
    }

    private static List<File> findVersions(File file, List<File> jsons) {
        File[] files = file.listFiles();
        if(files != null) {
            for (File file1 : files) {
                if (file1.isDirectory()) {
                    File file2 = new File(file1, "/" + file1.getName() + ".json");
                    if (file2.exists())
                        jsons.add(file2);
                }
            }
        }
        return jsons;
    }

    // endregion
    // region waiter

    private static TaskExecutor waiter;
    public static final int TIMEOUT_TIME = 60000;
    public static PrintStream waitOut;

    private static void waitFor(List<ITask> tasks) {
        if(waiter == null) {
            waiter = new TaskExecutor();
            waiter.setDaemon(true);
            waiter.start();
        }

        long str = System.currentTimeMillis();

        if(waitOut == null) {
            waitOut = System.out;
        }

        if(tasks == null) {
            if (parallel.taskLength() > 0) {
                waiter.pushTask(new Waiter(waitOut));

                waitFin(str);
            }
        } else {
            int once = MAIN_CONFIG.get("通用").asMap().getInteger("最大线程数");
            int remain = tasks.size();

            waiter.pushTask(new Waiter(waitOut));

            boolean skip = false;
            while(remain > 0) {
                CmdUtil.info("余量: " + remain);

                int t = Math.min(once, remain);
                for (int j = 0; j < t; j++) {
                    parallel.pushTask(tasks.get(--remain));
                }

                while (parallel.taskLength() > once / 2) {
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    if(!skip && System.currentTimeMillis() - str > TIMEOUT_TIME) {
                        int r = ifBreakWait();
                        if(r == -1) return;
                        if(r == 1)
                            skip = true;
                        str = System.currentTimeMillis();
                    }
                }
            }

            waitFin(str);
        }
    }

    private static void waitFin(long str) {
        boolean skip = false;
        do {
            parallel.waitUntilFinish(TIMEOUT_TIME - (System.currentTimeMillis() - str));
            if (parallel.taskLength() <= 0) return;

            if(!skip) {
                int r = ifBreakWait();
                if(r == -1) return;
                if(r == 1)
                    skip = true;
                str = System.currentTimeMillis();
            }

        } while (true);
    }

    private static int ifBreakWait() {
        if(activeWindow == null)
            return 1;

        Object[] options = { "继续等", "不等了", "不再提示" };
        int m = JOptionPane.showOptionDialog(activeWindow, "等了一分钟了，还没下完", "询问", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);

        switch (m) {
            case JOptionPane.YES_OPTION: // con
                return 0;
            default:
            case JOptionPane.NO_OPTION: // not
                parallel.clearTasks();
                FileUtil.ioPool.clearTasks();
                return -1;
            case JOptionPane.CANCEL_OPTION: // ignore
                return 1;
        }
    }

    static final class Waiter implements ITaskNaCl {
        private final PrintStream out;

        public Waiter(PrintStream out) {
            this.out = out;
        }

        @Override
        public void calculate(Thread thread) {
            try {
                Thread.sleep(1500);
            } catch (InterruptedException ignored) {}
            out.println("剩余任务: " + parallel.taskLength());
        }

        @Override
        public boolean isDone() {
            return false;
        }

        @Override
        public boolean continueExecuting() {
            return parallel.taskLength() > 0;
        }
    }

    // endregion
    // region download MC

    private static void download(ActionEvent event) {
        CMapping cfgGen = MAIN_CONFIG.get("通用").asMap();
        CMapping cfgLan = MAIN_CONFIG.get("启动器配置").asMap();

        File mcRoot = new File(cfgGen.getString("MC目录"));
        if(!mcRoot.isDirectory() && !mcRoot.mkdirs()) {
            error("无法创建minecraft目录");
        }

        Object[] options = { "MC", config.getString("mc_version") + "的Forge" };
        int m = JOptionPane.showOptionDialog(activeWindow, "你下嘛？", "询问", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
        if(m == JOptionPane.YES_OPTION) {
            CList versions = getMcVersionList(cfgLan);
            if (versions == null) return;

            new VersionSelect(versions.raw(), false);
        } else if(m == JOptionPane.NO_OPTION) {
            if(config.getString("mc_version").contains("forge")) {
                if(JOptionPane.showConfirmDialog(activeWindow, "似乎这个版本装了forge,继续吗?", "询问", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null) != JOptionPane.YES_OPTION)
                    return;
            }

            if(!config.containsKey("mc_conf")) {
                error("请选择一个MC版本");
                return;
            }

            String mcVer = config.getString("mc_version");

            CList versions;
            try {
                CharList out = new CharList(10000);
                ByteReader.decodeUTF(-1, out, new ByteList(FileUtil.downloadFileToMemory(cfgLan.getString("forge版本manifest地址").replace("<mc_ver>", mcVer))));

                versions = JSONParser.parseIntern(out).asList();
            } catch (ParseException | IOException e) {
                error("获取数据出了点错...\n请查看控制台");
                e.printStackTrace();
                return;
            }

            new VersionSelect(versions.raw(), true);
        }
    }

    static CList cache_mc_versions;
    public static CList getMcVersionList(CMapping cfgLan) {
        CList versions = cache_mc_versions;
        if(versions == null) {
            try {
                CharList out = new CharList(100000);
                ByteReader.decodeUTF(-1, out, new ByteList(FileUtil.downloadFileToMemory(cfgLan.getString("mc版本manifest地址"))));

                cache_mc_versions = versions = JSONParser.parseIntern(out).asMap().get("versions").asList();
            } catch (ParseException | IOException e) {
                error("获取数据出了点错...\n请查看控制台");
                e.printStackTrace();
                return null;
            }
        }
        return versions;
    }

    // region shared

    public static int completeAssets(String mirrorA, String mirrorB, CMapping mc_conf, CMapping mcJson) throws IOException {
        if(mcJson == null) {
            String jar = mc_conf.getString("jar");
            try {
                mcJson = JSONParser.parse(IOUtil.readAsUTF(new FileInputStream(jar.substring(0, jar.lastIndexOf('.')) + ".json"))).asMap();
            } catch (ParseException e) {
                throw new IOException("无效的MC配置json", e);
            }
        }

        File assets = new File(mc_conf.getString("assets_root") + File.separatorChar + "assets");
        if(!assets.isDirectory() && !assets.mkdirs()) {
            throw new IOException("无法创建assets目录");
        }
        File index = new File(assets, "/indexes/");
        if(!index.isDirectory() && !index.mkdir()) {
            throw new IOException("无法创建assets/indexes目录");
        }
        index = new File(index, '/' + mc_conf.getString("assets") + ".json");
        if(!index.isFile()) {
            downloadMinecraftFile(mcJson.get("assetIndex").asMap(), index, mirrorA, true);
        }
        CMapping objects;
        try {
            objects = JSONParser.parse(IOUtil.readAsUTF(new FileInputStream(index))).asMap().get("objects").asMap();
        } catch (ParseException e) {
            throw new IOException("无效的索引json", e);
        }

        assets = new File(assets, "/objects/");
        CharList tmp = new CharList();

        STDProgress.DECR_LOGS = true;
        STDProgress.DECR_LOGS_2 = true;

        MyHashSet<String> hashes = new MyHashSet<>();
        ArrayList<ITask> missingEntries = new ArrayList<>();
        for (Map.Entry<String, CEntry> entry : objects.entrySet()) {
            CMapping val = entry.getValue().asMap();
            String hash = val.getString("hash");
            if(!hashes.add(hash)) // 跳过重复的文件
                continue;
            String url = tmp.append(hash, 0, 2).append('/').append(hash).toString();
            tmp.clear();
            File asset = new File(assets, url);
            if(!asset.isFile() || asset.length() != val.getInteger("size")) {
                if(asset.isFile() && ! asset.delete()) {
                    CmdUtil.error("无法删除错误文件 " + asset);
                    continue;
                }
                File pa = asset.getParentFile();
                if(!pa.isDirectory() && !pa.mkdirs()) {
                    CmdUtil.error("无法创建文件 " + pa);
                    continue;
                }

                val.put("sha1", val.getString("hash"));
                val.remove("size");
                val.remove("hash");
                val.put("url", mirrorB + url);
                missingEntries.add(downMcFileAsTask(val, asset, mirrorB));
            }
        }

        if(!missingEntries.isEmpty())
            waitFor(missingEntries);

        STDProgress.DECR_LOGS_2 = false;
        STDProgress.DECR_LOGS = false;

        return missingEntries.size();
    }

    public static boolean installMinecraftClient(File mcRoot, File mcJson, boolean doAssets) {
        CMapping cfgGen = MAIN_CONFIG.get("通用").asMap();
        CMapping cfgLan = MAIN_CONFIG.get("启动器配置").asMap();

        Object[] result;
        CMapping jsonDesc, mc_conf;
        try {
            File mcNative = new File(mcJson.getParentFile(), "/$natives/");
            result = getRunConf(mcRoot, mcJson, mcNative, cfgGen);
            config.put("mc_conf", mc_conf = (CMapping) result[0]);
            mc_conf.put("native_path", mcNative.getAbsolutePath());
            jsonDesc = (CMapping) result[3];
            config.put("mc_version", jsonDesc.getString("id"));

            save();
        } catch (IOException e) {
            error("获取RunConfig出了点错...\n请查看控制台");
            e.printStackTrace();
            return false;
        }

        waitFor(null);

        boolean fl = result[4] == Boolean.TRUE;
        if (fl) CmdUtil.warning("高版本警告");
        config.put("high_ver", fl);

        if(!doAssets)
            return true;

        if (!cfgLan.getString("assets地址").isEmpty()) {
            CmdUtil.info("补全assets...");
            try {
                int count = completeAssets(cfgGen.getBool("下载MC相关文件使用镜像") ? cfgGen.getString("镜像地址") : null, cfgLan.getString("assets地址"), mc_conf, jsonDesc);
                CmdUtil.success("assets补全完毕, " + count);
            } catch (IOException e) {
                error("asset补全出了点错...\n请查看控制台");
                e.printStackTrace();
            }
        }
        return true;
    }

    public static void onClickInstall(CMapping map, boolean fg) {
        CMapping cfgGen = MAIN_CONFIG.get("通用").asMap();

        if(!fg) {
            String name = map.getString("id");
            File versionPath = new File(cfgGen.getString("MC目录") + "/versions/" + name + '/');
            if (!versionPath.isDirectory() && !versionPath.mkdirs()) {
                error("无法创建version/id目录");
                return;
            }
            String url = map.getString("url");
            if (cfgGen.getBool("下载MC相关文件使用镜像")) {
                url = url.replace("launchermeta.mojang.com", cfgGen.getString("镜像地址"));
            }
            File json = new File(versionPath, name + ".json");
            CMapping download;
            File target = new File(versionPath, name + ".json.tmp");
            try {
                if(!json.isFile()) {
                    FileUtil.downloadFileAsync(url, target).waitFor();
                }
                download = JSONParser.parse(IOUtil.readAsUTF(new FileInputStream(json.isFile() ? json : target))).asMap().get("downloads").asMap().get("client").asMap();
            } catch (IOException | ParseException e) {
                error("下载/解析版本json出了点错...\n请查看控制台");
                e.printStackTrace();
                return;
            }

            File mcFile = new File(versionPath, name + ".jar");

            try {
                downloadMinecraftFile(download, mcFile, cfgGen.getBool("下载MC相关文件使用镜像") ? cfgGen.getString("镜像地址") : null, true);
            } catch (IOException e) {
                error("下载版本jar出了点错...\n请查看控制台");
                e.printStackTrace();
                return;
            }

            if(!json.isFile() && !target.renameTo(json)) {
                error("版本json没法修改");
                return;
            }

            if(activeWindow instanceof MCLauncher)
                info(name + "下载成功.");
        } else {
            CharList tmp0 = new CharList(20).append(config.getString("mc_version")).append('-').append(map.getString("version"));
            if(map.containsKey("branch", roj.config.data.Type.STRING)) {
                tmp0.append('-').append(map.getString("branch"));
            }

            CharList tmp1 = new CharList(50).append(cfgGen.getString("协议")).append(cfgGen.getString("forge地址")).append("/net/minecraftforge/forge/");
            tmp1.append(tmp0);
            tmp1.append("/forge-").append(tmp0);

            String url = tmp1.append("-installer.jar").toString();

            CMapping found = null;
            for (CEntry entry : map.get("files").asList()) {
                CMapping ent = entry.asMap();
                if(ent.getString("category").equals("installer")) {
                    found = ent;
                    break;
                }
            }
            String md5 = null;
            if(found == null || !found.containsKey("hash"))
                CmdUtil.warning("没有找到installer类别,无法校验文件MD5, raw: " + map.toJSON());
            else
                md5 = found.getString("hash");

            File tmpFile = new File(TMP_DIR, tmp0.insert(0, "forge-").append("-installer.jar").toString());

            try {
                downloadAndVerifyMD5(url, md5, tmpFile, true);
            } catch (IOException e) {
                error("Forge下载失败! \n请看控制台");
                e.printStackTrace();
                return;
            }

            if(activeWindow instanceof MCLauncher)
                info("Forge由广告链接支持,若自动化了它的下载及安装\n请考虑点这里赞助 https://www.patreon.com/LexManos/\n点击确定继续安装");

            try {
                if (installForge(tmpFile, map.getString("version"), config.getBool("high_ver"))) {
                    waitFor(null);
                    if(activeWindow instanceof MCLauncher)
                        info("安装成功,您可以手动删除tmp目录下的\nfg-xxx-tmp-lib目录和xxx-fgInst-tmp.jar");
                }
            } catch (IOException e) {
                error("Forge安装失败! \n请看控制台");
                e.printStackTrace();
            }
        }
    }

    private static boolean installForge(File fgInstaller, String fgVersion, boolean highVersion) throws IOException {
        CMapping cfgGen = MAIN_CONFIG.get("通用").asMap();

        File mcRoot = new File(cfgGen.getString("MC目录"));
        if(!mcRoot.isDirectory() && !mcRoot.mkdirs()) {
            error("无法创建minecraft目录");
            return false;
        }

        final String ver = config.getString("mc_version") + "-forge-" + fgVersion;
        File installDir = new File(mcRoot, "/versions/" + ver + '/');
        if(installDir.isDirectory()) {
            //error("forge可能已安装,若之前安装失败,请手动删除目录 " + installDir.getAbsolutePath());
            //return false;
        } else if(!installDir.mkdirs()) {
            error("forge存放目录创建失败");
            return false;
        }

        ZipFile zf = new ZipFile(fgInstaller);

        ZipEntry instProf = zf.getEntry("install_profile.json");
        if(instProf == null) {
            zf.close();
            error("找不到安装配置 install_profile.json");
            return false;
        }

        CMapping instConf;
        try {
            instConf = JSONParser.parse(IOUtil.readAsUTF(zf.getInputStream(instProf))).asMap();
        } catch (ParseException e) {
            e.printStackTrace();
            zf.close();
            error("安装配置解析失败");
            return false;
        }

        if(instConf.containsKey("icon")) {
            if (!instConf.getString("minecraft").equals(config.getString("mc_version"))) {
                CmdUtil.warning("版本可能不匹配! " + instConf.getString("minecraft") + " - " + config.getString("mc_version"));
            }

            ZipEntry verJson = zf.getEntry(instConf.getString("json").substring(1));
            if (verJson == null) {
                zf.close();
                error("找不到版本配置 " + instConf.getString("json"));
                return false;
            }

            ByteList bl = new ByteList();
            // 没安装完
            final File mcJsonTmp = new File(installDir, ver + ".json.tmp");
            try (FileOutputStream fos = new FileOutputStream(mcJsonTmp)) {
                final InputStream in = zf.getInputStream(verJson);
                bl.readStreamArrayFully(in).writeToStream(fos);
                in.close();
                bl.clear();
            }

            File mcLibPath = new File(mcRoot, "/libraries/");
            if(instConf.get("processors").asList().size() > 0 || "true".equals(System.getProperty("fmd.forceDownloadForgeExternalLibraries"))) {
                CmdUtil.info("开始下载Forge安装所需jar...");

                File libraryPath = new File(TMP_DIR, "fg-" + ver + "-tmp-lib");
                if (!libraryPath.isDirectory() && !libraryPath.mkdir()) {
                    CmdUtil.warning("无法创建临时目录 " + libraryPath.getAbsolutePath());
                }

                Map<String, Version> versions = new MyHashMap<>();
                Map<String, String> libraries = new MyHashMap<>();
                final String libUrl = cfgGen.getString("libraries地址");
                for (CEntry entry : instConf.get("libraries").asList()) {
                    final CMapping data = entry.asMap();
                    if (data.getString("name").startsWith("net.minecraftforge:forge:") && data.get("downloads").asMap().get("artifact").asMap().getString("url").isEmpty())
                        continue;

                    detectLibrary(data, versions, libraryPath, null, libraries, libUrl, parallel);
                }

                String tmp = libraryPath.getAbsolutePath() + File.separatorChar;
                CharList cpTmp = new CharList(1000);
                for (String lib : libraries.values()) {
                    cpTmp.append(tmp).append(lib).append(File.pathSeparatorChar);
                }
                int cpLen = cpTmp.length();

                Set<String> forced = MAIN_CONFIG.get("启动器配置.作为MC自身lib的key").asList().asStringSet();
                CMapping data = instConf.get("data").asMap();
                MyHashMap<String, String> map = new MyHashMap<>(data.size());
                for (Map.Entry<String, CEntry> entry : data.entrySet()) {
                    String s = entry.getValue().asMap().getString("client");
                    String v;
                    if (s.startsWith("'")) {
                        v = s.substring(1, s.length() - 1);
                    } else if (s.startsWith("/")) {
                        String rndName = "file-" + System.currentTimeMillis() + ".tmp";
                        File f = new File(libraryPath, rndName);
                        v = f.getAbsolutePath();

                        ZipEntry ze = zf.getEntry(s.substring(1));
                        if (ze == null) {
                            zf.close();
                            error("找不到需求的文件 " + s);
                            return false;
                        }

                        try (FileOutputStream fos = new FileOutputStream(f)) {
                            final InputStream in = zf.getInputStream(ze);
                            bl.readStreamArrayFully(in).writeToStream(fos);
                            in.close();
                            bl.clear();
                        }

                    } else if (s.startsWith("[")) {
                        v = new File(forced.contains(entry.getKey()) ? mcLibPath : libraryPath, mavenPath(s.substring(1, s.length() - 1)).toString()).getAbsolutePath();
                    } else {
                        CmdUtil.error("未知参数 " + s);
                        continue;
                    }

                    map.put(entry.getKey(), v);
                }

                map.put("MINECRAFT_JAR", config.get("mc_conf").asMap().getString("jar"));
                map.put("SIDE", "client");
                map.put("ROOT", libraryPath.getAbsolutePath());
                map.put("INSTALLER", fgInstaller.getAbsolutePath());

                CmdUtil.info("Parallel: 导出安装器maven");
                Enumeration<? extends ZipEntry> e = zf.entries();
                while (e.hasMoreElements()) {
                    ZipEntry ze = e.nextElement();
                    if (!ze.isDirectory() && ze.getName().startsWith("maven/")) {
                        File dest = new File(mcLibPath, ze.getName().substring(6));
                        System.out.println("导出 " + ze.getName());

                        File p = dest.getParentFile();
                        if (!p.isDirectory() && !p.mkdirs()) {
                            CmdUtil.warning("无法创建目录 ");
                        }

                        try (FileOutputStream fos = new FileOutputStream(dest)) {
                            final InputStream in = zf.getInputStream(ze);
                            bl.readStreamArrayFully(in).writeToStream(fos);
                            in.close();
                            bl.clear();
                        }
                    }
                }
                zf.close();

                waitFor(null);
                CmdUtil.info("开始安装Forge...");
                if(instConf.getInteger("spec") == 1)
                    CmdUtil.error("太妙了！forge正在把我要下载的东西单线程重新下一遍！失败了别找我哦\n 按确定继续");

                new File(BASE, "forge_install.log").delete();

                List<String> keys = new ArrayList<>();
                CList processors = instConf.get("processors").asList();
                for (int i = 0; i < processors.size(); i++) {
                    CMapping op = processors.get(i).asMap();
                    CList sides = op.getOrCreateList("sides");
                    if(sides.size() != 0) {
                        boolean cl = false;
                        for (int j = 0; j < sides.size(); j++) {
                            if(sides.get(j).asString().equals("client")) {
                                cl = true;
                                break;
                            }
                        }
                        if(!cl) {
                            CmdUtil.info("服务端处理器: " + i + ", 跳过");
                        }
                    }
                    File path = new File(libraryPath, mavenPath(op.getString("jar")).toString());
                    if (!path.isFile()) {
                        CmdUtil.error("无法找到文件 " + path.getAbsolutePath());
                        return false;
                    }

                    keys.clear();
                    keys.add("java");
                    keys.add("-cp");
                    keys.add(cpTmp.append(path.getAbsolutePath()).toString());
                    cpTmp.setIndex(cpLen);

                    String mainCls;
                    try (ZipFile zf1 = new ZipFile(path)) {
                        ZipEntry ze = zf1.getEntry("META-INF/MANIFEST.MF");
                        Manifest manifest = new Manifest(zf1.getInputStream(ze));
                        mainCls = manifest.getMainAttributes().getValue("Main-Class");
                        if (mainCls == null || mainCls.isEmpty()) {
                            CmdUtil.error("没有Main-Class " + path.getAbsolutePath());
                            return false;
                        }
                    }
                    keys.add(mainCls);

                    for (CEntry entry : op.get("args").asList()) {
                        String s = entry.asString();
                        if (s.startsWith("[")) {
                            s = new File(libraryPath, mavenPath(s.substring(1, s.length() - 1)).toString()).getAbsolutePath();
                        } else {
                            int j = s.indexOf('{');
                            if (j != -1) {
                                CharList cl = new CharList(s.length()).append(s, 0, j);
                                for (; j < s.length(); j++) {
                                    char c = s.charAt(j);
                                    if (c == '{') {
                                        int k = j;
                                        while (s.charAt(k) != '}')
                                            k++;
                                        cl.append(map.get(s.substring(j + 1, k)));
                                        j = k;
                                    } else {
                                        cl.append(c);
                                    }
                                }
                                s = cl.toString();
                            }
                        }

                        keys.add(s);
                    }

                    CmdUtil.info("运行: " + mainCls);

                    if(runProcess(keys, TMP_DIR, 6, new File(BASE, "forge_install.log")) != 0) {
                        CmdUtil.error("尝试清空tmp/fg-xx-xxx-tmp-lib文件夹");
                        return false;
                    }
                }

                CmdUtil.info("log: forge_install.log");
            } else {
                CmdUtil.info("旧版模式: 导出安装器maven");
                Enumeration<? extends ZipEntry> e = zf.entries();
                while (e.hasMoreElements()) {
                    ZipEntry ze = e.nextElement();
                    if(!ze.isDirectory() && ze.getName().startsWith("maven/")) {
                        File dest = new File(mcLibPath, ze.getName().substring(6));
                        System.out.println("导出 " + ze.getName());

                        File p = dest.getParentFile();
                        if(!p.isDirectory() && !p.mkdirs()) {
                            CmdUtil.warning("无法创建目录");
                        }

                        try(FileOutputStream fos = new FileOutputStream(dest)) {
                            final InputStream in = zf.getInputStream(ze);
                            bl.readStreamArrayFully(in).writeToStream(fos);
                            in.close();
                            bl.clear();
                        }
                    }
                }
                zf.close();
            }

            final File dst = new File(installDir, ver + ".json");
            if(!mcJsonTmp.renameTo(dst)) {
                FileUtil.copyFile(mcJsonTmp, dst);
            }
        } else {
            final CMapping install = instConf.get("install").asMap();

            final String path = install.getString("filePath");
            ZipEntry forge = zf.getEntry(path);
            if (forge == null) {
                zf.close();
                error("找不到forge文件 " + path);
                return false;
            }

            File dest = new File(mcRoot, "/libraries/" + mavenPath(install.getString("path")));
            File parent = dest.getParentFile();
            if(!parent.isDirectory() && !parent.mkdirs()) {
                error("无法创建forge jar保存目录 " + dest.getAbsolutePath());
                return false;
            }

            ByteList bl = new ByteList();
            try(FileOutputStream fos = new FileOutputStream(dest)) {
                InputStream in = zf.getInputStream(forge);
                bl.readStreamArrayFully(in);
                in.close();
                bl.writeToStream(fos);
            }

            try (FileOutputStream fos = new FileOutputStream(new File(installDir, ver + ".json"))) {
                ByteWriter.writeUTF(bl, instConf.get("versionInfo").toShortJSON(), -1);
                bl.writeToStream(fos);
                bl.clear();
            }

            zf.close();
        }

        CmdUtil.success("安装完毕.");
        return true;
    }

    public static CharList mavenPath(final String name) {
        int i = TextUtil.limitedIndexOf(name, ':', 100);
        CharList cl = new CharList().append(name).replace('.', '/', 0, i);
        List<String> parts = TextUtil.splitStringF(new ArrayList<>(4), cl, ':');

        String ext = "jar";
        final String s = parts.get(parts.size() - 1);
        int extPos = s.lastIndexOf("@");
        if(extPos != -1) {
            ext = s.substring(extPos + 1);
            parts.set(parts.size() - 1, s.substring(0, extPos));
        }

        cl.clear();
        cl.append(parts.get(0)).append('/') // d
          .append(parts.get(1)).append('/') // n
          .append(parts.get(2)).append('/') // v
          .append(parts.get(1)).append('-').append(parts.get(2)); // n-v

        if(parts.size() > 3) {
            cl.append('-').append(parts.get(3));
        }

        return cl.append('.').append(ext);
    }

    static final class VersionSelect extends ReflectTool {
        List<CEntry> versions;
        boolean fg;

        VersionSelect(List<CEntry> versions, boolean isForge) {
            super(false, "版本");
            setTitle("版本安装");

            final String id = fg ? "version" : "id";
            versions.sort(new Comparator<CEntry>() {
               final Version v1 = new Version(), v2 = new Version();
                @Override
                public int compare(CEntry o1, CEntry o2) {
                    return v1.parse(o2.asMap().getString(id), false).compareTo(v2.parse(o1.asMap().getString(id), false));
                }
            });

            setTitle(((fg = isForge) ? "Forge" : "Minecraft") + "版本选择");
            addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosed(WindowEvent e) {
                    VersionSelect.this.onClose();
                }
            });
            setDefaultCloseOperation(DISPOSE_ON_CLOSE);

            this.versions = versions;

            done();
        }

        @Override
        protected void loadData(JLabel label) {
            label.setText("加载完毕, 开始搜索吧!");
        }

        private void onClose() {
            synchronized (this) {
                notifyAll();
            }
            if(activeWindow != null)
                activeWindow.requestFocus();
        }

        @Override
        protected void search(ActionEvent event) {
            result.removeAll();
            int y = 2;

            String str = classInp.getText().toLowerCase();
            List<String> entries = new ArrayList<>();
            final String id = fg ? "version" : "id";
            for (int i = 0; i < versions.size(); i++) {
                CEntry entry = versions.get(i);
                CMapping map = entry.asMap();
                if (map.getString(id).toLowerCase().startsWith(str)) {
                    entries.add(map.getString(id));
                    if(entries.size() == 100)
                        break;
                }
            }

            if(entries.isEmpty()) {
                JLabel labelNotify = new JLabel("没有结果!");
                labelNotify.setForeground(Color.RED);
                labelNotify.setBounds(220, y, 80, 15);
                y += 22;
                result.add(labelNotify);
            } else if(entries.size() >= 100) {
                JLabel labelNotify = new JLabel("结果超过" + 100 +  "个!");
                labelNotify.setForeground(Color.RED);
                labelNotify.setBounds(200, y, 100, 15);
                y += 22;
                result.add(labelNotify);
            }
            for(String entry : entries) {
                JButton button = new JButton(entry);
                button.setBounds(5, y, 480, 20);
                y += 22;
                button.addActionListener(this::choose);
                result.add(button);
            }

            result.setPreferredSize(new Dimension(500, y));
            scroll.validate();
            result.updateUI();
        }

        private void choose(ActionEvent event) {
            String text = ((JButton)event.getSource()).getText();
            final String id = fg ? "version" : "id";
            for (int i = 0; i < versions.size(); i++) {
                CEntry entry = versions.get(i);
                CMapping map = entry.asMap();
                if (map.getString(id).equals(text)) {
                    SwingUtilities.invokeLater(() -> {
                        onClickInstall(map, fg);
                    });
                    break;
                }
            }
            this.dispose();
        }
    }

    // endregion
    // endregion
    // region config

    static CMapping config;

    static void save() {
        try (FileOutputStream fos = new FileOutputStream(new File(BASE, "launcher.json"))) {
            fos.write(config.toShortJSON().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            error("配置保存失败\n详情看控制台");
            e.printStackTrace();
        }
    }

    static void load() {
        if(config != null) return;

        File conf = new File(BASE, "launcher.json");
        if(conf.isFile()) {
            try {
                final BOMInputStream bom = new BOMInputStream(new FileInputStream(conf), "UTF8");
                if(!bom.getEncoding().equals("UTF8")) { // 检测到了则是 UTF-8
                    CmdUtil.warning("文件的编码中含有BOM(推荐使用UTF-8无BOM格式!), 识别的编码: " + bom.getEncoding());
                }

                config = JSONParser.parse(IOUtil.readAs(bom, bom.getEncoding())).asMap();
                config.dotMode(true);
                CEntry path = config.getOrNull("mc_conf.native_path");
                if(path != null) {
                    File native_path = new File(path.asString());
                    if (!native_path.isDirectory()) {
                        error("你移动或修改了MC目录,请重新选择核心!");
                        config.remove("mc_conf");
                        config.remove("mc_version");
                        save();
                    }
                }
                return;
            } catch (Throwable e) {
                error("配置加载失败\n详情看控制台");
                e.printStackTrace();
            }
        }
        config = new CMapping();
    }

    // region edit

    private static void config(ActionEvent event) {
        if(!config.containsKey("mc_conf", roj.config.data.Type.MAP)) {
            error("请选择版本!");
        } else {
            if(!config.getBool("tip1")) {
                error("这里的修改在重新选择版本后会被通用配置覆盖\n" +
                              "启动配置保存在launcher.json\n" +
                              "通用配置保存在config.json");
                config.put("tip1", true);
                save();
            }
            new Config();
        }
    }

    static final class Config extends JFrame {
        private final JTextField nameInput, jvmInput, mcInput;

        public Config() {
            super("当前版本配置");
            UIUtil.setLogo(this, "FMD_logo.png");

            setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

            JLabel label = new JLabel("玩家名:");
            label.setBounds(10,10,80,25);
            add(label);

            label = new JLabel("JVM参数:");
            label.setBounds(10,40,80,25);
            add(label);

            label = new JLabel("MC参数:");
            label.setBounds(10,70,80,25);
            add(label);

            final CMapping mc_conf = config.get("mc_conf").asMap();

            nameInput = new JTextField(mc_conf.getString("player_name"), 0);
            nameInput.setBounds(80,10,500,25);
            add(nameInput);

            jvmInput = new JTextField(mc_conf.getString("jvmArg"), 0);
            jvmInput.setBounds(80,40,500,25);
            add(jvmInput);

            mcInput = new JTextField(mc_conf.getString("mcArg"), 0);
            mcInput.setBounds(80,70,500,25);
            add(mcInput);

            JPanel panel = new JPanel();
            panel.setBounds(40, 100, 40, 25);

            JButton save = new JButton("保存");
            save.addActionListener(this::save);
            panel.add(save);

            add(panel);

            setLayout(null);
            setVisible(true);
            setResizable(true);

            pack();
            setBounds(600, 400, 600, 160);
            validate();
        }

        private void save(ActionEvent event) {
            final String s = nameInput.getText().trim();
            if(s.isEmpty()) {
                JOptionPane.showMessageDialog(this, "名字不能为空", "错误", JOptionPane.WARNING_MESSAGE);
            } else {
                final CMapping mc_conf = config.get("mc_conf").asMap();
                mc_conf.put("player_name", s);
                mc_conf.put("jvmArg", jvmInput.getText().trim());
                mc_conf.put("mcArg", mcInput.getText().trim());
                MCLauncher.save();

                dispose();
            }
        }
    }

    // endregion
    // endregion

    // region prepare and run MC

    // region check
    static RunMinecraftTask task;

    private static boolean checkMCRun() {
        if (task != null && !task.isDone()) {
            int n = JOptionPane.showConfirmDialog(activeWindow, "MC没有退出,是否结束进程?", "询问", JOptionPane.YES_NO_OPTION);
            if (n == JOptionPane.YES_OPTION) task.cancel(true);
            else return true;
        }
        return false;
    }
    // endregion

    public static int runClient(CMapping mc_conf, File nativePath, int processFlags, Consumer<Process> consumer) throws IOException {
        Map<String, String> env = new MyHashMap<>(4);
        env.put("natives_directory", '"' + AbstLexer.addSlashes(nativePath.getAbsolutePath()) + '"');
        env.put("classpath", '"' + AbstLexer.addSlashes(new StringBuilder(mc_conf.getString("libraries")).append(mc_conf.getString("jar"))) + '"');
        env.put("launcher_name", "FMD");
        env.put("launcher_version", VERSION);
        env.put("classpath_separator", File.pathSeparator);
        x:
        try {
            do {
                nativePath = nativePath.getParentFile();
                if(nativePath == null)
                    break x;
            } while (!nativePath.getName().equals(".minecraft"));
            env.put("library_directory", new File(nativePath, "libraries").getAbsolutePath());
        } catch (Throwable e) {
            e.printStackTrace();
        }

        Map<String, String> mcEnv = new MyHashMap<>();

        String playerName = mc_conf.getString("player_name");
        if(playerName.equals(""))
            playerName = "Roj234";
        String uuid = UUID.nameUUIDFromBytes(playerName.getBytes(StandardCharsets.UTF_8)).toString();

        mcEnv.put("user_type", "Legacy");
        mcEnv.put("version_type", "FMD");
        mcEnv.put("launcher_name", "FMD");
        mcEnv.put("launcher_version", VERSION);
        mcEnv.put("player_name", playerName);
        mcEnv.put("auth_access_token", uuid);
        mcEnv.put("auth_uuid", uuid);
        mcEnv.put("assets_index_name", mc_conf.getString("assets"));
        mcEnv.put("assets_root", '"' + AbstLexer.addSlashes(mc_conf.getString("assets_root") + File.separatorChar + "assets") + '"');
        mcEnv.put("game_directory", mc_conf.getString("root"));
        mcEnv.put("version_name", "FMDv" + VERSION);
        mcEnv.put("auth_player_name", playerName);
        mcEnv.put("user_properties", "{}");

        CmdUtil.info("启动客户端...");

        SimpleList<String> list = new SimpleList<>();
        list.add("java");
        TextUtil.replaceVariable(env, mc_conf.getString("jvmArg"), list);
        list.add(mc_conf.getString("mainClass"));
        TextUtil.replaceVariable(mcEnv, mc_conf.getString("mcArg"), list);

        return runProcess(list, new File(mc_conf.getString("root")), processFlags, consumer);
    }

    // region UI
    private static void runClient(ActionEvent event) {
        if (checkMCRun()) return;

        if(!config.containsKey("mc_conf")) {
            error("没有选择版本!");
            return;
        }

        parallel.pushTask(task = new RunMinecraftTask(false));
    }

    private static void debug(ActionEvent event) {
        if (checkMCRun()) return;

        if(!config.containsKey("mc_conf")) {
            error("没有选择版本!");
            return;
        }

        parallel.pushTask(task = new RunMinecraftTask(true));
    }

    // endregion

    // region prepare MC

    public static Object[] getRunConf(File mcRoot, File mcJson, File mcNative, CMapping general) throws IOException {
        return getRunConf(mcRoot, mcJson, mcNative, Collections.emptyList(), false, general);
    }

    public static Object[] getRunConf(File mcRoot, File mcJson, File nativePath, Collection<String> skipped, boolean cleanNatives, CMapping general) throws IOException {
        return getRunConf(mcRoot, mcJson, nativePath, skipped, cleanNatives, general.getBool("版本隔离"), general.getString("libraries地址"), general.getString("附加JVM参数"), general.getString("附加MC参数"));
    }

    public static Object[] getRunConf(File mcRoot, File mcJson, File nativePath, Collection<String> skipped, boolean cleanNatives, boolean insulation, String mirror, String jvmArg, String mcArg) throws IOException {
        CMapping mc_conf = new CMapping();

        mc_conf.put("assets_root", mcRoot.getAbsolutePath());
        mc_conf.put("root", insulation ? mcJson.getParentFile().getAbsolutePath() : mcRoot.getAbsolutePath());

        if(nativePath.isDirectory()) {
            if(cleanNatives && !FileUtil.deletePath(nativePath)) {
                CmdUtil.error("无法清空natives, 请手动删除");
                return null;
            }
        } else if(!nativePath.mkdirs()) {
            CmdUtil.error("无法创建natives");
            return null;
        }

        Map<String, String> libraries = new MyHashMap<>();
        boolean is113andAbove = false;
        File mcJar;
        CMapping jsonDesc;

        try {
            jsonDesc = JSONParser.parse(IOUtil.readAsUTF(new FileInputStream(mcJson))).asMap();

            File librariesPath = new File(mcRoot, "/libraries/");

            MyHashMap<String, Object> imArgs = new MyHashMap<>(8);
            imArgs.put("libraryPath", librariesPath);
            imArgs.put("nativePath", nativePath);
            imArgs.put("libraries", libraries);
            imArgs.put("mirror", mirror);
            imArgs.put("handler", parallel);

            Object[] arr = parseJsonData(imArgs, new File(mcRoot, "/versions/"), skipped, jsonDesc);

            mcJar = (File) arr[0];

            mc_conf.put("jar", mcJar.getAbsolutePath());

            mc_conf.put("assets", (String) arr[1]);
            mc_conf.put("mainClass", (String) arr[2]);

            final int flag = (int) arr[5];
            if((flag & 1) != 0) {
                is113andAbove = true;

                String[] arr1 = (String[]) arr[3];
                mc_conf.put("mcArg", arr1[0] + ' ' + mcArg);
                mc_conf.put("jvmArg", jvmArg + ' ' + arr1[1]);
            } else {
                mc_conf.put("mcArg", ((String)arr[3]) + ' ' + mcArg);
                mc_conf.put("jvmArg", jvmArg + " -Djava.library.path=${natives_directory} -cp ${classpath}");
            }
            //mc_conf.put("maybeForge", (flag & 2) == 2);

            StringBuilder libPathString = new StringBuilder();
            String tmp = librariesPath.getAbsolutePath() + File.separatorChar;

            for (String lib : libraries.values()) {
                libPathString.append(tmp).append(lib).append(File.pathSeparatorChar);
            }

            mc_conf.put("libraries", libPathString.toString());
        } catch (ParseException e) {
            throw new IOException(mcJson + "读取失败...如果你确定你的版本json文件没有问题, 请报告这个异常", e);
        }

        return new Object[] {
                mc_conf, mcJar, libraries, jsonDesc, is113andAbove
        };
    }

    // region parse Manifest

    private static Object[] parseJsonData(MyHashMap<String, Object> imArgs, File versionsPath, Collection<String> skipped, CMapping desc) throws IOException, ParseException {
        Map<String, Version> versions = new MyHashMap<>();

        for(String s : skipped) {
            versions.put(s, Version.INFINITY);
        }

        if(DEBUG && !versions.isEmpty()) {
            CmdUtil.info("注: 跳过的依赖 " + versions.keySet());
        }

        imArgs.put("versions", versions);
        Object[] arr = mergeInherit(imArgs, versionsPath, desc, null, null, null, null, -1);

        int minLauncherVersion = (int) arr[4];
        if(minLauncherVersion > 18) {
            //if(arr[0] == null) {
            //    arr[0] = new File(versionsPath, desc.getString("id") + '/' + desc.getString("id") + ".jar");
            //}

            arr[3] = gatherNewArgs(versionsPath, desc, null);
            arr[5] = 1;
        }

        //if(arr[0] == null) {
        //    if(desc.getString("id").contains("forge"))
        //        CmdUtil.warning("没找到jar");
            // is not forge
        //    arr[0] = new File(versionsPath, desc.getString("id") + '/' + desc.getString("id") + ".jar");
        //    arr[5] = ((int)arr[5]) + 2;
        //}

        return arr;
    }

    private static String[] gatherNewArgs(File version, CMapping mapping, String arg) {
        CMapping map = mapping.get("arguments").asMap();

        CList mcArg = map.get("game").asList();
        String s1 = buildNewArgs(mcArg);

        CList jvmArg = map.get("jvm").asList();
        String s2 = buildNewArgs(jvmArg);

        return new String[] {s1, s2};
    }

    @Nonnull
    private static String buildNewArgs(CList mcArg) {
        StringBuilder arg = new StringBuilder();

        for(CEntry entry : mcArg) {
            switch (entry.getType()) {
                case STRING:
                    arg.append(slash(entry.asString())).append(' ');
                    break;
                case MAP:
                    if(isRuleFit(entry.asMap().get("rules").asList())) {
                        CEntry entry1 = entry.asMap().get("value");
                        if (entry1.getType() == roj.config.data.Type.LIST) {
                            CList values = entry1.asList();
                            for (CEntry value : values) {
                                arg.append(slash(value.asString())).append(' ');
                            }
                        } else {
                            arg.append(slash(entry1.asString())).append(' ');
                        }
                    }
                    break;
                default:
                    throw new IllegalArgumentException("未知类型: " + entry.toJSON());
            }
        }
        return arg.toString();
    }

    private static String slash(String s) {
        int index = s.indexOf('=');
        if(index != -1) {
            if(s.contains(" ")) {
                return s.substring(0, index + 1) + '"' + AbstLexer.addSlashes(s.substring(index + 1)) + '"';
            }
        }
        return s;
    }

    @SuppressWarnings("unchecked")
    private static Object[] mergeInherit(Map<String, Object> imArg, File version, CMapping mapping, File jar, String asset, String mainClass, String arg, int ver) throws IOException, ParseException {
        CList list = mapping.get("libraries").asList();

        if(list.size() > 0) {
            String mirror = (String) imArg.get("mirror");
            Map<String, String> libraries = (Map<String, String>) imArg.get("libraries");
            Map<String, Version> versions = (Map<String, Version>) imArg.get("versions");
            File nativePath = (File) imArg.get("nativePath");
            File libraryPath = (File) imArg.get("libraryPath");
            TaskHandler handler = (TaskHandler) imArg.get("handler");
            for (CEntry entry : list) {
                detectLibrary(entry.asMap(), versions, libraryPath, nativePath, libraries, mirror, handler);
            }
        }

        String jarName = mapping.getString("jar");
        if(jarName.length() > 0) {
            jar = new File(version, jarName + '/' + jarName + ".jar");
        }

        int ver1 = mapping.getInteger("minimumLauncherVersion");
        if(ver1 > ver) {
            ver = ver1;
        }

        String assets1 = mapping.getString("assets");
        if(assets1.length() > 0) {
            asset = assets1;
        }

        String mainClass1 = mapping.getString("mainClass");
        if(mainClass1.length() > 0) {
            mainClass = mainClass1;
        }

        String arg1 = mapping.getString("minecraftArguments");
        if(arg1.length() > 0) {
            arg = arg1;
        }

        String inherit = mapping.getString("inheritsFrom");
        if(inherit.length() > 0) {
            File dir = new File(version, inherit + '/' + inherit + ".json");
            CMapping desc = JSONParser.parse(IOUtil.readAsUTF(new FileInputStream(dir))).asMap();
            Object[] arr = mergeInherit(imArg, version, desc, jar, asset, mainClass, arg, ver);
            if(jar == null)
                jar = (File) arr[0];
            if(asset == null)
                asset = (String) arr[1];
            if(mainClass == null)
                mainClass = (String) arr[2];
            if(arg == null)
                arg = (String) arr[3];
            if((ver1 = (int) arr[4]) > ver) {
                ver = ver1;
            }
            mapping.merge(desc, true, true);
        }

        if(jar == null)
            jar = new File(version, (arg1 = mapping.getString("id")) + '/' + arg1 + ".jar");

        return new Object[] {jar, asset, mainClass, arg, ver, 0};
    }

    private static void detectLibrary(CMapping data, Map<String, Version> versions, File libPath, File nativePath, Map<String, String> libraries, String mirror, TaskHandler handler) {
        if(!isRuleFit(data.get("rules").asList())) {
            if(DEBUG)
                CmdUtil.info(data.getString("name") + " 不符合加载规则" + data.get("rules").toShortJSON());
            return;
        }

        String t1 = data.getString("name");
        int i = TextUtil.limitedIndexOf(t1, ':', 100);
        CharList sb = new CharList().append(t1).replace('.', '/', 0, i);
        List<String> parts = TextUtil.splitStringF(new ArrayList<>(4), sb, ':');

        String ext = "jar";
        final String s = parts.get(parts.size() - 1);
        int extPos = s.lastIndexOf("@");
        if(extPos != -1) {
            ext = s.substring(extPos + 1);
            parts.set(parts.size() - 1, s.substring(0, extPos));
        }

        sb.clear();

        String name = sb.append(parts.get(0)).append(':').append(parts.get(1)).toString();

        Version prevVer = versions.get(name);
        Version currVer = new Version(parts.get(2));
        boolean sameVersion = false;

        if(prevVer != null) {
            switch (currVer.compareTo(prevVer)) {
                case -1:
                    if(DEBUG)
                        System.out.println("跳过旧版本 " + currVer + " 的 " + name + " (新版本:" + prevVer + ')');
                    return;
                case 0:
                    sameVersion = true;
                    break;
                case 1:
                    if(DEBUG)
                        System.out.println("使用新版本 " + currVer + " 的 " + name + " (旧版本:" + prevVer + ')');
                    break;
            }
        }

        sb.clear();
        sb.append(parts.get(0)).append('/') // d
                .append(parts.get(1)).append('/') // n
                .append(parts.get(2)).append('/') // v
                .append(parts.get(1)).append('-').append(parts.get(2)); // n-v

        CMapping natives = data.get("natives").asMap();
        boolean nt = natives.size() > 0;
        String classifiers = null;
        if(nt) {
            String t;
            switch (OS.CURRENT) {
                case UNIX:
                    t = "unix";
                    break;
                case WINDOWS:
                    t = "windows";
                    break;
                case MAC_OS:
                    t = "osx";
                    break;
                default:
                    CmdUtil.warning("未知系统版本 " + OS.CURRENT);
                    return;
            }
            sb.append('-').append(classifiers = natives.getString(t).replace("${arch}", OS.ARCH));
        }

        if(parts.size() > 3) {
            if(nt)
                CmdUtil.warning("这是哪里来的第二个classifier? " + parts);
            sb.append('-').append(parts.get(3));
        }

        String file = sb.append('.').append(ext).toString();

        File libFile = new File(libPath, file);
        if(!libFile.isFile()) {
            if(mirror != null) {
                final CMapping downloads = data.containsKey("downloads") ? data.get("downloads").asMap() : data;
                downloadLibrary(downloads, mirror, libFile, handler, nt ? () -> {
                    extractNatives(data, libFile, nativePath);
                } : null, classifiers, file);
                if(nt)
                    return;
            } else {
                if (DEBUG)
                    CmdUtil.warning(libFile + " 不存在!");
            }
        } else if(nt) {
            extractNatives(data, libFile, nativePath);
            return;
        }

        if(sameVersion) {
            if(DEBUG)
                CmdUtil.warning(name + " 版本相同且不是Natives!");
            return;
        }

        versions.put(name, currVer);
        libraries.put(name, file);
    }

    private static void downloadLibrary(CMapping map, String mirror, File libFile, TaskHandler handler, Runnable cb, String classifiers, String libFileName) {
        File libParent = libFile.getAbsoluteFile().getParentFile();
        if(!libParent.isDirectory() && !libParent.mkdirs())
            throw new IllegalStateException("无法创建library保存文件夹  " + libParent.getAbsolutePath());

        CMapping artifact;
        if(classifiers != null) {
            artifact = map.get("classifiers").asMap().get(classifiers).asMap();
        } else if(!map.containsKey("artifact")) { // mc
            artifact = new CMapping();
            if(!map.containsKey("url")) {
                if (DEBUG)
                    CmdUtil.warning(libFile + " 没有下载地址");
                // artifact.put("url", MAIN_CONFIG.get("通用").asMap().getString("协议") + "libraries.minecraft.net/" + libFileName);
            } else {
                artifact.put("url", map.getString("url") + libFileName);
            }
        } else {
            artifact = map.get("artifact").asMap();
        }
        if(!artifact.containsKey("url") || artifact.getString("url").isEmpty()) {
            final CMapping map1 = MAIN_CONFIG.get("通用").asMap();
            artifact.put("url", map1.getString("协议") + (map1.getString("libraries地址").isEmpty() ? map1.getString("forge地址") : map1.getString("libraries地址")) + '/' + libFileName);
        }

        if(handler != null) {
            final DownMcFile task = downMcFileAsTask(artifact, libFile, mirror.isEmpty() ? null : mirror);
            if(task != null) {
                task.then(cb);
                handler.pushTask(task);
                return;
            }
        } else {
            try {
                downloadMinecraftFile(artifact, libFile, mirror.isEmpty() ? null : mirror, true);
            } catch (IOException e) {
                throw new IllegalStateException("下载失败", e);
            }
        }
        if(cb != null)
            cb.run();
    }

    private static boolean isRuleFit(CList rules) {
        boolean fitRule = rules.size() == 0;
        for(CEntry entry : rules) {
            CMapping entry1 = entry.asMap();
            switch (entry1.getString("action")) {
                case "allow":
                    if(canFitRule0(entry1)) {
                        fitRule = true;
                    }
                    break;
                case "disallow":
                    if(canFitRule0(entry1)) {
                        fitRule = false;
                    }
                    break;
            }
        }

        return fitRule;
    }

    private static boolean canFitRule0(CMapping ruleEntry) {
        if(ruleEntry.size() == 1)
            return true;
        if(ruleEntry.containsKey("os")) {
            CMapping os = ruleEntry.get("os").asMap();
            if (!os.containsKey("name"))
                return true;
            String ver = os.getString("version");
            if(ver.length() > 0) {
                try {
                    Pattern pattern = Pattern.compile(ver);
                    String test = System.getProperty("os.name").replace("Windows ", "");
                    if(!pattern.matcher(test).matches())
                        return false;
                } catch (Throwable ignored) {}
            }

            switch (os.getString("name")) {
                case "osx":
                    return OS.CURRENT == OS.MAC_OS;
                case "win":
                case "windows":
                    return OS.CURRENT == OS.WINDOWS;
                case "linux":
                    return OS.CURRENT == OS.UNIX;
                case "unknown":
                    return OS.CURRENT == OS.UNKNOWN || OS.CURRENT == OS.JVM;
            }
            throw new RuntimeException("未知的系统类型: " + os.getString("name"));
        }
        if(ruleEntry.containsKey("features")) {
            CMapping features = ruleEntry.get("features").asMap();
            for(Map.Entry<String, CEntry> entry : features.entrySet()) {
                if(!canFitFeature(entry))
                    return false;
            }
            return true;
        }
        throw new IllegalArgumentException("未知规则: " + ruleEntry.toJSON());
    }

    private static boolean canFitFeature(Map.Entry<String, CEntry> entry){
        String k = entry.getKey();
        switch (k) {
            case "is_demo_user":
            case "has_custom_resolution":
                return false;
        }
        final CEntry value = entry.getValue();
        switch (value.getType()) {
            case STRING:
            case DOUBLE:
            case INTEGER:
            case BOOL:
                CmdUtil.warning("FMD发现了一个未知规则: '" + k + "' 请手动判断这个规则是否符合");
                CmdUtil.warning(k + ": " + value.asString());
                try {
                    return UIUtil.readBoolean("请输入T或F并按回车: ");
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
        }
        throw new IllegalArgumentException("未知规则类型: " + value.getType());
    }

    // endregion

    private static void extractNatives(CMapping data, File libFile, File nativePath) {
        CMapping extractInfo = data.get("extract").asMap();
        CList exclude = extractInfo.get("exclude").asList();

        TrieTreeSet trieTree = new TrieTreeSet();
        for(CEntry entry : exclude) {
            trieTree.add(entry.asString());
        }
        trieTree.add("META-INF");

        if(DEBUG)
            System.out.println("解压natives " + libFile);
        try {
            ZipFile zipFile = new ZipFile(libFile);
            Enumeration<? extends ZipEntry> enumeration = zipFile.entries();
            while(enumeration.hasMoreElements()) {
                ZipEntry zipEntry = enumeration.nextElement();
                final String name = zipEntry.getName();
                if(!zipEntry.isDirectory() && !trieTree.startsWith(name) && (name.endsWith(".dll") || name.endsWith(".so"))) {
                    try(FileOutputStream fos = new FileOutputStream(new File(nativePath, name))) {
                        fos.write(IOUtil.readFully(zipFile.getInputStream(zipEntry)));
                    }
                } else if(DEBUG) {
                    CmdUtil.info("排除文件 " + zipEntry);
                }
            }
        } catch (IOException e) {
            CmdUtil.warning("Natives 无法读取!");
            e.printStackTrace();
        }
    }

    // endregion

    static final class RunMinecraftTask implements ITask, Consumer<Process> {
        boolean log;
        boolean run;
        Process process;

        RunMinecraftTask(boolean log) {
            this.log = log;
        }

        @Override
        public void calculate(Thread thread) throws Exception {
            runClient(config.get("mc_conf").asMap(), new File(config.getString("mc_conf.native_path")), log ? 3 : 2, this);
            run = true;
        }

        @Override
        public boolean isDone() {
            return run;
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public boolean cancel(boolean force) {
            if(run || process == null)
                return false;
            process.destroyForcibly();
            process = null;
            return true;
        }

        @Override
        public void accept(Process process) {
            this.process = process;
        }
    }

    // endregion
    // region Alert window

    public static void error(String s) {
        JOptionPane.showMessageDialog(activeWindow, s, "错误", JOptionPane.ERROR_MESSAGE);
    }

    public static void info(String s) {
        JOptionPane.showMessageDialog(activeWindow, s, "提示", JOptionPane.INFORMATION_MESSAGE);
    }

    // endregion
    // region download Util

    public static File downloadMinecraftFile(CMapping downloads, String name, String mirror) throws IOException {
        final CMapping map = downloads.get(name).asMap();
        String sha1 = map.get("sha1").asString();

        File tmpFile = new File(TMP_DIR, sha1 + '.' + name);

        downloadMinecraftFile(map, tmpFile, mirror, true);
        return tmpFile;
    }

    public static DownMcFile downMcFileAsTask(CMapping map, File targetFile, String mirror) {
        String url = replaceMirror(map, mirror);

        if (!targetFile.exists()) {
            //if(DEBUG)
            //    CmdUtil.info("下载 " + url);

            BigInteger sha1 = map.containsKey("sha1") ? new BigInteger(map.get("sha1").asString(), 16) : null;

            return new DownMcFile(url, targetFile, sha1);
        } else {
            return null;
        }
    }

    public static void downloadMinecraftFile(CMapping map, File targetFile, String mirror, boolean async) throws IOException {
        String url = replaceMirror(map, mirror);

        if (!targetFile.exists()) {
            CmdUtil.info("开始下载 " + url);

            int retry = 0;

            BigInteger lastResl = null;
            BigInteger sha1 = map.containsKey("sha1") ? new BigInteger(map.get("sha1").asString(), 16) : null;
            do {
                try {
                    if (async) {
                        FileUtil.downloadFileAsync(url, targetFile).waitFor();
                    } else {
                        FileUtil.downloadFile(url, targetFile);
                    }
                } catch (Throwable e) {
                    if(!targetFile.isFile()) {
                        CmdUtil.error(url + "下载失败, 重试", e);
                        try {
                            Thread.sleep(2500);
                        } catch (InterruptedException ignored) {}

                        if (retry++ > 5) {
                            throw e;
                        }
                        continue;
                    }
                }

                FileUtil.SHA1.reset();
                byte[] _sha1 = FileUtil.SHA1.digest(IOUtil.readFully(new FileInputStream(targetFile)));
                FileUtil.SHA1.reset();
                BigInteger sha1Resl = new BigInteger(1, _sha1);

                if (sha1 == null || sha1.equals(sha1Resl)) {
                    break;
                } else {
                    if (sha1Resl.equals(lastResl)) {
                        CmdUtil.warning("二次SHA1相同,镜像问题? " + url);
                        return;
                    }

                    CmdUtil.warning("SHA1效验失败! 预计: " + sha1.toString(16) + " 实际: " + sha1Resl.toString(16) +", 重新下载...");
                    if (!targetFile.delete()) {
                        throw new IOException("文件" + targetFile.getName() + "删除失败!");
                    }
                    lastResl = sha1Resl;
                }
            } while (true);
        }
    }

    private static String replaceMirror(CMapping map, String mirror) {
        String url = map.get("url").asString();

        if (mirror != null) {
            url = url.replace("launchermeta.mojang.com", mirror);
            url = url.replace("launcher.mojang.com", mirror);
            url = url.replace("libraries.minecraft.net", mirror);
            url = url.replace("files.minecraftforge.net/maven", mirror);
            url = url.replace("maven.minecraftforge.net", mirror);
        }
        return url;
    }

    public static void downloadAndVerifyMD5(String url, String md5ToCheck, File targetFile, boolean async) throws IOException {
        if (!targetFile.exists()) {
            CmdUtil.info("开始下载 " + url);

            int retry = 0;

            BigInteger lastResl = null;
            BigInteger md5 = md5ToCheck == null ? null : new BigInteger(md5ToCheck, 16);
            do {
                try {
                    if (async) {
                        FileUtil.downloadFileAsync(url, targetFile).waitFor();
                    } else {
                        FileUtil.downloadFile(url, targetFile);
                    }
                } catch (Throwable e) {
                    if(!targetFile.isFile()) {
                        CmdUtil.error(url + "下载失败, 重试", e);
                        try {
                            Thread.sleep(5000);
                        } catch (InterruptedException ignored) {}

                        if(retry++ > 5) {
                            throw e;
                        }
                        continue;
                    }
                }

                if(md5 == null)
                    return;

                FileUtil.MD5.reset();
                byte[] _md5 = FileUtil.MD5.digest(IOUtil.readFully(new FileInputStream(targetFile)));
                FileUtil.MD5.reset();
                BigInteger md5Resl = new BigInteger(1, _md5);

                if (md5.equals(md5Resl)) {
                    return;
                } else {
                    if (md5Resl.equals(lastResl)) {
                        CmdUtil.warning("二次MD5相同! 可能是镜像问题!");
                        return;
                    }
                    CmdUtil.warning("MD5效验失败! 预计: " + md5.toString(16) + " 实际: " + md5Resl.toString(16) + ", 重新下载...");
                    if (!targetFile.delete()) {
                        throw new IOException("文件" + targetFile.getName() + "删除失败!");
                    }
                    lastResl = md5Resl;
                }
            } while (true);
        }
    }

    public static void downloadAndVerifyMD5(String url, File targetFile, boolean async) throws IOException {
        if (!targetFile.exists()) {
            downloadAndVerifyMD5(url, new String(FileUtil.downloadFileToMemory(url + ".md5"), StandardCharsets.UTF_8), targetFile, async);
        }
    }

    private static class DownMcFile extends AbstractCalcTask<Void> {
        private final String url;
        private final File target;
        private final BigInteger sha1;

        boolean waitDigest;
        BigInteger lastResl;
        WaitingIOFuture future;
        Runnable callback;
        int retry;

        static PrintStream err;

        public DownMcFile(String url, File target, BigInteger sha1) {
            this.url = url;
            this.target = target;
            this.sha1 = sha1;
        }

        public void then(Runnable function) {
            this.callback = function;
        }

        @Override
        public void calculate(Thread thread) throws Exception {
            executing = true;

            if(!waitDigest) {
                if (future == null) {
                    try {
                        future = FileUtil.downloadFileAsync(url, target, IProgressHandler.getNotify(false));
                        if("unsupported".equals(future.flag()))
                            CmdUtil.warning("源" + url + "不支持断点续传!");
                    } catch (Throwable e) {
                        handleError(e);
                        return;
                    }
                }

                if (!future.isDone()) {
                    Thread.sleep(5);
                    return;
                }

                try {
                    future.waitFor();
                } catch (Throwable e) {
                    handleError(e);
                    future = null;
                    return;
                }

                if (!target.isFile()) {
                    future = null;
                    return;
                }

                waitDigest = true;
                return;
            }

            waitDigest = false;

            FileUtil.SHA1.reset();
            byte[] _sha1 = FileUtil.SHA1.digest(IOUtil.readFully(new FileInputStream(target)));
            FileUtil.SHA1.reset();

            BigInteger sha1Resl = new BigInteger(1, _sha1);

            if (sha1 == null || sha1.equals(sha1Resl)) {
                out = null;
                executing = false;
                CmdUtil.success(url.substring(url.lastIndexOf('/') + 1) + " 完毕.");
                if(callback != null)
                    callback.run();
            } else {
                if (sha1Resl.equals(lastResl)) {
                    CmdUtil.warning("二次SHA1相同,镜像问题? " + url);
                    out = null;
                    executing = false;
                    if(callback != null)
                        callback.run();
                    return;
                }

                if(DEBUG)
                    CmdUtil.warning("SHA1 For " + url + " Got: " + sha1Resl.toString(16));
                if (!target.delete()) {
                    CmdUtil.warning("文件" + target.getName() + "删除失败!");
                }
                future = null;

                lastResl = sha1Resl;
            }
        }

        public void handleError(Throwable e) {
            executing = false;

            if(e instanceof CancellationException) {
                CmdUtil.warning(url + " 的下载被取消");
                out = null;
                canceled = true;
                return;
            }

            if(e.getMessage().equals("文件已存在")) {
                CmdUtil.info(url + " 已被其它线程完成");
                out = null;
                return;
            }

            if(e.getMessage().equals("文件已被占用")) {
                CmdUtil.warning(url + " 正在被另一个线程下载");
                out = null;
                return;
            }

            if(!e.getMessage().equals("Read timed out")) {
                if(e instanceof FileNotFoundException) {
                    CmdUtil.error("网络连接异常: " + e.toString());
                    out = null;
                    return;
                }

                CmdUtil.error(url.substring(url.lastIndexOf('/') + 1) + " 下载失败.");

                if (err == null) {
                    try {
                        err = new PrintStream(new FileOutputStream(new File(BASE, "network_error.log")));
                    } catch (FileNotFoundException eee) {
                        err = System.err;
                    }
                }

                synchronized (err) {
                    err.println(System.currentTimeMillis());
                    err.print(url);
                    err.println("下载失败");
                    e.printStackTrace(err);
                    err.println();
                }

                if (retry++ > 5) {
                    CmdUtil.error(url + "错误太多, abort", e);
                    out = null;
                }
            } else {
                CmdUtil.warning("下载失败: 网络超时");
            }
        }

        @Override
        public boolean continueExecuting() {
            return !canceled && ((Object)out == this);
        }
    }

    // endregion
    // region Process management

    @SuppressWarnings("unchecked")
    public static int runProcess(List<String> tokens, File dir, int flag, Object obj) throws IOException {
        if(DEBUG)
            System.out.println("参数: '" + tokens + "'");
        ProcessBuilder pb = new ProcessBuilder(tokens).directory(dir);
        if((flag & 1) != 0)
            pb.redirectOutput(ProcessBuilder.Redirect.INHERIT)
                    .redirectError(ProcessBuilder.Redirect.INHERIT);
        else {
            File debug = (flag & 4) == 0 ? new File(dir, "debug.log") : (File) obj;
            pb.redirectOutput(ProcessBuilder.Redirect.appendTo(debug))
                    .redirectError(ProcessBuilder.Redirect.appendTo(debug));
        }

        Process process = pb.start();
        if(obj instanceof Consumer)
            ((Consumer<Process>)obj).accept(process);

        // 会导致无法窗口化, 可能是缓冲区满了....
        if((flag & 2) != 0) {
            try {
                process.waitFor();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            CmdUtil.info("进程终止");
            return process.exitValue();
        }
        return 0;
    }

    // endregion
}
