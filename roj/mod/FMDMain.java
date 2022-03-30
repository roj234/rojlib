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

import roj.asm.AccessTransformer;
import roj.asm.tree.ConstantData;
import roj.asm.util.Context;
import roj.collect.MyHashMap;
import roj.collect.MyHashSet;
import roj.collect.SimpleList;
import roj.concurrent.Waitable;
import roj.concurrent.task.AbstractExecutionTask;
import roj.concurrent.task.ExecutionTask;
import roj.config.data.CEntry;
import roj.config.data.CList;
import roj.config.data.CMapping;
import roj.config.data.Type;
import roj.config.word.Tokenizer;
import roj.config.word.Word;
import roj.config.word.WordPresets;
import roj.dev.ByteListOutput;
import roj.dev.Compiler;
import roj.io.*;
import roj.io.down.Downloader;
import roj.mapper.CodeMapper;
import roj.mapper.ConstMapper;
import roj.mapper.ConstMapper.State;
import roj.mapper.Mapping;
import roj.mapper.Util;
import roj.mapper.util.ResWriter;
import roj.math.Version;
import roj.mod.FileFilter.CmtATEntry;
import roj.mod.MCLauncher.RunMinecraftTask;
import roj.mod.fp.Proc1_12;
import roj.mod.fp.Proc1_16;
import roj.mod.fp.Processor;
import roj.mod.util.MappingHelper;
import roj.mod.util.YarnMapping;
import roj.text.CharList;
import roj.text.SimpleLineReader;
import roj.text.TextUtil;
import roj.ui.CmdUtil;
import roj.ui.UIUtil;
import roj.util.ByteList;
import roj.util.Helpers;

import java.io.*;
import java.net.SocketException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static roj.mod.Shared.*;

/**
 * FMD Main class
 *
 * @author Roj234
 * @since 2021/6/18 10:51
 */
public final class FMDMain {
    static boolean isCLI;

    @SuppressWarnings("fallthrough")
    public static void main(String[] args) throws IOException, InterruptedException {
        long startTime = System.currentTimeMillis();

        if(!isCLI) {
            Shared.loadProject(true);
            if(args.length == 0) {
                System.out.println("FMD 更快的mod开发环境 " + VERSION + " By Roj234");
                CmdUtil.info("CLI可用指令: build, run, config, reflect, deobf, gc, reload, auto");
                System.out.println();
            }
        }

        if(args.length == 0) {
            if(isCLI) {
                System.out.println();
                return;
            }

            isCLI = true;
            Tokenizer tokenizer = new Tokenizer();
            Map<String, String> shortcuts = Helpers.cast(CONFIG.getOrCreateMap("CLI Shortcuts").unwrap());
            if(shortcuts.isEmpty())
                shortcuts = Collections.emptyMap();

            ArrayList<String> tmp = new ArrayList<>(48);
            o:
            while (true) {
                String input = UIUtil.userInput("> ");
                tokenizer.init(shortcuts.getOrDefault(input, input));
                try {
                    while (tokenizer.hasNext()) {
                        Word w = tokenizer.readWord();
                        if(w.type() == WordPresets.EOF) {
                            CmdUtil.error("指令有误 " + tmp);
                            continue o;
                        }
                        tmp.add(w.val());
                        tokenizer.recycle(w);
                    }
                    main(tmp.toArray(new String[tmp.size()]));
                } catch (Throwable e) {
                    e.printStackTrace();
                }
                tmp.clear();
            }
        }

        int exitCode = 0;

        switch (args[0]) {
            case "b":
            case "build":
                exitCode = build(buildArgs(args));
                break;
            case "r":
            case "run":
                exitCode = run(buildArgs(args));
                break;
            case "changeVersion":
                try {
                    exitCode = changeVersion();
                } finally {
                    Shared.singletonUnlock();
                }
                break;
            case "f2m":
                exitCode = forgeToMcp(args);
                break;
            case "c":
            case "config":
                exitCode = config(args, project);
                break;
            case "d":
            case "deobf":
                exitCode = deobf(args);
                break;
            case "ref":
            case "reflect":
                ReflectTool.start(!isCLI);
                break;
            case "at":
            case "preAT":
                exitCode = preAT(new UIWarp());
                break;
            case "a":
            case "auto":
                assert isCLI;
                if (args.length < 2) {
                    System.out.println("auto <true/false>");
                    break;
                }
                AutoCompile.setEnabled(Boolean.parseBoolean(args[1]));
                break;
            case "k":
            case "kill":
                assert isCLI;
                if (MCLauncher.task != null && !MCLauncher.task.isDone()) {
                    MCLauncher.task.cancel(true);
                }
                break;
            case "re":
                main(new String[] {"kill"});
                main(new String[] {"run zl"});
                break;
            case "reload":
                if(isCLI) {
                    CmdUtil.warning("重新加载映射表...");
                    mapperFwd.clear();
                    loadMapper();
                }
                break;
            case "download": {
                File downloadPath;
                String url;
                if(args.length != 3) {
                    downloadPath = new File(UIUtil.userInput("下载保存位置: "));
                    String ua = UIUtil.userInput("UA(可选): ");
                    if(!ua.equals(""))
                        FileUtil.userAgent = ua;
                    url = UIUtil.userInput("网址: ");
                } else {
                    downloadPath = new File(args[1]);
                    url = args[2];
                }

                if(!downloadPath.isDirectory() && !downloadPath.mkdirs()) {
                    CmdUtil.warning("下载目录不存在且无法创建");
                    exitCode = -1;
                    break;
                }

                try {
                    Downloader.downloadMTD(url, downloadPath).waitFor();
                } catch (IOException e) {
                    CmdUtil.warning("文件下载失败, 您可以重试，断点数据已经保存");
                    exitCode = -1;
                }
            }
            break;
            case "gc":
                ATHelper.gc();
                System.runFinalization();
                System.gc();
                System.runFinalization();
                System.gc();
            break;
            default:
                CmdUtil.warning("参数错误");
        }

        if(isCLI) return;

        long costTime = System.currentTimeMillis() - startTime;
        CmdUtil.info("主线程运行时长" + ((double)costTime / 1000d));
        if(exitCode != 0)
            System.exit(exitCode);
    }

    private static int forgeToMcp(File mcpConfig, File mcpFile, Map<String, Map<String, List<String>>> paramMap, File target) throws IOException {
        MappingHelper helper = new MappingHelper(true);
        helper.readMcpConfig(mcpConfig);
        //helper.extractNotch2Srg_McpConf(new File(BASE, "Notch2Srg.srg"));

        File log = new File(BASE, "parse_mcp.log");
        redirectOutput(log, () -> {
            try {
                if(paramMap == null) {
                    helper.parseMCP(mcpFile, null);
                } else {
                    Map<String, String> origPM = new MyHashMap<>(1000);
                    helper.parseMCP(mcpFile, origPM);
                    helper.MCP_optimizeParamMap(origPM, paramMap);
                }
                helper.extractMcp2Srg_MCP(target);
            } catch (IOException e) {
                CmdUtil.warning("IO异常 ", e);
            }
        });

        int warnings = 1;
        try(FileInputStream fis = new FileInputStream(log)) {
            warnings = new SimpleLineReader(fis).size();
        } catch (IOException ignored) {}
        if(warnings > 1)
            CmdUtil.warning("忽略了 " + (warnings - 1) + " 个警告, 详情查看 " + log.getAbsolutePath());

        return 0;
    }

