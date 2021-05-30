package roj.mod;

import roj.asm.annotation.AnnotationProcessor;
import roj.asm.annotation.OpenAny;
import roj.asm.remapper.IRemapper;
import roj.asm.remapper.Renamer;
import roj.asm.remapper.Util;
import roj.asm.remapper.util.Context;
import roj.asm.remapper.util.ResWriter;
import roj.asm.struct.ConstantData;
import roj.asm.transform.AccessTransformer;
import roj.collect.MyHashMap;
import roj.collect.MyHashSet;
import roj.collect.SimpleList;
import roj.concurrent.SharedThreads;
import roj.concurrent.task.CalculateTask;
import roj.config.JSONParser;
import roj.config.ParseException;
import roj.config.data.CList;
import roj.config.data.CMapping;
import roj.config.data.ConfEntry;
import roj.config.data.Type;
import roj.config.word.Lexer;
import roj.config.word.Word;
import roj.config.word.WordPresets;
import roj.io.AppendOnlyCache;
import roj.io.FileUtil;
import roj.io.IOUtil;
import roj.io.ZipUtil;
import roj.math.MutableInt;
import roj.math.Version;
import roj.mod.util.MappingHelper;
import roj.text.CharList;
import roj.text.SimpleLineReader;
import roj.text.TextUtil;
import roj.ui.CmdUtil;
import roj.ui.UIUtil;
import roj.util.Base64;
import roj.util.*;

import java.io.*;
import java.net.SocketException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static roj.mod.Shared.*;

/**
 * This file is a part of more items mod (MI) <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 * <p>
 * @author Asyncorized_MC
 * @since 2020/8/5
 */
public final class ModDevelopment {

