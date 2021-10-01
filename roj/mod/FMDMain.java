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

import roj.asm.Parser;
import roj.asm.mapper.CodeMapper;
import roj.asm.mapper.ConstMapper;
import roj.asm.mapper.ConstMapper.State;
import roj.asm.mapper.Util;
import roj.asm.mapper.util.Context;
import roj.asm.mapper.util.ResWriter;
import roj.asm.transform.AccessTransformer;
import roj.asm.tree.ConstantData;
import roj.collect.MyHashMap;
import roj.collect.MyHashSet;
import roj.collect.SimpleList;
import roj.concurrent.task.AbstractExecutionTask;
import roj.concurrent.task.CalculateTask;
import roj.config.data.CEntry;
import roj.config.data.CList;
import roj.config.data.CMapping;
import roj.config.data.Type;
import roj.config.word.Tokenizer;
import roj.config.word.Word;
import roj.config.word.WordPresets;
import roj.io.*;
import roj.math.Version;
import roj.mod.fp.Proc1_12;
import roj.mod.fp.Proc1_16;
import roj.mod.fp.Processor;
import roj.mod.util.MappingHelper;
import roj.text.CharList;
import roj.text.SimpleLineReader;
import roj.text.TextUtil;
import roj.text.crypt.Base64;
import roj.ui.CmdUtil;
import roj.ui.UIUtil;
import roj.util.ByteList;
import roj.util.ByteWriter;
import roj.util.Executable;
import roj.util.Helpers;

import java.io.*;
import java.net.SocketException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static roj.mod.Shared.*;

/**
 * FMD Main class
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/6/18 10:51
 */
public final class FMDMain {
    static boolean isCLI;