    public static int forgeToMcp(String[] args) throws IOException {
        File mcpConfig = null;
        File mcpFile = null;
        {

            if (args.length > 1) {
                mcpConfig = new File(args[1]);
            }
            if (mcpConfig == null || !mcpConfig.isFile()) {
                mcpConfig = UIUtil.readFile("下载的mcp_config_xxx.zip (不要解压!!!!!)");
            }
            String path = mcpConfig.getAbsolutePath();
            int i = path.lastIndexOf('-');
            String maybeVersion = null;
            if (i != -1) {
                maybeVersion = path.substring(i + 1, path.lastIndexOf('.'));
            }

            if (args.length > 2) {
                mcpFile = new File(args[2]);
            }
            if (mcpFile == null || !mcpFile.isFile()) {
                mcpFile = UIUtil.readFile("下载的mcp_stable/snapshot_xxx.zip (不要解压!!!!!)");
            }
            final String path1 = mcpFile.getAbsolutePath();
            i = path1.lastIndexOf('-');
            if (i != -1) {
                String maybeVersion2 = path1.substring(i + 1, path1.lastIndexOf('.'));
                if (!maybeVersion2.equals(maybeVersion)) {
                    CmdUtil.warning("警告! 您下载的MCP版本可能不对! " + maybeVersion + " <=> " + maybeVersion2);
                    CmdUtil.warning("继续请按回车");
                    UIUtil.br.readLine();
                }
            }
        }
        return forgeToMcp(mcpConfig, mcpFile, null, new File(BASE, "Mcp2Srg.srg"));
    }

    public static int deobf(String[] args) throws IOException, InterruptedException {
        List<File> files = new ArrayList<>();

        if(args.length > 1) {
            for (int i = 1; i < args.length; i++)
                files.add(new File(args[i]));
        }
        if(files.isEmpty()) {
            files = Collections.singletonList(UIUtil.readFile("文件"));
        }

        ConstMapper rev = loadReverseMapper();

        MyHashMap<String, byte[]> res = new MyHashMap<>(400);

        ConstMapper.State state = new ConstMapper.State();

        for (int i = files.size() - 1; i >= 0; i--) {
            File file = files.get(i);
            List<Context> list = Util.ctxFromZip(file, StandardCharsets.UTF_8, res);

            String path = file.getAbsolutePath();
            int index = path.lastIndexOf('.');
            File out = index == -1 ? new File(path + "-结果") : new File(path.substring(0, index) + "-结果.jar");
            ZipFileWriter zfw = new ZipFileWriter(out);

            ExecutionTask task = Task.pushRunnable(new ResWriter(zfw, res));

            rev.remap(DEBUG, list);
            rev.getExtendedSuperList().add(rev.snapshot(state));

            if (!isForgeMap) {
                new CodeMapper(rev).remap(DEBUG, list);
            }

            try {
                task.get();
            } catch (ExecutionException e) {
                e.printStackTrace();
            }

            for (int j = 0; j < list.size(); j++) {
                Context ctx = list.get(j);
                zfw.writeNamed(ctx.getFileName(), ctx.get());
            }
            zfw.finish();

            res.clear();
        }

        CmdUtil.success("操作成功完成!");

        return 0;
    }

    public static int preAT(UIWarp helper) throws IOException {
        watcher.reset();
        ATHelper.gc();

        Map<String, Collection<String>> map = AccessTransformer.getTransforms();
        map.clear();

        readTextList((s) -> {
            List<String> tmp = TextUtil.split(new ArrayList<>(), s, ' ', 2);
            if (tmp.size() < 2) {
                CmdUtil.warning("Unknown entry " + s);
            }
            AccessTransformer.add(tmp.get(0), tmp.get(1));
        }, "预AT.编译期");
        loadPreAT(null);

        if(map.isEmpty()) {
            CmdUtil.info("没有找到AT");
            return 0;
        }

        boolean clearPkgInfo = helper.getBoolean("清除'@XXXNonnullByDefault'注解: (T/F)");

        Map<String, String> className = new MyHashMap<>();
        Map<String, Collection<String>> openSubClasses = new MyHashMap<>();

        for (String s : map.keySet()) {
            String s1;
            className.put(s1 = s.replace('.', '/') + ".class", s);
            s = s1;

            int index = s.lastIndexOf('$');
            if(index >= 0) {
                String fatherName = s.substring(0, index);
                String f2;
                className.put(f2 = fatherName + ".class", fatherName.replace('/', '.'));
                openSubClasses.computeIfAbsent(f2, Helpers.fnLinkedList()).add(s.substring(0, s.length() - 6));
            }
        }

        File jarFile = new File(BASE, "class/" + MC_BINARY + ".jar");
        File tmpFile;
        if(FileUtil.checkTotalWritePermission(jarFile)) {
            tmpFile = jarFile;
        } else {
            FileUtil.copyFile(jarFile, tmpFile = new File(BASE, "class/" + MC_BINARY + ".jar.tmp"));
        }

        File backupFile = new File(BASE, "class/" + MC_BINARY + ".jar.bak");
        if(!backupFile.isFile()) {
            FileUtil.copyFile(jarFile, backupFile);
        } else {
            FileUtil.copyFile(backupFile, tmpFile);
        }

        MutableZipFile mz = new MutableZipFile(tmpFile);
        ZipFile origZip = new ZipFile(backupFile);

        Enumeration<? extends ZipEntry> en = origZip.entries();
        while (en.hasMoreElements()) {
            ZipEntry ze = en.nextElement();
            if(ze.isDirectory())
                continue;

            if(clearPkgInfo && ze.getName().endsWith("package-info.class")) {
                mz.put(ze.getName(), null);
                continue;
            }

            String name = className.remove(ze.getName());
            if(name != null) {
                byte[] code = AccessTransformer.transform(name, IOUtil.read(origZip.getInputStream(ze)));
                Collection<String> subs = openSubClasses.remove(ze.getName());
                if(subs != null)
                    code = AccessTransformer.openSubClass(code, subs);

                mz.put(ze.getName(), new ByteList(code));

                CmdUtil.success("转换 " + ze.getName());
            }
        }
        origZip.close();
        mz.store();

        if(!className.isEmpty()) {
            CmdUtil.warning("这些class没有找到: " + className.keySet());
        }

        if(jarFile != tmpFile && ((jarFile.isFile() && !jarFile.delete()) || !tmpFile.renameTo(jarFile)))
            CmdUtil.warning("请手动重命名 " + MC_BINARY + ".jar.tmp为.jar");
        else
            CmdUtil.success("完毕");

        return 0;
    }

    // region PreAT.Util

    private static void loadPreAT(Project cfg) {
        if(cfg != null) {
            if (cfg.atConfigPathStr == null)
                return;
            CMapping mapping = CONFIG.getDot("预AT.编译期+运行期").asMap().get(cfg.name).asMap();

            loadATMap(mapping, false);
        } else {
            Shared.loadSrg2Mcp();
            for(CEntry ce : CONFIG.getDot("预AT.编译期+运行期").asMap().values()) {
                if(ce.getType() == Type.MAP)
                    loadATMap(ce.asMap(), true);
            }
        }
    }