    public static void main(String[] args) throws IOException, InterruptedException, ParseException {
        long startTime = System.currentTimeMillis();

        /*if(Runtime.getRuntime().maxMemory() < 1024L * 1024L * 600L) {
            CmdUtil.warning("注意：最大内存不足600MB(" + (Runtime.getRuntime().maxMemory() >>> 20) + "MB)");
        }*/

        if(args.length == 0) {
            CmdUtil.error("F", false);
            CmdUtil.success("M", false);
            CmdUtil.fg(CmdUtil.Color.BLUE, true);
            System.out.print("D");
            CmdUtil.fg(CmdUtil.Color.RED, true);
            System.out.print(" FastModDev");
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
            CmdUtil.reset();
            CmdUtil.info("可用指令: build, runClient, hotreload, changeVersion, f2m, config, reflectHelp, srg2mcp, preAT, down");
            CmdUtil.info("请查看使用说明(太太太太老了...)获取详细信息");
            System.out.println();
            System.exit(0);
        }

        if(!CONF_INDEX.exists()) {
            config(new String[] {"config", "select"}, null);
        }

        Shared.loadConfig(true);

        int exitCode = 0;

        switch (args[0]) {
            case "build":
                exitCode = build(buildArgs(args));
                break;
            case "runClient":
                exitCode = runClient(buildArgs(args));
                break;
            case "changeVersion":
                exitCode = changeVersion();
                break;
            case "f2m":
                exitCode = forgeToMcp(args);
                break;
            case "config":
                exitCode = config(args, config);
                break;
            case "srg2mcp":
                exitCode = srg2mcp(args);
                break;
            case "reflectHelp":
                ReflectToolWindow.start(true);
                break;
            case "preAT":
                exitCode = preAT(new IGuiHelper());
                break;
            case "down": {
                File downloadPath;
                String url;
                if(args.length != 3) {
                    downloadPath = new File(UIUtil.userInput("下载保存位置: "));
                    FileUtil.USER_AGENT = "";
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
            default:
                CmdUtil.warning("参数错误");
        }

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
            String maybeVersion = null;

            if (args.length > 1) {
                mcpConfig = new File(args[1]);
            }
            if (mcpConfig == null || !mcpConfig.isFile()) {
                mcpConfig = UIUtil.readFile("下载的mcp_config_xxx.zip (不要解压!!!!!)");
            }
            String path = mcpConfig.getAbsolutePath();
            int i = path.lastIndexOf('-');
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

    public static int srg2mcp(String[] args) throws IOException, InterruptedException {
        List<File> files = new ArrayList<>();

        if(args.length > 1) {
            for (int i = 1; i < args.length; i++)
                files.add(new File(args[i]));
        }
        if(files.isEmpty()) {
            files = Collections.singletonList(UIUtil.readFile("文件"));
        }

        IRemapper remapper = getReverseRemapper();

        Map<String, InputStream> streams = new HashMap<>(400);
        Map<String, InputStream> classes = new HashMap<>(200);

        for (int i = 0; i < files.size(); i++) {
            File file = files.get(i);
            ZipFile zf = Util.prepareInputFromZip(file, StandardCharsets.UTF_8, streams);
            Helpers.filter(streams, classes, (s) -> s.endsWith(".class"));

            String path = file.getAbsolutePath();
            int index = path.lastIndexOf('.');
            File out = index == -1 ? new File(path + "-结果") : new File(path.substring(0, index) + "-结果.jar");
            ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(out));
            Thread thread = Util.createResourceWriter(zos, streams);

            List<Context> list = remapper.remap(classes, DEBUG);

            if (!isForgeMap) {
                new Renamer(remapper).remap(DEBUG, list);
            }

            thread.join();

            Util.write(list, zos, true);

            zf.close();

            streams.clear();
            classes.clear();
        }

        CmdUtil.success("操作成功完成!");

        return 0;
    }

    public static int preAT(IGuiHelper helper) throws IOException {
        File cache0 = new File(BASE, "/util/remapCache.bin");

        if (cache0.isFile() && !cache0.delete())
            CmdUtil.warning("无法删除映射缓存 util/remapCache.bin!", true);
    
        Map<String, Collection<String>> map = AccessTransformer.getTransforms();
        map.clear();

        readTextList((s) -> {
                String[] tmp = s.split(" ", 2);
                if (tmp.length < 2) {
                    CmdUtil.warning("Unknown entry " + s);
                }
                AccessTransformer.add(tmp[0], tmp[1]);
        }, "预AT.编译期");
        loadPreAT(null, true);

        if(map.isEmpty()) {
            CmdUtil.warning("没有找到AT");
            return 0;
        }

        boolean clearPkgInfo = helper.getBoolean("清除默认的'所有 参数/返回值 非空'注解: (T/F)");

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

        CmdUtil.info("操作开始!");

        File backupFile = new File(BASE, "class/" + MERGED_FILE_NAME + ".jar.bak");
        File jarFile = new File(BASE, "class/" + MERGED_FILE_NAME + ".jar");
        File tmpFile = new File(BASE, "class/" + MERGED_FILE_NAME + ".jar.tmp");

        ByteList bl = new ByteList(10000);

        if(!backupFile.isFile()) {
            FileUtil.copyFile(jarFile, backupFile);
        }

        ZipFile origZip = new ZipFile(backupFile);

        ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(tmpFile));
        Enumeration<? extends ZipEntry> en = origZip.entries();
        while (en.hasMoreElements()) {
            ZipEntry ze = en.nextElement();
            if(ze.isDirectory() || (ze.getName().endsWith("package-info.class") && clearPkgInfo))
                continue;
            ZipEntry cpEntry = new ZipEntry(ze.getName());
            zos.putNextEntry(cpEntry);
            
            String name = className.remove(ze.getName());
            if(name != null) {
                byte[] code = AccessTransformer.transform(name, IOUtil.readFully(origZip.getInputStream(ze)));
                Collection<String> subs = openSubClasses.remove(ze.getName());
                if(subs != null)
                    code = AccessTransformer.openSubClass(code, subs);

                zos.write(code);
                CmdUtil.success("已转换 " + ze.getName());
            } else {
                bl.clear();
                bl.readStreamArrayFully(origZip.getInputStream(ze));
                zos.write(bl.list, 0, bl.pos());
            }
            zos.closeEntry();
        }
        ZipUtil.close(zos);
        origZip.close();

        if(!className.isEmpty()) {
            CmdUtil.warning("有类没有找到: " + className.keySet());
        }

        if((jarFile.isFile() && !jarFile.delete()) || !tmpFile.renameTo(jarFile)) {
            CmdUtil.warning("请手动重命名 " + MERGED_FILE_NAME + ".jar.tmp为.jar");
        }

        CmdUtil.success("完毕");

        return 0;
    }

    // region PreAT.Util

    private static void loadPreAT(Config config, boolean any) {
        if(!any) {
            if (!config.at)
                return;
            CMapping mapping = MAIN_CONFIG.getDot("预AT.编译期+运行期").asMap().get(config.currentProject).asMap();

            loadATMap(mapping, false);
        } else {
            OpenAnyProcFMD.load_S2M_Map();
            for(ConfEntry ce : MAIN_CONFIG.getDot("预AT.编译期+运行期").asMap().values()) {
                if(ce.getType().canFit(Type.MAP))
                    loadATMap(ce.asMap(), true);
            }
        }
    }

    private static void loadATMap(CMapping mapping, boolean tr) {
        if(mapping.size() == 0)
            return;

        for(Map.Entry<String, ConfEntry> entry : mapping.entrySet()) {
            String k = entry.getKey();
            if(k.startsWith("#"))
                continue;

            ConfEntry ce = entry.getValue();
            switch (ce.getType()) {
                case LIST:
                    List<ConfEntry> rl = ce.asList().raw();
                    for (int i = 0; i < rl.size(); i++) {
                        ConfEntry ce1 = rl.get(i);
                        AccessTransformer.add(k, tr ? translateSeg(ce1) : ce1.asString());
                    }
                    break;
                case STRING:
                    AccessTransformer.add(k, tr ? translateSeg(ce) : ce.asString());
                    break;
            }
        }
    }

    private static String translateSeg(ConfEntry ce1) {
        String s = TextUtil.splitStringF(new ArrayList<>(2), new CharList(), ce1.asString(), ' ').get(0);
        if(DEBUG)
            System.out.println("AT Name " + s);

        int i = s.lastIndexOf('|');
        if(i != -1) {
            String s1 = s.substring(0, i);
            s = OpenAnyProcFMD.srg2mcp.getOrDefault(s1, s1) + s.substring(i);
        } else {
            s = OpenAnyProcFMD.srg2mcp.getOrDefault(s, s);
        }
        return s;
    }

    // endregion

    public static int config(String[] args, Config config) throws IOException {

        List<File> files = FileUtil.findAllFiles(PROJ_CONF_DIR, (f) -> !f.getName().equalsIgnoreCase("index.json") && !f.getName().equalsIgnoreCase("mc.json"));
        if(args.length > 2) {
            File path = new File(args[2]);
            if(!path.isFile()) {
                path = new File(BASE, "config/" + args[2]);
                if(!path.isFile()) {
                    CmdUtil.error("参数错误: 文件不存在");
                    return -1;
                }
            }
            files.clear();
            files.add(path);
        }

        switch (args[1]) {
            case "edit":
                editConfig(files);
                break;
            case "select":
                CmdUtil.info("当前选择的配置文件: " + (config == null ? "无" : config.getFile()));
                System.out.println();

                if(files.isEmpty()) {
                    CmdUtil.info("没有配置文件可用! 正在创建默认配置...");
                    File file;
                    editConfig0(file = new File(PROJ_CONF_DIR, "default.json"));
                    files = Collections.singletonList(file);
                }

                File selectConfig = files.get(UIUtil.selectOneFile(files, "配置文件"));

                roj.mod.Shared.setCurrentConfig(selectConfig.getAbsolutePath());
                CmdUtil.success("选择的配置文件已更新为: " + selectConfig);
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

        File selectConfig = files.get(UIUtil.selectOneFile(files, "配置文件"));
        if(selectConfig == newCreate) {
            do {
                String fileName = UIUtil.userInput("请输入新文件名然后按下回车: ");
                selectConfig = new File(BASE, "/config/" + fileName.replace(' ', '_').replace(' ', '_') + ".json");

                if(selectConfig.getName().equalsIgnoreCase("config.json") || selectConfig.getName().equalsIgnoreCase("mc.json")) {
                    CmdUtil.error("不能创建此文件名!");
                    continue;
                }

                if (selectConfig.exists()) {
                    CmdUtil.error("文件" + selectConfig + "已存在!");
                } else {
                    break;
                }
            } while(true);
        }

        editConfig0(selectConfig);
    }

    private static void editConfig0(File selectConfig) throws IOException {
        Config conf = new Config(selectConfig);
        conf.currentProject = UIUtil.userInput("请输入项目(mod)名 并回车: ");
        conf.currentVersion = UIUtil.userInput("请输入项目版本 并回车: ");
        conf.atName = UIUtil.userInput("请输入AT文件名 并回车(不懂留空): ");
        if(!conf.atName.equals(""))
            conf.at = UIUtil.readBoolean("是否启用AT(true或false) 并回车: ");
        do {
            conf.charset = UIUtil.userInput("请输入字符集(默认UTF-8) 并回车: ");
            if(conf.charset.equals(""))
                conf.charset = "UTF-8";
        } while (!hasCharset(conf.charset));

        File projPath = new File(BASE.getAbsolutePath() + File.separatorChar + "projects" + File.separatorChar + conf.currentProject);

        if(!projPath.isDirectory() && projPath.mkdirs()) {
            roj.io.ZipUtil.unzip(BASE.getAbsolutePath() + "/util/defaultProject.zip", projPath.getAbsolutePath() + '/');
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

    public static int runClient(Map<String, String> args) throws IOException, ParseException, InterruptedException {
        CMapping mc_conf;
        try {
            mc_conf = JSONParser.parse(IOUtil.readAsUTF(new FileInputStream(MC_CONF))).asMap();
        } catch (FileNotFoundException e) {
            CmdUtil.error("runClient配置文件" + MC_CONF.getAbsolutePath() + "不存在!");
            return -1;
        }

        File jar = new File(mc_conf.getString("root") + File.separatorChar + "mods" + File.separatorChar);
        String path = config.currentProject + "-TEST.jar";

        if(build0(args, config, jar,  path, 1)) {
            jar = new File(jar, path);
            if(!jar.isFile()) {
                CmdUtil.warning("抱歉，此情况无法使用增量编译! (destination jar was skipped but removed)");
                return -1;
            }

            if(MAIN_CONFIG.getDot("FMD配置.启用热重载").asBoolean()) {
                File hrTmp = new File(TMP_DIR, "hr");
                if(!hrTmp.isDirectory() && !hrTmp.mkdirs()) {
                    CmdUtil.error("tmp/hr 目录创建失败");
                }

                String jvm = mc_conf.getString("jvmArg");
                CharList cl = Base64.encode(ByteWriter.encodeUTF(hrTmp.getAbsolutePath()), new CharList()).append(' ');
                mc_conf.put("jvmArg", jvm + " -javaagent:" + new File(BASE, "util/FMD-agent.jar").getAbsolutePath() + '=' + cl);

                CmdUtil.info("重载agent配置完毕 " + cl);
            }

            return MCLauncher.runClient(mc_conf, new File(mc_conf.getString("native_path")), true, null);
        } else {
            return -1;
        }
    }

    public static int build(Map<String, String> args) throws IOException, InterruptedException {
        if(MAIN_CONFIG.getDot("FMD配置.启用热重载").asBoolean())
            args.put("", "");

        int code = build0(args, config, BASE, config.currentProject + '-' + config.currentVersion + ".jar", 0) ? 0 : -1;
        if(code == 0) {
            if(args.containsKey("") && !args.containsKey("  ")) {
                File hrTmp = new File(TMP_DIR, "hr");
                if(!hrTmp.isDirectory() && !hrTmp.mkdirs()) {
                    CmdUtil.error("tmp/hr 目录创建失败");
                }

                List<ConstantData> modifiedHrs = Helpers.cast(args.get(" "));
                if(modifiedHrs != null) {
                    AppendOnlyCache aoc = new AppendOnlyCache(new File(hrTmp, "modified.bin"));
                    aoc.clear();

                    for (int i = 0; i < modifiedHrs.size(); i++) {
                        ConstantData data = modifiedHrs.get(i);
                        aoc.append(data.name, data.getBytes());
                    }

                    File lck = new File(hrTmp, "mod.lck");
                    try(FileOutputStream fos = new FileOutputStream(lck)) {
                        fos.write(0x23);
                    }
                    CmdUtil.success("重载请求已发送...");
                } else {
                    CmdUtil.warning("没有modifiedList " + args);
                }
            }
        }
        return code;
    }

    public static boolean build0(Map<String, ?> args, Config config, File jarDest, String jarName, int flag) throws IOException, InterruptedException {
        if(!config.required.equals("")) {
            File newConfig = new File(config.required + ".json");
            if(!newConfig.exists()) {
                newConfig = new File(BASE, "/config/" + config.required + ".json");
            }
            if(!newConfig.exists())
                CmdUtil.warning("前置未找到");
            else {
                Config conf = new Config(newConfig);

                String jarName1;
                if((flag & 1) == 0)
                    jarName1 = jarName.replace(config.currentProject + '-' + config.currentVersion, conf.currentProject + '-' + conf.currentVersion);
                else
                    jarName1 = jarName.replace(config.currentProject + "-TEST", conf.currentProject + "-TEST");

                if(!build0(args, conf, jarDest, jarName1, flag | 2)) {
                    CmdUtil.info("前置编译失败");
                    return false;
                }
            }
        }

        File jarFile = new File(jarDest, jarName);

        boolean incrCompile = args.containsKey("zl");

        String binaryPath = BASE.getAbsolutePath() + File.separatorChar + "bin" + File.separatorChar + config.currentProject + File.separatorChar;

        File binaryPathFile = new File(binaryPath);

        if(args.containsKey("clearBin")) {
            if(!FileUtil.deletePath(binaryPathFile))
                CmdUtil.warning("无法清除编译目录");
        }
        if(!binaryPathFile.isDirectory()) {
            if(!binaryPathFile.mkdirs() && !binaryPathFile.delete() && !binaryPathFile.mkdirs()) {
                CmdUtil.error("无法创建编译目录");
                return false;
            }
            if(!binaryPathFile.setLastModified(0)) {
                CmdUtil.warning("时间戳设置失败");
            }
        }

        String libClasses = getLibClasses();

        SimpleList<String> options = MAIN_CONFIG.get("编译参数").asList().getStringList();

        options.addAll("-d", binaryPath, "-cp", binaryPath + File.pathSeparatorChar + libClasses, "-encoding", config.charset);

        try {
            Charset charset = config.charset.equals("") ? StandardCharsets.UTF_8 : Charset.forName(config.charset);
        } catch (UnsupportedCharsetException e) {
            CmdUtil.error("字符集不存在, 请修改");
            return false;
        }

        File resourcePath = new File(BASE.getAbsolutePath() + File.separatorChar + "projects" + File.separatorChar + config.currentProject + File.separatorChar + "resources" + File.separatorChar);

        Map<String, byte[]> resourceStream = new HashMap<>(100);

        CalculateTask<Void> task;
        if(incrCompile && jarFile.isFile()) {
            task = null;
        } else {
            task = new CalculateTask<>(() -> {
                initRemapper();

                Map<String, InputStream> map1 = Helpers.cast(resourceStream);

                FileUtil.findAndOpenStream(resourcePath, map1, (f) -> true);

                Set<Map.Entry<String, Object>> entrySet = Helpers.cast(map1.entrySet());

                for (Map.Entry<String, Object> entry : entrySet) {
                    entry.setValue(IOUtil.readFully((InputStream) entry.getValue()));
                }

                return null;
            });
            SharedThreads.IO_POOL.pushTask(task);
        }

        MutableInt oaMark = new MutableInt(config.at ? 2 : 0);

        File sourcePath = new File(BASE.getAbsolutePath() + File.separatorChar + "projects" + File.separatorChar + config.currentProject + File.separatorChar + "java" + File.separatorChar);
        if(!sourcePath.isDirectory()) {
            CmdUtil.warning("源码目录 " + sourcePath.getAbsolutePath() + " 不存在");
            return false;
        }

        final ByteList tmp0 = new ByteList(MAIN_CONFIG.getNumber("AT查找缓冲区大小"));
        List<File> files = FileUtil.findAllFiles(sourcePath, file -> {
            boolean flag1 = file.getName().endsWith(".java");
            if(flag1) {
                if(oaMark.and(3) == 2)
                    if (isOAMarked(tmp0, file)) {
                        oaMark.orEq(1);
                    }
            }
            return flag1;
        });

        List<File> srcLst;

        File stampFl = new File(BASE, "/bin/" + config.currentProject);

        long stamp = 0;
        //outer:
        if(incrCompile) {
            if((flag & 2) == 0) {
                CmdUtil.info("增量编译! ", false);
                CmdUtil.warning("注: 若删除了方法或类,更改了方法签名等, 请使用全量编译!");
            }

            if(stampFl.isDirectory()) {
                stamp = stampFl.lastModified();
            }

            // disable AT
            oaMark.andEq(~1);

            srcLst = new ArrayList<>();

            for (int i = 0; i < files.size(); i++) {
                File file = files.get(i);
                if (file.lastModified() > stamp) {
                    if (isOAMarked(tmp0, file)) {
                        CmdUtil.error("OpenAny注解已修改, 请使用全量编译");
                        return false;
                    }

                    srcLst.add(file);
                }
            }

            if((flag & 1) == 1 && srcLst.isEmpty()) {
                CmdUtil.info("已跳过编译");
                return true;
            }
        } else {
            args.remove("");
            args.put("  ", null);
            srcLst = files;
        }

        OpenAnyProcFMD md = null;
        if(oaMark.and(1) != 0) {
            md = new OpenAnyProcFMD();

            String atPath = resourcePath.getPath() + File.separatorChar + "META-INF" + File.separatorChar + config.atName + ".cfg";

            Map<String, Collection<String>> map = AccessTransformer.getTransforms();
            map.clear();
            loadPreAT(config, false);

            if (!map.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (Map.Entry<String, Collection<String>> entry : map.entrySet()) {
                    for (String val : entry.getValue()) {
                        sb.append("public-f ").append(entry.getKey()).append(' ').append(val).append('\n');
                    }
                }
                try (FileOutputStream fos = new FileOutputStream(new File(atPath))) {
                    fos.write(sb.toString().getBytes(StandardCharsets.UTF_8));
                }
            }
            md.internalInit(atPath, libClasses, binaryPath, !map.isEmpty());
        }

        Set<String> ignores = new MyHashSet<>();
        readTextList(ignores::add, "忽略的编译错误码");

        long time = System.currentTimeMillis();
        if(!srcLst.isEmpty() && Compiler.compile(options, srcLst, System.out, binaryPath, ignores, args.containsKey("showErrorCode"), md)) {
            CmdUtil.success("编译成功! 耗时: " + ((double) (System.currentTimeMillis() - time) / 1000d));

            runHook();

            Map<String, InputStream> binaryStream = new HashMap<>(100);
            if(!incrCompile || !jarFile.isFile()) {
                if(jarFile.exists() && !jarFile.delete()) {
                    CmdUtil.error("无法删除输出jar");
                    return false;
                }

                try {
                    task.get();
                } catch (ExecutionException e) {
                    e.printStackTrace();
                }

                FileUtil.findAndOpenStream(new File(binaryPath), binaryStream, file -> file.getName().endsWith(".class"));

                for (String s : binaryStream.keySet()) {
                    if ((resourceStream.remove(s)) != null) {
                        CmdUtil.warning("发现重复的文件 " + s);
                    }
                }

                if (md != null) {
                    try {
                        resourceStream.put("META-INF" + File.separatorChar + config.atName + ".cfg", IOUtil.readFile(md.getAtPath()));
                    } catch (Throwable e) {
                        throw new RuntimeException("That shouldn't happen!", e);
                    }
                }

                ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(jarFile));
                CalculateTask<Void> task1 = CalculateTask.fromVoid(new ResWriter(zos, resourceStream));
                SharedThreads.IO_POOL.pushTask(task1);

                List<Context> list = Util.prepareContexts(binaryStream);

                // copy into class
                if((flag & 2) != 0) {
                    File devJar = new File(BASE, "/class/dep-" + config.currentProject + ".jar");
                    ZipOutputStream zos1 = new ZipOutputStream(new FileOutputStream(devJar));
                    Util.write(list, zos1, true);
                }

                System.out.println("文件已加载");

                remapper.remap(DEBUG, list);

                if(!isForgeMap) {
                    new Renamer(remapper).remap(DEBUG, list);
                }

                try {
                    task1.get();
                } catch (ExecutionException e) {
                    e.printStackTrace();
                }

                Util.write(list, zos, true);
            } else {
                long finalStamp = stamp;
                FileUtil.findAndOpenStream(new File(binaryPath), binaryStream, file -> {
                    if(file.getName().endsWith(".class")) {
                        return file.lastModified() > finalStamp;
                    }
                    return false;
                });

                AnnotationProcessor.closeStreams();

                List<Context> list = Util.prepareContexts(binaryStream);
                MyHashSet<String> set = new MyHashSet<>(binaryStream.size());
                for (String path : binaryStream.keySet()) {
                    set.add(path.replace('\\', '/'));
                }

                ByteList tmp1 = new ByteList(10240);
                if((flag & 2) != 0) {
                    File devJar = new File(BASE, "/class/dep-" + config.currentProject + ".jar");
                    File devJarTmp = new File(devJar.getPath() + ".tmp");

                    ZipOutputStream zout = new ZipOutputStream(new FileOutputStream(devJarTmp));
                    if(devJar.isFile()) {
                        try(ZipInputStream zin = new ZipInputStream(new FileInputStream(devJar))) {
                            ZipEntry ze;
                            while ((ze = zin.getNextEntry()) != null) {
                                if (set.contains(ze.getName()))
                                    continue;
                                zout.putNextEntry(new ZipEntry(ze));
                                tmp1.readStreamFully(zin).writeToStream(zout);
                                zout.closeEntry();
                                tmp1.clear();
                            }
                        } catch (IOException e) {
                            CmdUtil.error("文件读取失败", e);
                            zout.close();
                            devJar.delete();
                            devJarTmp.delete();
                        }
                    }

                    Util.write(list, zout, true);

                    if(devJar.isFile() && !devJar.delete()) {
                        CmdUtil.warning(devJar + " 被占用");
                    } else if(!devJarTmp.renameTo(devJar)) {
                        CmdUtil.warning(devJar + " 被占用");
                    }
                }

                System.out.println("文件已加载");

                remapper.remap(DEBUG, list);

                if(!isForgeMap) {
                    new Renamer(remapper).remap(DEBUG, list);
                }

                File jarTmp = new File(jarFile.getPath() + ".tmp");

                ZipOutputStream zout = new ZipOutputStream(new FileOutputStream(jarTmp));
                try(ZipInputStream zin = new ZipInputStream(new FileInputStream(jarFile))) {
                    ZipEntry ze;
                    while ((ze = zin.getNextEntry()) != null) {
                        if (set.contains(ze.getName()))
                            continue;
                        zout.putNextEntry(new ZipEntry(ze));
                        tmp1.readStreamFully(zin).writeToStream(zout);
                        zout.closeEntry();
                        tmp1.clear();
                    }
                } catch (IOException io) {
                    CmdUtil.error("文件读取失败", io);
                    zout.close();
                    jarTmp.delete();
                    jarFile.delete();
                    return false;
                }

                if(args.containsKey("")) {
                    Object o = args.get(" ");
                    if (o == null)
                        args.put(" ", Helpers.cast(o = new ArrayList<>()));
                    List<ConstantData> o1 = Helpers.cast(o);
                    for (int i = 0; i < list.size(); i++) {
                        o1.add(list.get(i).getData());
                    }
                }

                Util.write(list, zout, true);

                if(jarFile.isFile() && !jarFile.delete()) {
                    CmdUtil.warning(jarFile + " 被占用");
                    return false;
                } else if(!jarTmp.renameTo(jarFile)) {
                    CmdUtil.error(jarFile + " 被占用");
                    return false;
                }
            }

            if(!stampFl.setLastModified(System.currentTimeMillis())) {
                throw new IOException("设置时间戳失败!");
            }
            return true;
        }

        runHook();

        if(srcLst.isEmpty())
            if((flag & 2) != 0)
                return true;
            else
                CmdUtil.info("莫得源文件");

        return false;
    }

    // region Build.util

    static List<Runnable> hook = new ArrayList<>(2);

    public static void annotationHook(Runnable runnable) {
        hook.add(runnable);
    }

    public static void runHook() {
        for (int i = 0; i < hook.size(); i++) {
            hook.get(i).run();
        }

        hook.clear();
    }

    // endregion

    // region OpenAny Finder

    public static boolean isOAMarked(ByteList tmp0, File file) {
        try(FileInputStream fis = new FileInputStream(file)) {
            int len = fis.read(tmp0.list, 0, Math.min((int) file.length() >> 1, tmp0.capacity()));
            tmp0.pos(len);
            if(findOpenAnyMark(tmp0)) {
                return true;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    static String oac = OpenAny.class.getName().replace('/', '.');
    private static boolean findOpenAnyMark(ByteList s) {
        for (int i = 0; i < s.pos(); i++) {
            byte c = s.get(i);
            if(c == '@') {
                i++;
                if(regionMatches(s.list, i, oac) || regionMatches(s.list, i, "OpenAny")) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean regionMatches(byte[] list, int index, CharSequence seq) {
        if (index + seq.length() > list.length)
            return false;

        int j = 0;
        for (int i = index; j < seq.length(); i++, j++) {
            if (list[i] != seq.charAt(j))
                return false;
        }

        return true;
    }

    // endregion

    private static int changeVersion() throws IOException {
        CMapping cfgGen = MAIN_CONFIG.get("通用").asMap();
        CMapping cfgFMD = MAIN_CONFIG.get("FMD配置").asMap();

        File mcRoot;
        {
            File file = new File(cfgGen.getString("MC目录"));
            if (file.isDirectory())
                mcRoot = file;
            else
                mcRoot = UIUtil.readFile("MC目录(.minecraft)");
        }

        List<File> versions = MCLauncher.findVersions(new File(mcRoot, "/versions/"));

        File mcJson = null;
        if(versions.size() < 1) {
            CmdUtil.error("没有找到任何MC版本！！！", true);
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

        return changeVersion(mcRoot, mcJson, new IGuiHelper());
    }

    @SuppressWarnings("unchecked")
    static <T extends IntSupplier & BiConsumer<Integer, File>> int changeVersion(File mcRoot, File mcJson, IGuiHelper gui) throws IOException {
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

        File mcServer = null;
        File mcpPackFile = null;

        {
            File file = new File(cfgFMD.getString("MC未反混淆的服务器端"));
            if (file.isFile())
                mcServer = file;
        }


        File mcpSrgPath = new File(BASE, "/util/mcp-srg.srg");
        if(mcpSrgPath.isFile() && !mcpSrgPath.delete()) {
            CmdUtil.error("无法删除旧的映射数据: " + mcpSrgPath.getPath());
            return -1;
        }

        Map<String, Map<String, List<String>>> paramMap = null;
        {
            String version = "";
            if(is113andAbove) {
                version = jsonDesc.get("clientVersion").asString();
            }
            if(version.isEmpty()) {
                final String name = mcJar.getName();
                version = name.substring(0, name.lastIndexOf('.'));
                int i = name.lastIndexOf('-');
                if(i != -1)
                    version = version.substring(0, i);
                version = version.replace("-forge", "");
            }

            String mcpVersion = detectVersion(version);

            gui.stageInfo(0);
            String changeVersion = gui.userInput("自动检测的MCP版本号为: " + mcpVersion + " 若出错请修改, 否则直接按回车: ", true).trim();
            if(changeVersion.length() > 0)
                mcpVersion = changeVersion;

            Version comparableVersion = new Version(version);

            CMapping downloads = jsonDesc.get("downloads").asMap();

            boolean canDownloadOfficial = downloads.containsKey("client_mappings");
            boolean canDownloadYarn = comparableVersion.compareTo(new Version("1.12")) >= 0;

            if(gui.isConsole()) {
                System.out.println();
                CmdUtil.info("需要提供一个映射表! ");
                CmdUtil.info(" 0: 下载MCP映射表");
                if (canDownloadOfficial) {
                    CmdUtil.info(" 1: 下载MC官方的映射表");
                }
                if (canDownloadYarn) {
                    CmdUtil.info(" 2: 使用YARN映射表 (WIP)");
                }
                CmdUtil.info(" 3: 使用自己的");
                CmdUtil.info(" 4: 手动下载(比如, 使用镜像)");
                System.out.println();
            }

            String mcpConfUrl = cfgGen.getString("协议") + cfgGen.getString("forge地址") + "/de/oceanlabs/mcp/mcp_config/" + version + "/mcp_config-" + version + ".zip";
            String mirror = cfgGen.getBoolean("下载MC相关文件使用镜像") ? cfgGen.getString("镜像地址") : null;
            boolean mapUseMirror = cfgFMD.getBoolean("映射表也使用镜像");

            gui.stageInfo(1, canDownloadOfficial, canDownloadYarn);
            int select = gui.getNumberInRange(0, 5);
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
                mcpConfigFile = new File(TMP_DIR, "mcp_config-" + version + ".zip");

                try {
                    MCLauncher.downloadAndVerifyMD5(mcpConfUrl, mcpConfigFile, true);
                } catch (FileNotFoundException e) {
                    CmdUtil.warning("错误: 404  MCP-Config不存在! 请检查镜像地址, 或者手动下载");
                    return -1;
                } catch (IOException e) {
                    CmdUtil.warning("MCP-Config下载失败 " + mcpConfUrl);
                    return -1;
                }
                Process1_16.handleMCPConfig(mcpConfigFile);
            } else {
                mcpConfigFile = null;
            }

            isForgeMap = false;

            switch (select) {
                case 0: {
                    String mcpPackUrl = cfgFMD.getString("MCP下载根路径");

                    boolean autoDownloadStable = cfgFMD.getBoolean("自动下载稳定版的MCP");

                    String a = getStableMCPVersion(mcpVersion);
                    if(!autoDownloadStable || a == null) {
                        if(a == null) {
                            CmdUtil.warning("不幸的是目前你的版本没有稳定的MCP");
                        }

                        CmdUtil.info("方便你输入: stable-123 => s-123 (稳定版)  snapshot-20201221 => 20201221 (快照版) ");
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

                    isForgeMap = true;

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
                    // todo
                    File intermediary = new File(TMP_DIR, "/yarn_" + version + "_intermediary.txt");
                    MCLauncher.downloadAndVerifyMD5("https://github.com/FabricMC/intermediary/raw/master/mappings/" + version + ".tiny", null, intermediary, true);

                    File map = new File(TMP_DIR, "/yarn_" + version + "_map.zip");
                    MCLauncher.downloadAndVerifyMD5("https://github.com/FabricMC/yarn/archive/" + version + ".zip", null, map, true);

                    CmdUtil.info("生成MCP-SRG映射表...");

                    MappingHelper mh = new MappingHelper(false);
                    MappingHelper.Yarn yarn = mh.new Yarn();
                    yarn.parse(intermediary, map, version);
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

            if(mcServer == null)
                mcServer = MCLauncher.downloadMinecraftFile(downloads, "server", mirror);
        }

        CmdUtil.info("合并libraries中...请稍后");

        {
            File cache0 = new File(BASE, "/util/remapCache.bin");

            if (cache0.isFile() && !cache0.delete())
                CmdUtil.error("无法删除映射缓存 util/remapCache.bin!", true);
        }

        File librariesPath = new File(mcRoot, "/libraries/");

        IProcess remapThread;
        if(is113andAbove) {
            Object[] files = Process1_16.find116Files(librariesPath, libraries);

            mc_conf.put("jar", ((File) files[0]).getAbsolutePath());

            remapThread = new Process1_16(mcServer, paramMap, files);
        } else {
            remapThread = new Process1_12(mcServer, mcJar, mcpSrgPath.isFile() ? null : mcpPackFile);
        }

        mergeLibraries(librariesPath, libraries, remapThread, is113andAbove);

        try {
            updateConfig();

            try (FileOutputStream fos = new FileOutputStream(MC_CONF)) {
                ByteWriter.encodeUTF(mc_conf.toJSON()).writeToStream(fos);
            }
        } catch (IOException e) {
            CmdUtil.error("IO异常: 文件复制失败!", e);
        }

        CmdUtil.success("合并libraries完毕", true);

        int code = remapThread.getAsInt();
        if(code != 0)
            return code;
        
        File backupFile = new File(BASE, "class/" + MERGED_FILE_NAME + ".jar.bak");
        if(backupFile.isFile() && !backupFile.delete()) {
            CmdUtil.warning("备份删除失败");
        }

        File merged = new File(BASE, "class/" + MERGED_FILE_NAME + ".jar");

        // clear close() hook
        System.gc();
        System.runFinalization();
        System.gc();
        System.runFinalization();

        preAT(gui);
        return 0;
    }

    // region ChangeVersion.Util

    private static String getLibClasses() {
        File lib = new File(BASE, "/class/");

        StringBuilder sb = new StringBuilder();
        List<File> fs = FileUtil.findAllFiles(lib);
        for (int i = 0; i < fs.size(); i++) {
            File file = fs.get(i);
            if (!(file.getName().endsWith(".zip") || file.getName().endsWith(".jar")) || file.length() == 0) {
                //boolean result = file.delete();
                //CmdUtil.warning("文件 " + file.getAbsolutePath() + " 大小为0, 会影响程序运行, " + (result ? "已被自动删除" : "请手动删除"));
                //if(result)
                continue;
            }
            try (ZipFile zipFile = new ZipFile(file)) {
                if (zipFile.size() == 0)
                    CmdUtil.warning("文件 " + file.getAbsolutePath() + " 是空的, 可以删除");
            } catch (Throwable e) {
                CmdUtil.warning("文件 " + file.getAbsolutePath() + " 无法作为压缩文件读取!");
                if (!file.renameTo(new File(file.getAbsolutePath() + ".error"))) {
                    throw new RuntimeException("文件无法重命名");
                } else {
                    CmdUtil.info("文件已被自动重命名为<原文件名>.error");
                }
            }

            sb.append(file.getAbsolutePath()).append(File.pathSeparatorChar);
        }
        return sb.deleteCharAt(sb.length() - 1).toString();
    }

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
        CmdUtil.info("        文件大小: " + TextUtil.getScaledNumber(tmp.getNumber("size")).toUpperCase() + 'B');
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
                    CmdUtil.info("跳过Patchy");
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

    private static Map<String, String> buildArgs(String[] args) throws ParseException {
        Lexer r = new Lexer().init(TextUtil.concat(args, ' '));
        Map<String, String> map = new HashMap<>();
        while(r.hasNext()) {
            Word w = r.readWord();
            if(w.type() != WordPresets.STRING && w.type() != WordPresets.LITERAL) {
                throw r.err("Unexpected");
            }

            String text = w.val(), next = null;
            w = r.nextWord();
            if(w.type() == WordPresets.ERROR && w.val().equals("=")) {
                next = r.readWord().val();
            } else {
                r.retractWord();
            }

            map.put(text, next);
        }
        return map;
    }

    public static void readTextList(Consumer<String> set, String key) {
        ConfEntry m = MAIN_CONFIG.getDot(key);
        if (m.getType() == Type.LIST) {
            CList list = m.asList();
            List<ConfEntry> rl = list.raw();
            for (int i = 0; i < rl.size(); i++) {
                ConfEntry entry = rl.get(i);
                if (entry.getType().canFit(Type.STRING)) {
                    String s = entry.asString();
                    s = s.trim();
                    if (!s.startsWith("#") && s.length() > 0)
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