    @SuppressWarnings("fallthrough")
    public static void main(String[] args) throws IOException, InterruptedException {
        long startTime = System.currentTimeMillis();

        if(!isCLI) {
            if (!CONF_INDEX.exists()) {
                config(new String[]{"config", "select"}, null);
            }

            Shared.loadConfig(true);
            if(args.length == 0) {
                CmdUtil.error("F", false);
                CmdUtil.success("M", false);
                CmdUtil.fg(CmdUtil.Color.BLUE, true);
                System.out.print("D");
                CmdUtil.fg(CmdUtil.Color.WHITE, true);
                System.out.print("   更快的MOD开发环境 ");
                CmdUtil.reset();
                System.out.print("Ver ");
                CmdUtil.fg(CmdUtil.Color.GREEN, true);
                System.out.println(VERSION);
                CmdUtil.fg(CmdUtil.Color.WHITE, true);
                CmdUtil.bg(CmdUtil.Color.BLUE, true);
                System.out.print("    Powered");
                CmdUtil.fg(CmdUtil.Color.WHITE, false);
                System.out.print(" by ");
                CmdUtil.fg(CmdUtil.Color.YELLOW, true);
                System.out.println("Roj234");
                CmdUtil.info("指令: build, run, changeVersion, f2m, config, reflect, deobf, download, gc, reload");
                System.out.println();
            }
        }

        if(args.length == 0) {
            if(isCLI) {
                CmdUtil.info("指令: build, run, changeVersion, f2m, config, reflect, deobf, download, gc, reload");
                System.out.println();
                return;
            }

            isCLI = true;
            Tokenizer tokenizer = new Tokenizer();
            Map<String, String> shortcuts = Helpers.cast(MAIN_CONFIG.getOrCreateMap("CLI Shortcuts").toNudeObject());
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
            case "build":
                exitCode = build(buildArgs(args));
                break;
            case "run":
                exitCode = run(buildArgs(args));
                break;
            case "changeVersion":
                exitCode = changeVersion();
                break;
            case "f2m":
                exitCode = forgeToMcp(args);
                break;
            case "config":
                exitCode = config(args, currentProject);
                break;
            case "deobf":
                exitCode = deobf(args);
                break;
            case "reflect":
                ReflectTool.start(!isCLI);
                break;
            case "preAT":
                exitCode = preAT(new UIWarp());
                break;
            case "reload":
                if(isCLI) {
                    CmdUtil.warning("重新加载映射表...");
                    mapperFwd.clear();
                    initForwardMapper();
                    mapperRev = null;
                }
                break;
            case "download": {
                File downloadPath;
                String url;
                if(args.length != 3) {
                    downloadPath = new File(UIUtil.userInput("下载保存位置: "));
                    FileUtil.USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/72.0.3626.121 Safari/537.36";
                    String ua = UIUtil.userInput("UA(可选): ");
                    if(!ua.equals(""))
                        FileUtil.USER_AGENT = ua;
                    url = UIUtil.userInput("网址: ");
                } else {
                    downloadPath = new File(args[1]);
                    url = args[2];
                }

                FileUtil.MIN_ASYNC_SIZE = 1024 * 64;
                FileUtil.ENABLE_ENDPOINT_RECOVERY = true;

                if(!downloadPath.isDirectory() && !downloadPath.mkdirs()) {
                    CmdUtil.warning("下载目录不存在且无法创建");
                    exitCode = -1;
                    break;
                }

                try {
                    FileUtil.downloadFileAsync(url, downloadPath).waitFor();
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

        ConstMapper rev = getReverseMapper();

        MyHashMap<String, byte[]> res = new MyHashMap<>(400);

        ConstMapper.State state = new ConstMapper.State();

        for (int i = files.size() - 1; i >= 0; i--) {
            File file = files.get(i);
            List<Context> list = Util.ctxFromZip(file, StandardCharsets.UTF_8, res);

            String path = file.getAbsolutePath();
            int index = path.lastIndexOf('.');
            File out = index == -1 ? new File(path + "-结果") : new File(path.substring(0, index) + "-结果.jar");
            ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(out));

            CalculateTask<Void> task = new CalculateTask<>(new ResWriter(zos, res));
            parallel.pushTask(task);

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

            Util.write(list, zos, true);

            res.clear();
        }

        CmdUtil.success("操作成功完成!");

        return 0;
    }

    public static int preAT(UIWarp helper) throws IOException {
        watcher.reset();

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

        File jarFile = new File(BASE, "class/" + MERGED_FILE_NAME + ".jar");
        File tmpFile;
        if(FileUtil.checkTotalWritePermission(jarFile)) {
            tmpFile = jarFile;
        } else {
            FileUtil.copyFile(jarFile, tmpFile = new File(BASE, "class/" + MERGED_FILE_NAME + ".jar.tmp"));
        }

        File backupFile = new File(BASE, "class/" + MERGED_FILE_NAME + ".jar.bak");
        if(!backupFile.isFile()) {
            FileUtil.copyFile(jarFile, backupFile);
        }

        MutableZipFile mz = new MutableZipFile(tmpFile);
        ZipFile origZip = new ZipFile(backupFile);

        Enumeration<? extends ZipEntry> en = origZip.entries();
        while (en.hasMoreElements()) {
            ZipEntry ze = en.nextElement();
            if(ze.isDirectory())
                continue;

            if(clearPkgInfo && ze.getName().endsWith("package-info.class")) {
                mz.setFileData(ze.getName(), null);
                continue;
            }

            String name = className.remove(ze.getName());
            if(name != null) {
                byte[] code = AccessTransformer.transform(name, IOUtil.read(origZip.getInputStream(ze)));
                Collection<String> subs = openSubClasses.remove(ze.getName());
                if(subs != null)
                    code = AccessTransformer.openSubClass(code, subs);

                mz.setFileData(ze.getName(), new ByteList(code), true);

                CmdUtil.success("转换 " + ze.getName());
            }
        }
        origZip.close();
        mz.store();

        if(!className.isEmpty()) {
            CmdUtil.warning("这些class没有找到: " + className.keySet());
        }

        if(jarFile != tmpFile && ((jarFile.isFile() && !jarFile.delete()) || !tmpFile.renameTo(jarFile)))
            CmdUtil.warning("请手动重命名 " + MERGED_FILE_NAME + ".jar.tmp为.jar");
        else
            CmdUtil.success("完毕");

        return 0;
    }

    // region PreAT.Util

    private static void loadPreAT(Project cfg) {
        if(cfg != null) {
            if (cfg.atConfigPathStr == null)
                return;
            CMapping mapping = MAIN_CONFIG.getDot("预AT.编译期+运行期").asMap().get(cfg.name).asMap();

            loadATMap(mapping, false);
        } else {
            Shared.load_S2M_Map();
            for(CEntry ce : MAIN_CONFIG.getDot("预AT.编译期+运行期").asMap().values()) {
                if(ce.getType().fits(Type.MAP))
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
        String s = TextUtil.split(new ArrayList<>(2), new CharList(), ce1.asString(), ' ').get(0);
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

    public static int config(String[] args, Project project) throws IOException {
        List<File> files = FileUtil.findAllFiles(PROJ_CONF_DIR, (f) -> !f.getName().equalsIgnoreCase("index.json"));
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
                CmdUtil.info("当前的配置文件: " + (project == null ? "无" : project.getFile()));
                System.out.println();

                if(files.isEmpty()) {
                    CmdUtil.info("没有配置文件! 创建默认配置...");
                    File file;
                    editConfig0(file = new File(PROJ_CONF_DIR, "default.json"));
                    files = Collections.singletonList(file);
                }

                File selected = files.get(UIUtil.selectOneFile(files, "配置文件"));

                Shared.setConfig(selected.getName().substring(0, selected.getName().lastIndexOf('.')));
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

    public static int run(Map<String, Object> args) throws IOException, InterruptedException {
        MCLauncher.load();

        CMapping mc_conf = MCLauncher.config.get("mc_conf").asMap();
        if(mc_conf.size() == 0) {
            CmdUtil.error("配置丢失，无法启动");
            return -1;
        }

        File dest = new File(mc_conf.getString("root") + File.separatorChar + "mods" + File.separatorChar);
        if(compile(args, currentProject, dest, 1)) {
            dest = new File(dest, currentProject.name + ".jar");
            if(!dest.isFile()) {
                CmdUtil.warning("目标jar不存在 (3)");
                return -1;
            }

            if(MAIN_CONFIG.getDot("FMD配置.启用热重载").asBool()) {
                File hrTmp = new File(TMP_DIR, "hr");
                if(!hrTmp.isDirectory() && !hrTmp.mkdirs()) {
                    CmdUtil.error("tmp/hr 目录创建失败");
                }

                String jvm = mc_conf.getString("jvmArg");
                CharList cl = Base64.encode(ByteWriter.encodeUTF(hrTmp.getAbsolutePath()), new CharList()).append(' ');
                mc_conf.put("jvmArg", jvm + " -javaagent:" + new File(BASE, "util/FMD-agent.jar").getAbsolutePath() + '=' + cl);

                if(DEBUG)
                    CmdUtil.info("重载配置完毕 " + cl);
            }

            return MCLauncher.runClient(mc_conf, new File(mc_conf.getString("native_path")), 3, null);
        } else {
            return -1;
        }
    }

    public static int build(Map<String, Object> args) throws IOException, InterruptedException {
        if(MAIN_CONFIG.getDot("FMD配置.启用热重载").asBool())
            args.put("_HOT_RELOAD_ENABLE_", Helpers.cast(new ArrayList<>()));

        if(compile(args, currentProject, BASE, 0)) {
            if(args.containsKey("_HOT_RELOAD_ENABLE_")) {
                File hrTmp = new File(TMP_DIR, "hr");
                if(!hrTmp.isDirectory() && !hrTmp.mkdirs()) {
                    CmdUtil.error("tmp/hr 目录创建失败");
                }

                List<ConstantData> modified = Helpers.cast(args.get("_HOT_RELOAD_ENABLE_"));
                BoxFile aoc = new BoxFile(new File(hrTmp, "modified.bin"));
                aoc.clear();

                for (int i = 0; i < modified.size(); i++) {
                    ConstantData data = modified.get(i);
                    aoc.append(data.name, Parser.toByteArrayShared(data));
                }

                File lck = new File(hrTmp, "mod.lck");
                try(FileOutputStream fos = new FileOutputStream(lck)) {
                    fos.write(0x23);
                }
                aoc.close();

                if(DEBUG)
                    CmdUtil.success("发送重载请求");
            }

            return 0;
        }
        return -1;
    }

    /**
     * @param flag Bit 1 : run (NoVersion) , Bit 2 : dependency mode
     */
    public static boolean compile(Map<String, ?> args, Project project, File jarDest, int flag) throws IOException, InterruptedException {
        // 前置
        if((flag & 2) == 0) {
            if(!args.containsKey("zl"))
                watcher.reset();

            //if(args.containsKey("zl") && !isCLI)
            //    CmdUtil.info("增量编译! ", false);
            for (Project proj : project.getAllDependencies()) {
                if(!compile(args, proj, jarDest, flag | 2)) {
                    CmdUtil.info("前置编译失败");
                    return false;
                }
                proj.registerWatcher();
            }
        }

        // 输出目录存在性
        File binary = project.binary;
        if(!binary.isDirectory()) {
            if(!binary.mkdirs() && !binary.delete() && !binary.mkdirs()) {
                CmdUtil.error("无法创建编译目录");
                return false;
            }
            if(!binary.setLastModified(0)) {
                CmdUtil.warning("时间戳设置失败");
            }
        }

        boolean increment = args.containsKey("zl");

        // region 获取所有源文件并(可选的)检测AT

        File source = project.source;
        if(!source.isDirectory()) {
            CmdUtil.warning("源码目录 " + source.getAbsolutePath() + " 不存在");
            return false;
        }

        List<File> files = null;
        if(increment) {
            MyHashSet<String> set = watcher.getModified(project, ProjectWatcher.ID_SRC);
            if(!set.contains(null)) {
                files = new ArrayList<>(set.size());
                for (String s : set) {
                    files.add(new File(s));
                }
            }
            FileFilter.state = 0;
        }
        if(files == null) {
            files = FileUtil.findAllFiles(source, FileFilter.INST.reset(0, increment ? FileFilter.F_SRC : FileFilter.F_JAVA_ANNO));
        }

        // endregion

        long stamp = binary.lastModified();
        incr:
        if(increment) {
            List<File> tmp = new ArrayList<>();

            for (int i = 0; i < files.size(); i++) {
                File file = files.get(i);
                if (file.lastModified() > stamp) {
                    if (FileFilter.checkATComments(file)) {
                        CmdUtil.warning("找到AT注解, 使用全量编译");

                        files = FileUtil.findAllFiles(source, FileFilter.INST.reset(0, FileFilter.F_JAVA_ANNO));
                        increment = false;

                        break incr;
                    }

                    tmp.add(file);
                }
            }

            files = tmp;
        }

        if(files.isEmpty()) {
            inc:
            if(increment) {
                AbstractExecutionTask task = project.getResourceTask();
                Project.resourceFilter.reset(stamp, FileFilter.F_TIME);
                task.calculate(null);

                String jarName = project.name + ((flag & 1) == 0 ? '-' + project.version : "") + ".jar";
                File jarFile = new File(jarDest, jarName);

                if(!jarFile.isFile()) {
                    CmdUtil.error("31: Only resource changed, with no destination jar found");
                    break inc;
                }

                if (!FileUtil.checkTotalWritePermission(jarFile)) {
                    if (!isCLI || !UIUtil.readBoolean("输出jar已被锁定, 是否能解决? ")) {
                        CmdUtil.error("30: 输出jar已被锁定");
                        break inc;
                    }
                }

                Map<String, ByteList> entries = Helpers.cast(project.resourceCache);
                for (Map.Entry<String, ?> entry : entries.entrySet()) {
                    if (entry.getValue() instanceof byte[])
                        entry.setValue(Helpers.cast(new ByteList((byte[]) entry.getValue())));
                }

                MutableZipFile mz = project.mz;
                try {
                    if (mz == null) {
                        mz = project.mz = new MutableZipFile(jarFile);
                    } else {
                        mz.open();
                    }
                    mz.setFileDataMore(entries);
                    mz.store();
                    mz.tClose();
                } catch (Throwable e) {
                    if (e.getCause() instanceof EOFException)
                        CmdUtil.warning("似乎jar文件不完整 请尝试全量编译", e);
                    else CmdUtil.warning("MutableZipFile 遇到了一些问题", e);
                    return false;
                }
            }
            if((flag & 3) != 0)
                return true;
            else
                CmdUtil.info("无源文件");
            return false;
        }

        // OpenAny
        ATHelper h = null;
        if(!FileFilter.cmtEntries.isEmpty()) {
            if(project.atName.isEmpty()) {
                CmdUtil.error(project.name + ": 使用了AT注解系统,却没有设置atConfig");
                return false;
            }

            Map<String, Collection<String>> map = AccessTransformer.getTransforms();

            map.clear();
            loadPreAT(project);

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

            ByteList val = ByteWriter.encodeUTF(atData);

            try(FileOutputStream fos = new FileOutputStream(project.atConfigPathStr)) {
                val.writeToStream(fos);
            }

            h = new ATHelper();
            h.init(project.name, val, map);
        }

        Set<String> ignores = new MyHashSet<>();
        readTextList(ignores::add, "忽略的编译错误码");

        long time = System.currentTimeMillis();

        // 放到这里, 因为会删除文件...
        String jarName = project.name + ((flag & 1) == 0 ? '-' + project.version : "") + ".jar";
        File jarFile = new File(jarDest, jarName);

        while (jarFile.isFile() && !FileUtil.checkTotalWritePermission(jarFile)) {
            if(!isCLI || !UIUtil.readBoolean("输出jar已被锁定, 是否能解决? ")) {
                jarFile = new File(jarDest, System.currentTimeMillis() + jarName);
                CmdUtil.warning("输出jar已被锁定... 改为 " + jarFile.getName());
            }
        }

        // 反正compile时间绝对够

        boolean canIncrementWrite = !args.containsKey("dbg-nomz") && MAIN_CONFIG.getBool("FMD配置.启用MutableZipFile") && increment & project.state != null & jarFile.isFile();
        if(!canIncrementWrite) {
            if(project.mz != null) {
                project.mz.close();
                project.mz = null;
            }
        }

        // region 更新资源文件

        AbstractExecutionTask task = project.getResourceTask();
        Project.resourceFilter.reset(stamp, canIncrementWrite ? FileFilter.F_TIME : FileFilter.F_ALL);
        parallel.pushTask(task);

        // endregion

        String binaryStr = project.binaryPathStr;

        // 库文件
        CharList libBuf = new CharList(200).append(binaryStr).append(File.pathSeparatorChar);
        if(h != null) {
            libBuf.append(h.getFakeJarPath()).append(File.pathSeparatorChar);
        }
        if(args.containsKey("_COMPILE_OUTPUT_SINCE_"))
            libBuf.append((CharSequence) args.get("_COMPILE_OUTPUT_SINCE_")).append(getLibClasses());
        else
            libBuf.append(getLibClasses());

        SimpleList<String> options = MAIN_CONFIG.get("编译参数").asList().asStringList();
        options.addAll("-d", binaryStr, "-cp", libBuf.toString(), "-encoding", project.charset.name());

        if(Compiler.compile(options, files, System.out, ignores, args.containsKey("showErrorCode"), null)) {
            if(DEBUG)
                System.out.println("编译完成 " + (System.currentTimeMillis() - time));

            try {
                task.get();
            } catch (ExecutionException e) {
                e.printStackTrace();
            }

            MyHashMap<String, InputStream> classes = new MyHashMap<>(100);

            if(canIncrementWrite) {
                MyHashSet<String> set = watcher.getModified(project, ProjectWatcher.ID_BIN);
                if(!set.contains(null)) {
                    for (String s : set) {
                        classes.put(s.substring(project.binaryPathStr.length()).replace('\\', '/'), new FileInputStream(s));
                    }
                } else {
                    FileUtil.findAndOpenStream(project.binary, classes, FileFilter.INST.reset(stamp, FileFilter.F_CLASS_TIME));
                }
            } else {
                // 自动删除被删除的文件
                // build之后，修改时间没有更新，就是被删除的文件了, 所以增量不支持
                FileUtil.findAndOpenStream(project.binary, classes, FileFilter.INST.reset(stamp, increment ? FileFilter.F_CLASS : FileFilter.F_CLASS_TIME_REMOVE));

                if(FileFilter.state > 0) {
                    int i = FileUtil.removeEmptyPaths(FileFilter.modified);
                    if(DEBUG)
                        CmdUtil.info("删除了" + FileFilter.state + "个已删除的文件, folder: " + i);
                }
            }

            // 想办法减少ZIP写入的I/O
            MyHashMap<String, byte[]> resources = project.resourceCache;

            if (h != null) {
                resources.put("META-INF/" + project.atName + ".cfg", h.getAtCfgBytes());
            }

            List<Context> list = Util.ctxFromStream(classes);

            if(DEBUG)
                System.out.println("资源处理完成 " + (System.currentTimeMillis() - time));

            List<Project> ad = project.getAllDependencies();
            List<State> extendedSuperList = mapperFwd.getExtendedSuperList();
            extendedSuperList.clear();
            for (int i = 0; i < ad.size(); i++) {
                extendedSuperList.add(ad.get(i).state);
            }

            if(canIncrementWrite) {
                mapperFwd.state(project.state);
                mapperFwd.remapIncrement(list);
                project.state = mapperFwd.snapshot(project.state);

                if(!isForgeMap) {
                    new CodeMapper(mapperFwd).remap(DEBUG, list);
                }

                if(DEBUG)
                    System.out.println("映射完成 " + (System.currentTimeMillis() - time));

                Map<String, ByteList> entries = Helpers.cast(resources);

                for (Map.Entry<String, ?> entry : entries.entrySet()) {
                    if(entry.getValue() instanceof byte[])
                        entry.setValue(Helpers.cast(new ByteList((byte[]) entry.getValue())));
                }

                for (int i = 0; i < list.size(); i++) {
                    Context ctx = list.get(i);
                    if(entries.put(ctx.getName(), ctx.get()) != null) {
                        CmdUtil.warning("发现重复的文件 " + ctx.getName());
                    }
                }

                MutableZipFile mz = project.mz;
                try {
                    if(mz == null) {
                        mz = project.mz = new MutableZipFile(jarFile);
                    } else {
                        mz.open();
                    }
                } catch (Throwable e) {
                    if(e.getCause() instanceof EOFException)
                        CmdUtil.warning("似乎jar文件不完整 请尝试全量编译", e);
                    else
                        CmdUtil.warning("MutableZipFile 遇到了一些问题", e);
                    return false;
                }

                try {
                    mz.setFileDataMore(entries);
                    mz.store();
                    mz.tClose();
                } catch (Throwable e) {
                    CmdUtil.warning("MutableZipFile 遇到了一些问题", e);
                }

                for (int i = 0; i < list.size(); i++) {
                    entries.remove(list.get(i).getName());
                }

                if(args.containsKey("_HOT_RELOAD_ENABLE_")) {
                    List<ConstantData> dst = Helpers.cast(args.get("_HOT_RELOAD_ENABLE_"));
                    for (Context data : list) {
                        if (FileFilter.modified.contains(data.getData().name)) {
                            dst.add(data.getData());
                        }
                    }
                }
            } else {
                for (int i = 0; i < list.size(); i++) {
                    if (resources.remove(list.get(i).getName()) != null) {
                        CmdUtil.warning("发现重复的文件 " + list.get(i).getName());
                    }
                }

                ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(jarFile));
                CalculateTask<Void> rw = new CalculateTask<>(new ResWriter(zos, resources));
                parallel.pushTask(rw);

                mapperFwd.remap(DEBUG, list);
                project.state = mapperFwd.snapshot(project.state);

                if(!isForgeMap) {
                    new CodeMapper(mapperFwd).remap(DEBUG, list);
                }

                if(DEBUG)
                    System.out.println("映射完成 " + (System.currentTimeMillis() - time));

                try {
                    rw.get();
                } catch (ExecutionException e) {
                    CmdUtil.warning("资源写入失败", e);
                }

                Util.write(list, zos, true);

                args.remove("_HOT_RELOAD_ENABLE_");
            }

            if((flag & 2) != 0) {
                CharList cl = (CharList) args.get("_COMPILE_OUTPUT_SINCE_");
                if(cl == null)
                    args.put("_COMPILE_OUTPUT_SINCE_", Helpers.cast(cl = new CharList()));
                cl.append(project.binaryPathStr).append(File.pathSeparatorChar);
            }

            CmdUtil.success("编译成功! 耗时: " + ((double) (System.currentTimeMillis() - time) / 1000d));

            if(!args.containsKey("dbg-nots")) {
                if (!binary.setLastModified(System.currentTimeMillis())) {
                    throw new IOException("设置时间戳失败!");
                }
            }

            project.registerWatcher();
            return true;
        }

        return false;
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
        sb.setIndex(sb.length() - 1);
        return sb;
    }

    // endregion

    private static int changeVersion() throws IOException {
        CMapping cfgGen = MAIN_CONFIG.get("通用").asMap();
        CMapping cfgFMD = MAIN_CONFIG.get("FMD配置").asMap();

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
            String versionJson = cfgFMD.getString("MC版本JSON");
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

        CMapping cfgGen = MAIN_CONFIG.get("通用").asMap();
        CMapping cfgFMD = MAIN_CONFIG.get("FMD配置").asMap();

        MyHashSet<String> skipped = new MyHashSet<>();
        readTextList(skipped::add, "FMD配置.忽略的libraries");
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
        Shared.saveForgeMapping();

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
                MCLauncher.downloadAndVerifyMD5(mcpConfUrl, mcpConfigFile, true);
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
                    MCLauncher.downloadAndVerifyMD5(mcpPackUrl, mcpPackFile, true);
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
                CmdUtil.warning("若raw.githubusercontent.com无法访问 请自己加hosts avatar.githubusercontent.com -> raw.githubusercontent.com");
                // todo Yarn
                File intermediary = new File(TMP_DIR, "/yarn_" + mcVersion + "_intermediary.txt");
                MCLauncher.downloadAndVerifyMD5("https://github.com/FabricMC/intermediary/raw/master/mappings/" + mcVersion + ".tiny", null, intermediary, true);

                File map = new File(TMP_DIR, "/yarn_" + mcVersion + "_map.zip");
                MCLauncher.downloadAndVerifyMD5("https://github.com/FabricMC/yarn/archive/" + mcVersion + ".zip", null, map, true);

                CmdUtil.info("生成MCP-SRG映射表...");

                MappingHelper mh = new MappingHelper(false);
                MappingHelper.Yarn yarn = mh.new Yarn();
                yarn.parse(intermediary, map, mcVersion);
                yarn.extract(mcpSrgPath);

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

        File merged = new File(BASE, "class/" + MERGED_FILE_NAME + ".jar");
        File backupFile = new File(BASE, "class/" + MERGED_FILE_NAME + ".jar.bak");
        try {
            FileUtil.copyFile(merged, backupFile);
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
        CmdUtil.info("        文件大小: " + TextUtil.getScaledNumber(tmp.getInteger("size")).toUpperCase() + 'B');
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

        ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(dest));
        ByteList list = new ByteList(1024 * 128);

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

                ZipFile zf = new ZipFile(file);

                Enumeration<? extends ZipEntry> itr = zf.entries();
                while (itr.hasMoreElements()) {
                    ZipEntry entry = itr.nextElement();
                    if (!entry.isDirectory() && entry.getName().endsWith(".class")) {
                        String prevPkg = names.get(entry.getName());
                        if(prevPkg != null) {
                            if (!pkg.equals(prevPkg)) {
                                prevPkg = prevPkg.substring(prevPkg.lastIndexOf('/') + 1);
                                pkg = pkg.substring(pkg.lastIndexOf('/') + 1);
                                CmdUtil.warning("重复的 " + entry.getName() + " 在 " + prevPkg + " 和 " + pkg, true);
                            }
                        } else {
                            ZipEntry ze1 = new ZipEntry(entry);
                            ze1.setCompressedSize(-1);
                            ze1.setMethod(ZipEntry.DEFLATED);
                            zos.putNextEntry(ze1);
                            list.clear();
                            InputStream in = zf.getInputStream(entry);
                            list.readStreamArrayFully(in).writeToStream(zos);
                            in.close();
                            zos.closeEntry();

                            names.put(entry.getName(), pkg);
                        }
                    }
                }

                zf.close();
            }
        }

        ZipUtil.close(zos);
    }

    private static final boolean _USE_PATCHY = System.getProperty("fmd.usePatchy", "false").equalsIgnoreCase("true");

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
        CEntry m = MAIN_CONFIG.getDot(key);
        if (m.getType() == Type.LIST) {
            CList list = m.asList();
            List<CEntry> rl = list.raw();
            for (int i = 0; i < rl.size(); i++) {
                CEntry entry = rl.get(i);
                if (entry.getType().fits(Type.STRING)) {
                    String s = entry.asString().trim();
                    if (s.length() > 0)
                        set.accept(s);
                }
            }
        }
    }

    public static void redirectOutput(File file, Executable action) throws FileNotFoundException {
        PrintStream out = System.out;

        PrintStream ps;
        System.setOut(ps = new PrintStream(new FileOutputStream(file)));

        action.execute();

        ps.flush();
        ps.close();
        if(ps.checkError()) {
            System.err.println("Failed to close temporary output stream.");
        }

        System.setOut(out);
    }

    // endregion
}