    private static void loadATMap(CMapping at, boolean tr) {
        if(at.size() == 0)
            return;

        for(Map.Entry<String, CEntry> entry : at.entrySet()) {
            String k = entry.getKey();

            CEntry ce = entry.getValue();
            switch (ce.getType()) {
                case LIST:
                    List<CEntry> rl = ce.asList().raw();
                    for (int i = 0; i < rl.size(); i++) {
                        CEntry ce1 = rl.get(i);
                        AccessTransformer.add(k, tr ? translateSeg(ce1) : ce1.asString());
                    }
                    break;
                case STRING:
                    AccessTransformer.add(k, tr ? translateSeg(ce) : ce.asString());
                    break;
            }
        }
    }

    private static String translateSeg(CEntry ce1) {
        String s = TextUtil.split(new ArrayList<>(2), ce1.asString(), ' ').get(0);
        int i = s.lastIndexOf('|');
        if(i != -1) {
            String s1 = s.substring(0, i);
            s = srg2mcp.getOrDefault(s1, s1) + s.substring(i);
        } else {
            s = srg2mcp.getOrDefault(s, s);
        }
        return s;
    }

    // endregion

    public static int config(String[] args, Project p) throws IOException {
        List<File> files = FileUtil.findAllFiles(PROJECTS_DIR, (f) -> !f.getName().equalsIgnoreCase("index.json"));
        if(args.length > 2) {
            File path = new File(args[2] + ".json");
            if(!path.isFile()) {
                path = new File(BASE, "config/" + args[2] + ".json");
                if(!path.isFile()) {
                    CmdUtil.error("参数错误: 文件不存在");
                    return -1;
                }
            }
            files.clear();
            files.add(path);
        }

        switch (args.length > 1 ? args[1] : "") {
            case "edit":
                editConfig(files);
                break;
            case "select":
                CmdUtil.info("当前的配置文件: " + (p == null ? "无" : p.getFile()));
                System.out.println();

                if(files.isEmpty()) {
                    CmdUtil.info("没有配置文件! 创建默认配置...");
                    File file;
                    editConfig0(file = new File(PROJECTS_DIR, "default.json"));
                    files = Collections.singletonList(file);
                }

                File selected = files.get(UIUtil.selectOneFile(files, "配置文件"));

                Shared.setProject(selected.getName().substring(0, selected.getName().lastIndexOf('.')));
                CmdUtil.success("配置文件已选择: " + selected);
                break;
            default:
                CmdUtil.error("用法 config <edit/select>");
                return -1;
        }
        return 0;
    }

    private static void editConfig(List<File> files) throws IOException {
        if(!files.isEmpty())
            CmdUtil.warning("请选择需要编辑的配置文件");

        File newCreate;
        files.add(newCreate = new File("创建一个"));

        File newConfig = files.get(UIUtil.selectOneFile(files, "配置文件"));
        if(newConfig == newCreate) {
            do {
                String fn = UIUtil.userInput("请输入新文件名然后按下回车: ");

                if(fn.equalsIgnoreCase("index.json")) {
                    CmdUtil.error("不能创建此文件名!");
                    continue;
                }

                newConfig = new File(BASE, "/config/" + fn.replace(' ', '_').replace(' ', '_') + ".json");

                if (newConfig.exists()) {
                    CmdUtil.error("文件" + newConfig + "已存在!");
                } else {
                    break;
                }
            } while(true);
        }

        editConfig0(newConfig);
    }

    private static void editConfig0(File file) throws IOException {
        Project conf = Project.load(file.getName().substring(0, file.getName().lastIndexOf('.')));
        conf.version = UIUtil.userInput("请输入项目(mod)版本 并回车: ");
        conf.atName = UIUtil.userInput("请输入AccessTransformer配置(xxx_at.cfg)文件名 并回车(关闭留空): ");

        String charset;
        do {
            charset = UIUtil.userInput("请输入编码(默认UTF-8) 并回车: ");
            if(charset.equals(""))
                charset = "UTF-8";
        } while (!hasCharset(charset));
        conf.charset = Charset.forName(charset);

        File path = new File(BASE.getAbsolutePath() + File.separatorChar + "projects" + File.separatorChar + conf.name);

        if(!path.isDirectory() && path.mkdirs()) {
            ZipUtil.unzip(BASE.getAbsolutePath() + "/util/default.zip", path.getAbsolutePath() + '/');
        }

        conf.save();
    }

    // region EditConfig.Util

    private static boolean hasCharset(String charset) {
        try {
            Charset.forName(charset);
            return true;
        } catch (UnsupportedCharsetException e) {
            CmdUtil.warning("字符集不存在!");
            return false;
        }
    }

    // endregion

    public static int run(Map<String, Object> args) throws IOException {
        MCLauncher.load();
        if (MCLauncher.task != null && !MCLauncher.task.isDone()) {
            if (UIUtil.readBoolean("MC没有退出,是否结束进程?")) {
                MCLauncher.task.cancel(true);
                MCLauncher.task = null;
            } else {
                return -1;
            }
        }

        CMapping mc_conf = MCLauncher.config.get("mc_conf").asMap();
        if(mc_conf.size() == 0) {
            CmdUtil.error("启动配置不存在，无法启动，请重新安装或在启动器内选择版本");
            return -1;
        }

        File dest = new File(mc_conf.getString("root") + "/mods/");

        Shared.singletonLock();
        AutoCompile.beforeCompile();
        int v = -1;
        try {
            v = compile(args, project, BASE, 0);
        } finally {
            Shared.singletonUnlock();
            AutoCompile.afterCompile(v);
        }

        if (v < 0) return v;

        for (Project proj : project.getAllDependencies()) {
            copy4run(dest, proj);
        }
        copy4run(dest, project);

        boolean asyncRun = CONFIG.getBool("异步运行MC");

        if(CONFIG.getBool("启用热重载")) {
            asyncRun = true;

            int port = 0xFFFF & CONFIG.getInteger("重载端口");
            if (port == 0) port = 4485;

            CEntry jvm = mc_conf.get("jvmArg");
            if (jvm.getType() == Type.STRING) {
                CList list = new CList(2);
                list.add(jvm.asString());
                list.add("-javaagent:" + new File(BASE, "util/FMD-agent.jar").getAbsolutePath() + "=" + port);

                mc_conf.put("jvmArg", list);

                if(DEBUG) CmdUtil.info("重载工具已在端口 " + port + " 上启动");
            }
        }

        MCLauncher.clearLogs(null);
        if (asyncRun) {
            Task.pushTask(MCLauncher.task = new RunMinecraftTask(true));
            return 0;
        } else {
            return MCLauncher.runClient(mc_conf, 3, null);
        }
    }

    private static void copy4run(File dest, Project p) throws IOException {
        File src = new File(BASE, p.name + '-' + p.version + ".jar");
        File dst = new File(dest, p.name + ".jar");
        FileUtil.copyFile(src, dst);
    }

    public static int build(Map<String, Object> args) throws IOException {
        if(hotReload != null) args.put("$$HR$$", new ArrayList<>());

        Shared.singletonLock();
        AutoCompile.beforeCompile();
        int v = -1;
        try {
            v = compile(args, project, BASE, 0);
        } finally {
            Shared.singletonUnlock();
            AutoCompile.afterCompile(v);
        }

        List<ConstantData> modified = Helpers.cast(args.get("$$HR$$"));
        if(modified != null && !modified.isEmpty()) {
            hotReload.sendChanges(modified);
            if(DEBUG) CmdUtil.success("发送重载请求");
        }

        return v;
    }

    private static void executeCommand(Project proj, File base) throws IOException {
        String jarName = proj.name + '-' + proj.version + ".jar";
        String jarPath = base.getAbsolutePath() + '/' + jarName;

        CMapping cmd = CONFIG.get("编译成功后执行指令").asMap();
        CList list = cmd.get("**").asList();
        ex(list, jarPath);
        list = (cmd.containsKey(proj.name) ? cmd.get(proj.name) : cmd.get("*")).asList();
        ex(list, jarPath);
    }

    private static void ex(CList suc, String jarPath) throws IOException {
        for (int i = 0; i < suc.size(); i++) {
            Runtime.getRuntime().exec(suc.get(i).asString().replace("%jar", jarPath).replace("%name", project.name));
        }
    }

    /**
     * @param flag Bit 1 : run (NoVersion) , Bit 2 : dependency mode
     */
    private static int compile(Map<String, ?> args, Project p, File jarDest, int flag) throws IOException {
        if((flag & 2) == 0) {
            for (Project proj : p.getAllDependencies()) {
                if(compile(args, proj, jarDest, flag | 2) < 0) {
                    CmdUtil.info("前置编译失败");
                    return -1;
                }
            }
        }

        boolean increment = args.containsKey("zl");
        long time = System.currentTimeMillis();

        // region 检测代码更改

        File source = p.srcPath;
        if(!source.isDirectory()) {
            CmdUtil.warning("源码目录 " + source.getAbsolutePath() + " 不存在");
            return -1;
        }

        List<File> files = null;
        if(increment) {
            MyHashSet<String> set = watcher.getModified(p, FileWatcher.ID_SRC);
            if(!set.contains(null)) {
                files = new ArrayList<>(set.size());
                synchronized (set) {
                    for (String s : set) {
                        files.add(new File(s));
                    }
                    if (DEBUG) System.out.println("FileWatcher.getSrc(): " + set);
                    //set.clear();
                }
            }
        }

        long stamp = p.binJar.lastModified();

        if(files == null) {
            files = FileUtil.findAllFiles(source, FileFilter.INST.reset(stamp, increment ? FileFilter.F_SRC_TIME : FileFilter.F_SRC_ANNO));
            if (DEBUG) System.out.println("FileFilter.getSrc(): " + (files.size() < 100 ? files : files.subList(0, 100)));
        }

        if(DEBUG)
            System.out.println("检测更改 " + (System.currentTimeMillis() - time));

        // endregion
        // region 自动备份源码

        if (CONFIG.getBool("自动备份源码") && !files.isEmpty()) {
            ZipOutput src = p.srcFile;
            try {
                src.begin(!increment);
            } catch (Throwable e) {
                CmdUtil.warning("压缩遇到了一些问题", e);
            }
            String srcPath = source.getAbsolutePath();
            for (int i = 0; i < files.size(); i++) {
                File file = files.get(i);
                src.set(file.getAbsolutePath()
                            .substring(srcPath.length() + 1)
                            .replace(File.separatorChar, '/'),
                        new FileInputStream(file));
            }
            try {
                src.end();
            } catch (Throwable e) {
                CmdUtil.warning("压缩遇到了一些问题", e);
            }

            if(DEBUG)
                System.out.println("备份源码 " + (System.currentTimeMillis() - time));
        }

        // endregion
        // region 检测权限转换标记

        if(increment) {
            for (int i = 0; i < files.size(); i++) {
                File file = files.get(i);
                if (FileFilter.checkATComments(file)) {
                    List<CmtATEntry> ent = FileFilter.cmtEntries;
                    for (int j = 0; j < ent.size(); j++) {
                        if (!p.atEntryCache.contains(ent.get(j))) {
                            ent.clear();

                            CmdUtil.warning("找到AT注解, 使用全量编译");
                            files = FileUtil.findAllFiles(source, FileFilter.INST.reset(0, FileFilter.F_SRC_ANNO));
                            increment = false;
                            break;
                        }
                    }
                    ent.clear();
                }
            }
        }

        // endregion

        File jarFile = new File(jarDest, p.name + '-' + p.version + ".jar");
        if (!ensureWritable(jarFile)) return -1;

        // region 无代码更改
        if(files.isEmpty()) {
            p.registerWatcher();
            if (increment) {
                if (!jarFile.isFile()) {
                    CmdUtil.warning("输出jar不存在, 若只有资源文件修改, 请使用全量编译");
                    return -1;
                }

                ZipOutput zo1 = updateDst(p, jarFile);

                try {
                    zo1.begin(false);

                    AbstractExecutionTask task = p.getResourceTask(1);
                    Project.resourceFilter.reset(stamp, FileFilter.F_RES_TIME);
                    task.calculate();
                    task.get();
                } catch (Throwable e) {
                    CmdUtil.warning("资源写入失败", e);
                    return -1;
                } finally {
                    try {
                        zo1.close();
                    } catch (Throwable e) {
                        CmdUtil.warning("压缩遇到了一些问题", e);
                    }
                }
            }
            if ((flag & 3) == 0)
                CmdUtil.info("无源文件");
            try {
                executeCommand(p, BASE);
            } catch (IOException e) {
                CmdUtil.warning("无法执行指令", e);
            }
            return 0;
        }
        // endregion
        // region 应用权限转换

        if(!FileFilter.cmtEntries.isEmpty()) {
            if(p.atName.isEmpty()) {
                CmdUtil.error(p.name + ": 使用了AT注解系统,却没有设置atConfig");
                return -1;
            }

            p.atEntryCache.clear();
            p.atEntryCache.addAll(FileFilter.cmtEntries);

            Map<String, Collection<String>> map = AccessTransformer.getTransforms();

            map.clear();
            loadPreAT(p);

            CharList atData = new CharList();
            for (Map.Entry<String, Collection<String>> entry : map.entrySet()) {
                for (String val : entry.getValue()) {
                    atData.append("public-f ").append(entry.getKey()).append(' ').append(val).append('\n');
                }
            }
            map.clear();

            List<FileFilter.CmtATEntry> cmt = FileFilter.cmtEntries;
            for (int i = 0; i < cmt.size(); i++) {
                FileFilter.CmtATEntry entry = cmt.get(i);
                if (!entry.compile) {
                    for (String val : entry.value) {
                        atData.append("public-f ").append(entry.clazz).append(' ').append(val).append('\n');
                    }
                }
                map.computeIfAbsent(entry.clazz, Helpers.fnMyHashSet()).addAll(entry.value);
            }

            try(FileOutputStream fos = new FileOutputStream(p.atConfigPathStr)) {
                IOUtil.SharedCoder.get().encodeTo(atData, fos);
            }

            ATHelper.init(p.name, map);
        }

        // endregion

        boolean canIncrementWrite = increment & jarFile.isFile() & p.state != null;

        ZipOutput zo1 = updateDst(p, jarFile);

        try {
            zo1.begin(!canIncrementWrite);
        } catch (Throwable e) {
            CmdUtil.warning("压缩遇到了一些问题", e);
        }

        // region 更新资源文件

        AbstractExecutionTask writeRes = p.getResourceTask(increment ? 1 : 0);
        Project.resourceFilter.reset(stamp, canIncrementWrite ? FileFilter.F_RES_TIME : FileFilter.F_RES);
        Task.pushTask(writeRes);

        // endregion
        // region 制备依赖

        CharList libBuf = new CharList(200);

        if(p.binJar.length() > 0 && increment) {
            libBuf.append(p.binJar.getAbsolutePath()).append(File.pathSeparatorChar);
        }

        libBuf.append(ATHelper.getJar(p.name).getAbsolutePath()).append(File.pathSeparatorChar);

        List<Project> dependencies = p.getAllDependencies();
        for (int i = 0; i < dependencies.size(); i++) {
            libBuf.append(dependencies.get(i).binJar.getAbsolutePath()).append(File.pathSeparatorChar);
        }
        libBuf.append(getLibClasses());

        // endregion

        SimpleList<String> options = CONFIG.get("编译参数").asList().asStringList();
        options.addAll("-cp", libBuf.toString(), "-encoding", p.charset.name());

        Compiler.showErrorCode(args.containsKey("showErrorCode"));
        if(p.compiler.compile(options, files)) {
            try {
                writeRes.get();
            } catch (ExecutionException | InterruptedException e) {
                CmdUtil.warning("资源读取失败", e);
            }

            if(DEBUG)
                System.out.println("编译完成 " + (System.currentTimeMillis() - time));

            MyHashMap<String, String> resources = p.resources;

            List<ByteListOutput> outputs = p.compiler.getCompiled();
            List<Context> list = Helpers.cast(outputs);
            for (int i = 0; i < outputs.size(); i++) {
                ByteListOutput out = outputs.get(i);
                if (resources.remove(out.getName()) != null) {
                    CmdUtil.warning("资源与类重复 " + out.getName());
                }

                list.set(i, new Context(out.getName(), out.getOutput()));
            }

            writeRes = p.getResourceTask(2);
            Task.pushTask(writeRes);

            int compiledCount = list.size();

            ZipOutput stampZip = p.binFile;
            try {
                stampZip.begin(!increment);
            } catch (Throwable e) {
                CmdUtil.warning("压缩文件有错误,请尝试全量", e);
                return -1;
            }

            for (int i = 0; i < list.size(); i++) {
                Context out = list.get(i);
                stampZip.set(out.getFileName(), out);
            }

            if (increment & !canIncrementWrite) {
                MyHashSet<String> changed = new MyHashSet<>(list.size());
                for (int i = 0; i < list.size(); i++) {
                    changed.add(list.get(i).getFileName());
                }

                MutableZipFile mzf = stampZip.getMZF();
                boolean isOpen = mzf.isOpen();
                if (!isOpen) mzf.reopen();
                ByteList buf = IOUtil.getSharedByteBuf();
                for (MutableZipFile.EFile file : mzf.getEntries().values()) {
                    if (!changed.contains(file.getName())) {
                        Context ctx = new Context(file.getName(), mzf.get(file, buf).toByteArray());
                        list.add(ctx);
                        buf.clear();
                    }
                }
                if (!isOpen) mzf.tClose();
            }

            try {
                stampZip.end();
            } catch (Throwable e) {
                CmdUtil.warning("压缩遇到了一些问题", e);
            }

            if(DEBUG)
                System.out.println("代码处理完成 " + (System.currentTimeMillis() - time));

            zo1.setComment("FMD " + VERSION + "\r\nBy Roj234 @ https://www.github.com/roj234/rojlib");

            // region 映射

            List<State> depStates = mapperFwd.getExtendedSuperList();
            depStates.clear();
            for (int i = 0; i < dependencies.size(); i++) {
                depStates.add(dependencies.get(i).state);
            }

            if (canIncrementWrite) {
                mapperFwd.state(p.state);
                mapperFwd.remapIncrement(list);
            } else {
                mapperFwd.remap(false, list);
            }
            p.state = mapperFwd.snapshot(p.state);

            if(!isForgeMap) {
                new CodeMapper(mapperFwd).remap(false, list);
            }

            if(DEBUG)
                System.out.println("映射完成 " + (System.currentTimeMillis() - time));

            // endregion
            // region 写入映射结果

            try {
                // 等待资源写入
                writeRes.get();
            } catch (ExecutionException | InterruptedException e) {
                CmdUtil.warning("资源写入失败", e);
            }

            if(increment) {
                List<ConstantData> modified = Helpers.cast(args.get("$$HR$$"));
                if(modified != null) {
                    for (int i = 0; i < compiledCount; i++) {
                        modified.add(list.get(i).getData());
                    }
                }
            } else {
                args.remove("$$HR$$");
            }

            for (int i = 0; i < list.size(); i++) {
                Context ctx = list.get(i);
                zo1.set(ctx.getFileName(), ctx);
            }

            // endregion

            try {
                zo1.end();
            } catch (Throwable e) {
                CmdUtil.warning("压缩遇到了一些问题", e);
            }

            CmdUtil.success("编译成功! " + (System.currentTimeMillis() - time) + "ms");

            if (!p.binJar.setLastModified(args.containsKey("dbg-nots") ? stamp : time)) {
                throw new IOException("设置时间戳失败!");
            }

            p.registerWatcher();

            try {
                executeCommand(p, BASE);
            } catch (IOException e) {
                CmdUtil.warning("无法执行指令", e);
            }

            return 1;
        }

        return -1;
    }

    private static ZipOutput updateDst(Project p, File jarFile) throws IOException {
        ZipOutput zo1 = p.dstFile;
        if (zo1 == null || !zo1.file.getAbsolutePath().equals(jarFile.getAbsolutePath())) {
            if (zo1 != null) {
                zo1.close();
            }
            p.dstFile = zo1 = new ZipOutput(jarFile);
            zo1.setCompress(true);
        }
        return zo1;
    }

    private static boolean ensureWritable(File jarFile) {
        int amount = 30 * 20;
        while (jarFile.isFile() && !FileUtil.checkTotalWritePermission(jarFile) && amount > 0) {
            if ((amount % 100) == 0) CmdUtil.warning("输出jar已被锁定, 请在30秒内解除对它的锁定，否则编译无法继续");
            LockSupport.parkNanos(50_000_000L);
            amount--;
        }
        return amount != 0;
    }

    // region Build.util

    static long lastLibHash;
    static CharList libClasses;

    private static CharList getLibClasses() {
        File lib = new File(BASE, "/class/");
        if(!lib.isDirectory())
            return new CharList();

        List<File> fs = Arrays.asList(lib.listFiles());

        CharList sb = libClasses;
        if(sb == null)
            libClasses = sb = new CharList();
        else if(Util.libHash(fs) == lastLibHash)
            return sb;
        sb.clear();

        for (int i = 0; i < fs.size(); i++) {
            File file = fs.get(i);
            if (!(file.getName().endsWith(".zip") || file.getName().endsWith(".jar")) || file.length() == 0) {
                continue;
            }
            try (ZipFile zf = new ZipFile(file)) {
                if (zf.size() == 0)
                    CmdUtil.warning(file.getPath() + " 是空的");
            } catch (Throwable e) {
                CmdUtil.error(file.getPath() + " 不是ZIP压缩文件", e);
                if (!file.renameTo(new File(file.getAbsolutePath() + ".err"))) {
                    throw new RuntimeException("未指定的I/O错误");
                } else {
                    CmdUtil.info("文件已被自动重命名为.err");
                }
            }

            sb.append(file.getAbsolutePath()).append(File.pathSeparatorChar);
        }
        lastLibHash = Util.libHash(fs);
        sb.setLength(sb.length() - 1);
        return sb;
    }

    // endregion

    private static int changeVersion() throws IOException {
        Shared.singletonLock();

        CMapping cfgGen = CONFIG.get("通用").asMap();

        File mcRoot = new File(cfgGen.getString("MC目录"));
        if (!mcRoot.isDirectory())
            mcRoot = UIUtil.readFile("MC目录(.minecraft)");
        if (!new File(mcRoot, "/versions/").isDirectory())
            mcRoot = new File(mcRoot, ".minecraft");

        List<File> versions = MCLauncher.findVersions(new File(mcRoot, "/versions/"));

        File mcJson = null;
        if(versions.isEmpty()) {
            CmdUtil.error("没有找到任何MC版本！请确认目录名是.minecraft", true);
            return -1;
        } else {
            String versionJson = CONFIG.getString("MC版本JSON");
            if(!versionJson.isEmpty()) {
                for (int i = 0; i < versions.size(); i++) {
                    File file = versions.get(i);
                    if (file.getName().equals(versionJson)) {
                        mcJson = file;
                    }
                }
            }
            if(mcJson == null)
                mcJson = versions.get(UIUtil.selectOneFile(versions, "MC版本"));
        }

        return changeVersion(mcRoot, mcJson, new UIWarp());
    }

    @SuppressWarnings("unchecked")
    static int changeVersion(File mcRoot, File mcJson, UIWarp gui) throws IOException {
        watcher.terminate();
        mcRoot = mcRoot.getAbsoluteFile();

        CMapping cfgGen = CONFIG.get("通用").asMap();
        CMapping cfgFMD = CONFIG;

        MyHashSet<String> skipped = new MyHashSet<>();
        readTextList(skipped::add, "忽略的libraries");
        final File nativePath = new File(mcJson.getParentFile(), "$natives/");
        Object[] result = MCLauncher.getRunConf(mcRoot, mcJson, nativePath, skipped, true, cfgGen);
        if(result == null)
            return -1;

        CMapping jsonDesc = (CMapping) result[3];

        if(!jsonDesc.getString("type").equals("release")) {
            CmdUtil.error("不幸的是我们目前只支持发行版, 不支持快照");
            return -1;
        }

        CMapping mc_conf = (CMapping) result[0];
        mc_conf.put("player_name", cfgGen.getString("玩家名字"));
        mc_conf.put("native_path", nativePath.getAbsolutePath());
        Collection<String> libraries = ((Map<String, String>) result[2]).values();
        boolean is113andAbove = (boolean) result[4];
        File mcJar = (File) result[1];

        File mcpSrgPath = new File(BASE, "/util/mcp-srg.srg");
        if(mcpSrgPath.isFile() && !mcpSrgPath.delete()) {
            CmdUtil.error("无法删除旧的映射数据: " + mcpSrgPath.getPath());
            return -1;
        }

        String mcVersion = "";
        if(is113andAbove) {
            mcVersion = jsonDesc.get("clientVersion").asString();
        }
        if(mcVersion.isEmpty()) {
            String name = mcJar.getName();
            mcVersion = name.substring(0, name.lastIndexOf('.'));
            int i = name.lastIndexOf('-');
            if(i != -1)
                mcVersion = mcVersion.substring(0, i);
            mcVersion = mcVersion.replace("-forge", "");
        }

        MCLauncher.load();
        MCLauncher.config.put("mc_conf", mc_conf);
        MCLauncher.config.put("mc_version", jsonDesc.getString("id"));

        String mcpVersion = detectVersion(mcVersion);

        gui.stageInfo(0);
        String newMcp = gui.userInput("自动检测的MCP版本号为: " + mcpVersion + " 无需修改请按回车: ", true).trim();
        if(newMcp.length() > 0)
            mcpVersion = newMcp;

        CMapping downloads = jsonDesc.get("downloads").asMap();

        boolean canDownloadOfficial = downloads.containsKey("client_mappings");
        boolean canDownloadYarn = new Version(mcVersion).compareTo(new Version("1.12")) >= 0;

        if(gui.isConsole()) {
            System.out.println();
            CmdUtil.info("需要提供一个映射表! ");
            CmdUtil.info(" 0: MCP");
            if (canDownloadOfficial) {
                CmdUtil.info(" 1: MC官方");
            }
            if (canDownloadYarn) {
                CmdUtil.info(" 2: YARN (WIP)");
            }
            CmdUtil.info(" 3: 自己提供");
            CmdUtil.info(" 4: 手动下载");
            System.out.println();
        }

        String mcpConfUrl = cfgGen.getString("协议") + cfgGen.getString("forge地址") + "/de/oceanlabs/mcp/mcp_config/" + mcVersion + "/mcp_config-" + mcVersion + ".zip";
        String mirror = cfgGen.getBool("下载MC相关文件使用镜像") ? cfgGen.getString("镜像地址") : null;
        boolean mapUseMirror = cfgFMD.getBool("映射表也使用镜像");

        gui.stageInfo(1, canDownloadOfficial, canDownloadYarn);
        int select = gui.getNumberInRange(0, 5);

        isForgeMap = select == 0/* && mcVersion < 1.17*/;
        Shared.setProject(null);

        switch (select) {
            case 1: {
                if(!canDownloadOfficial) {
                    CmdUtil.error("此版本不支持下载MC官方的映射表");
                    return -1;
                }
            }
            break;
            case 2: {
                if(!canDownloadYarn) {
                    CmdUtil.error("此版本不支持使用Yarn映射表");
                    return -1;
                }
            }
            break;
            case 4: {
                CmdUtil.warning("请手动下载下面几个文件: ");
                CmdUtil.info("第一个文件: 映射表");

                if(canDownloadOfficial && gui.isConsole()) {
                    CmdUtil.info(" 0: 下载MCP");
                    CmdUtil.info(" 1: 下载MC官方");
                }

                CMapping tmp;

                if(canDownloadOfficial)
                    gui.stageInfo(2);
                int num = canDownloadOfficial ? gui.getNumberInRange(0, 2) : 0;
                switch (num) {
                    case 0: {
                        if(!is113andAbove) {
                            CmdUtil.info("  '官方'");
                            CmdUtil.info("    " + cfgGen.getString("协议") + cfgGen.getString("forge地址") + "/de/oceanlabs/mcp/mcp_stable/");
                            CmdUtil.info("    " + cfgGen.getString("协议") + cfgGen.getString("forge地址") + "/de/oceanlabs/mcp/mcp_snapshot/");
                            CmdUtil.info("  '非官方' (速度快)");
                        }

                        CmdUtil.info("    " + cfgFMD.getString("MCP下载根路径"));
                        System.out.println();
                        CmdUtil.warning("来自ForgeGradle? 打开build.gradle");
                        CmdUtil.info("  搜索'// stable_#, 下面就是版本");
                    }
                    break;
                    case 1: {
                        showFileDetail(downloads, "client_mappings", mapUseMirror ? mirror : null);
                    }
                    break;
                }

                System.out.println();
                CmdUtil.warning("不清楚？ https://neutrino.v2mcdev.com/devenvironment/folderintro.html");
                if(is113andAbove) {
                    System.out.println();
                    CmdUtil.info("第二个文件: MCP Config");
                    CmdUtil.info("    " + mcpConfUrl);
                }

                System.out.println();
                CmdUtil.info("第三个文件: MC服务端");
                showFileDetail(downloads, "server", mirror);
                if(is113andAbove)
                    showFileDetail(downloads, "server_mappings", mapUseMirror ? mirror : null);

                System.out.println();
                CmdUtil.info("所有的文件放到tmp文件夹里, 然后重新安装");
                gui.stageInfo(3);
                return -1;
            }
        }

        File mcpConfigFile;
        if(is113andAbove) {
            mcpConfigFile = new File(TMP_DIR, "mcp_config-" + mcVersion + ".zip");

            try {
                MCLauncher.downloadAndVerifyMD5(mcpConfUrl, mcpConfigFile);
            } catch (FileNotFoundException e) {
                CmdUtil.warning("错误: 404  MCP-Config不存在! 请检查镜像地址, 或者手动下载");
                return -1;
            } catch (IOException e) {
                CmdUtil.warning("MCP-Config下载失败 " + mcpConfUrl);
                return -1;
            }
            Proc1_16.handleMCPConfig(mcpConfigFile);
        } else {
            mcpConfigFile = null;
        }

        Map<String, Map<String, List<String>>> paramMap = null;
        File mcpPackFile = null;

        switch (select) {
            case 0: {
                String mcpPackUrl = cfgFMD.getString("MCP下载根路径");

                boolean autoDownloadStable = cfgFMD.getBool("自动下载稳定版的MCP");

                String a = getStableMCPVersion(mcpVersion);
                if(!autoDownloadStable || a == null) {
                    if(a == null) {
                        CmdUtil.warning("不幸的是目前你的版本没有稳定的MCP");
                    }

                    CmdUtil.info("提示: stable-123 => s-123 (稳定版)  snapshot-20201221 => 20201221 (快照版) ");
                    CmdUtil.warning("上面的是示例!! 版本要你自己找!! 不会找的上面按5");
                    gui.stageInfo(4);
                    String subVersion = gui.userInput("请输入使用的MCP版本并按回车: ", false);

                    if (subVersion.startsWith("s-")) {
                        a = subVersion.substring(2) + '-' + mcpVersion;
                        mcpPackUrl += "mcp_stable/" + a + "/mcp_stable-" + a + ".zip";
                    } else {
                        a = subVersion + '-' + mcpVersion;
                        mcpPackUrl += "mcp_snapshot/" + a + "/mcp_snapshot-" + a + ".zip";
                    }

                    mcpPackFile = new File(TMP_DIR, "mcp_" + (subVersion.startsWith("s-") ? "stable" : "snapshot") + '-' + a + ".zip");
                } else {
                    String tmp = "mcp_stable-" + a + '-' + mcpVersion + ".zip";
                    mcpPackUrl += "mcp_stable/" + a  + '-' + mcpVersion + '/' + tmp;
                    mcpPackFile = new File(TMP_DIR, tmp);
                }

                try {
                    MCLauncher.downloadAndVerifyMD5(mcpPackUrl, mcpPackFile);
                } catch (FileNotFoundException e) {
                    CmdUtil.warning("错误: 404 MCP不存在! 请检查你输入的MCP版本, 或者手动下载");
                    return -1;
                } catch (IOException e) {
                    CmdUtil.warning("MCP下载失败 " + mcpPackUrl);
                    return -1;
                }

                if(is113andAbove) {
                    CmdUtil.info("生成MCP-SRG映射表...");
                    paramMap = new MyHashMap<>();
                    forgeToMcp(mcpConfigFile, mcpPackFile, paramMap, mcpSrgPath);
                    CmdUtil.info("生成完毕...");
                }  // 1.12.2 delayed read forge data

            }
            break;
            case 1: {
                try {
                    File clientMap = MCLauncher.downloadMinecraftFile(downloads, "client_mappings", mapUseMirror ? mirror : null);
                    File serverMap = MCLauncher.downloadMinecraftFile(downloads, "server_mappings", mapUseMirror ? mirror : null);

                    CmdUtil.info("生成MCP-SRG映射表...");
                    if(generateMinecraftMapping(mcpConfigFile, clientMap, serverMap) == null) {
                        return -1;
                    }
                    CmdUtil.info("生成完毕...");
                } catch (FileNotFoundException e) {
                    CmdUtil.warning("错误: 404  Mapping不存在! 请检查你的版本json文件或者手动下载");
                    return -1;
                } catch (SocketException e) {
                    CmdUtil.warning("错误: " + e.getMessage() + " 请多次尝试，或手动下载...");
                    CmdUtil.success("提示： 支持断点续传!");
                    return -1;
                }

                break;
            }
            case 2: {
                CmdUtil.warning("若raw.githubusercontent.com无法访问 请自己解决");

                File intermediary = new File(TMP_DIR, "/yarn_" + mcVersion + "_intermediary.txt");
                Waitable w = Downloader.download("https://github.com/FabricMC/intermediary/raw/master/mappings/" + mcVersion + ".tiny", intermediary);

                File map = new File(TMP_DIR, "/yarn_" + mcVersion + "_map.zip");
                Waitable w2 = Downloader.download("https://github.com/FabricMC/yarn/archive/" + mcVersion + ".zip", map);

                w.waitFor();
                w2.waitFor();

                CmdUtil.info("生成MCP-SRG映射表...");

                // Notch -> Yarn
                YarnMapping notch2yarn = new YarnMapping();
                notch2yarn.load(intermediary, map, mcVersion);
                // Yarn -> Notch
                notch2yarn.reverse();

                // Notch -> Srg
                Mapping notch2srg = new Mapping();
                if (is113andAbove) {
                    ZipFile mcz = new ZipFile(mcpConfigFile);
                    ZipEntry ze = mcz.getEntry("config/joined.tsrg");
                    notch2srg.loadMap(mcz.getInputStream(ze), false);
                    mcz.close();
                } else {
                    for (String pkg : libraries) {
                        if (pkg.startsWith("net/minecraftforge")) {
                            Proc1_12.__loadForge(new File(mcRoot, "/libraries/" + pkg), notch2srg);
                            break;
                        }
                    }
                }

                // Yarn -> Srg
                notch2srg.extend(notch2yarn);

                notch2srg.saveMap(mcpSrgPath);

                CmdUtil.info("生成完毕...");
            }
            break;
            case 3:
                File rmcpSrgPath = new File(BASE, "Mcp2Srg.srg");
                if(!rmcpSrgPath.isFile()) {
                    CmdUtil.warning("请将Mcp -> Srg 映射表重命名为Mcp2Srg.srg放到当前目录");
                    return -1;
                }
                FileUtil.copyFile(rmcpSrgPath, mcpSrgPath);
                break;
            default:
                throw new RuntimeException("Impossible!");
        }

        File mcServer = MCLauncher.downloadMinecraftFile(downloads, "server", mirror);

        CmdUtil.info("合并libraries中...请稍后");

        File cache0 = new File(BASE, "/util/remapCache.bin");

        if (cache0.isFile() && !cache0.delete())
            CmdUtil.error("无法删除映射缓存 util/remapCache.bin!", true);

        File librariesPath = new File(mcRoot, "/libraries/");

        Processor proc;
        if(is113andAbove) {
            Object[] files = Proc1_16.find116Files(librariesPath, libraries);

            mc_conf.put("jar", ((File) files[0]).getAbsolutePath());

            proc = new Proc1_16(mcServer, paramMap, files);
        } else {
            proc = new Proc1_12(mcServer, mcJar, mcpSrgPath.isFile() ? null : mcpPackFile);
        }

        mergeLibraries(librariesPath, libraries, proc, is113andAbove);

        MCLauncher.save();

        CmdUtil.success("合并libraries完毕", true);

        int code = proc.getAsInt();
        if(code != 0)
            return code;

        try {
            FileUtil.copyFile(new File(BASE, "class/" + MC_BINARY + ".jar"), new File(BASE, "class/" + MC_BINARY + ".jar.bak"));
        } catch (IOException e) {
            CmdUtil.warning("备份失败", e);
        }

        // clear close() hook
        System.gc();
        System.runFinalization();
        System.gc();
        System.runFinalization();

        preAT(gui);
        return 0;
    }

    // region ChangeVersion.Util

    private static String detectVersion(String version) {
        switch (version) {
            case "1.8.1":
            case "1.8.2":
            case "1.8.3":
            case "1.8.4":
            case "1.8.6":
            case "1.8.7":
                return "1.8";
            case "1.9.1":
            case "1.9.2":
            case "1.9.3":
                return "1.9";
            case "1.10":
            case "1.10.1":
                return "1.10.2";
            case "1.11.1":
            case "1.11.2":
                return "1.11";
            case "1.12.1":
            case "1.12.2":
                return "1.12";
        }
        return version;
    }

    private static String getStableMCPVersion(String version) {
        switch (version) {
            case "1.7.10":
                return "12";
            case "1.8":
                return "18";
            case "1.8.8":
                return "20";
            case "1.8.9":
                return "22";
            case "1.9":
                return "24";
            case "1.9.4":
                return "26";
            case "1.10.2":
                return "29";
            case "1.11":
                return "32";
            case "1.12":
                return "39";
            case "1.13":
                return "43";
            case "1.13.1":
                return "45";
            case "1.13.2":
                return "47";
            case "1.14":
                return "49";
            case "1.14.1":
                return "51";
            case "1.14.2":
                return "53";
            case "1.14.3":
                return "56";
            case "1.14.4":
                return "58";
            case "1.15":
                return "60";
        }
        return null;
    }

    private static void showFileDetail(CMapping download, String id, String mirror) {
        CMapping tmp = download.get(id).asMap();

        String url = tmp.getString("url");

        if(mirror != null) {
            url = url.replace("launchermeta.mojang.com", mirror);
            url = url.replace("launcher.mojang.com", mirror);
        }

        CmdUtil.info("    " + url);
        CmdUtil.info("        文件大小: " + TextUtil.scaledNumber(tmp.getInteger("size")).toUpperCase() + 'B');
        CmdUtil.info("        文件SHA1效验码: " + tmp.getString("sha1"));
        CmdUtil.warning("         注意：这个文件名字要改成 '" + tmp.getString("sha1") + '.' + id +'\'');
    }

    private static File generateMinecraftMapping(File mcpConfig, File clientMap, File serverMap) throws IOException {
        MappingHelper helper = new MappingHelper(true);

        helper.readMcpConfig(mcpConfig);

        MappingHelper.Mojang mojang = helper.new Mojang();

        if(!mojang.read(clientMap, serverMap)) {
            return null;
        }

        File output = new File(BASE, "util/mcp-srg.srg");

        mojang.extract(output);

        return output;
    }

    private static void mergeLibraries(File mcBase, Collection<String> libraries, BiConsumer<Integer, File> biConsumer, boolean is113) throws IOException {
        File dest = new File(BASE,"class/[noread]merged_lib.jar");

        ZipFileWriter zfw = new ZipFileWriter(dest, false);

        MyHashMap<String, String> names = new MyHashMap<>();

        for (String pkg : libraries) {
            File file = new File(mcBase, pkg);
            if(!file.isFile()) {
                CmdUtil.error("依赖不存在: " + pkg);
                continue;
            }
            if(pkg.startsWith("net/minecraftforge") || pkg.startsWith("cpw/minecraftforge")) {
                if(!is113) {
                    biConsumer.accept(0, file);
                    continue;
                } else if(pkg.startsWith("net/minecraftforge/forge/")) {
                    continue;
                }
            }
            if(pkg.startsWith("lzma/lzma")) {
                biConsumer.accept(1, file);
            } else {
                if(pkg.startsWith("com/mojang/patchy") && !_USE_PATCHY) {
                    if(DEBUG)
                        CmdUtil.info("跳过Patchy " + pkg);
                    continue;
                }

                MutableZipFile mzf = new MutableZipFile(file, 0);
                for (MutableZipFile.EFile entry : mzf.getEntries().values()) {
                    if (entry.getName().endsWith(".class")) {
                        String prevPkg = names.get(entry.getName());
                        if(prevPkg != null) {
                            if (!pkg.equals(prevPkg)) {
                                prevPkg = prevPkg.substring(prevPkg.lastIndexOf('/') + 1);
                                pkg = pkg.substring(pkg.lastIndexOf('/') + 1);
                                CmdUtil.warning("重复的 " + entry.getName() + " 在 " + prevPkg + " 和 " + pkg, true);
                            }
                        } else {
                            zfw.write(mzf, entry);

                            names.put(entry.getName(), pkg);
                        }
                    }
                }
                mzf.close();
            }
        }

        zfw.finish();
    }

    private static final boolean _USE_PATCHY = false;

    // endregion
    // region Common.Util

    private static Map<String, Object> buildArgs(String[] args) {
        // args: dbg-nots showErrorCode zl
        MyHashMap<String, Object> ojbk = new MyHashMap<>(args.length);
        for (String arg : args) {
            ojbk.put(arg, null);
        }
        return ojbk;
    }

    public static void readTextList(Consumer<String> set, String key) {
        CEntry m = CONFIG.getDot(key);
        if (m.getType() == Type.LIST) {
            CList list = m.asList();
            List<CEntry> rl = list.raw();
            for (int i = 0; i < rl.size(); i++) {
                CEntry entry = rl.get(i);
                if (entry.getType() == Type.STRING) {
                    String s = entry.asString().trim();
                    if (s.length() > 0)
                        set.accept(s);
                }
            }
        }
    }

    public static void redirectOutput(File file, Runnable action) throws FileNotFoundException {
        PrintStream out = System.out;

        PrintStream ps;
        System.setOut(ps = new PrintStream(new FileOutputStream(file)));

        action.run();

        ps.flush();
        ps.close();
        if(ps.checkError()) {
            System.err.println("Failed to close temporary output stream.");
        }

        System.setOut(out);
    }

    // endregion
}
