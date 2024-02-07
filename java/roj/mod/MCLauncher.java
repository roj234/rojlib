package roj.mod;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import roj.collect.MyHashMap;
import roj.collect.SimpleList;
import roj.collect.TrieTreeSet;
import roj.concurrent.TaskHandler;
import roj.concurrent.Waitable;
import roj.concurrent.task.AsyncTask;
import roj.concurrent.task.ITask;
import roj.config.JSONParser;
import roj.config.ParseException;
import roj.config.data.CCommMap;
import roj.config.data.CEntry;
import roj.config.data.CList;
import roj.config.data.CMapping;
import roj.config.word.Tokenizer;
import roj.config.word.Word;
import roj.io.ChineseInputStream;
import roj.io.IOUtil;
import roj.io.down.DownloadTask;
import roj.io.down.ProgressGroupedMulti;
import roj.math.Version;
import roj.text.CharList;
import roj.text.Template;
import roj.text.TextUtil;
import roj.ui.CLIUtil;
import roj.util.ByteList;
import roj.util.OS;

import javax.swing.*;
import java.awt.event.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static roj.mod.Shared.*;

/**
 * Roj234's Minecraft Launcher
 *
 * @author Roj234
 * @since 2021/5/31 21:17
 */
public class MCLauncher {
	@Nullable
	static JFrame activeWindow;

	public static void main(String[] args) {
		System.out.println("FMD 2.3.0起移除了MCLauncher及其他功能，见更新日志");
		System.exit(-1);
	}

	public static void clearLogs(ActionEvent e) {
		if (checkMCRun()) return;

		if (!config.containsKey("mc_conf")) {
			error("没有选择版本!");
			return;
		}

		File basePath = new File(config.getString("mc_conf.root"));
		File debugLog = new File(basePath, "minecraft.log");
		debugLog.delete();
		File logs = new File(basePath, "logs");
		IOUtil.deletePath(logs);
		File crashes = new File(basePath, "crash-reports");
		IOUtil.deletePath(crashes);

		CLIUtil.warning("清除了没啥用的日志文件!");
	}

	// region Select version

	public static List<File> findVersions(File file) { return findVersions(file, new ArrayList<>()); }
	private static List<File> findVersions(File file, List<File> jsons) {
		File[] files = file.listFiles();
		if (files != null) {
			for (File file1 : files) {
				if (file1.isDirectory()) {
					File file2 = new File(file1, file1.getName()+".json");
					if (file2.exists()) jsons.add(file2);
				}
			}
		}
		return jsons;
	}

	// endregion
	// region waiter

	public static final int TIMEOUT_TIME = 60000;

	private static void waitFor(ITask task) {
		long str = System.currentTimeMillis();

		boolean skip = false;
		do {
			Task.awaitFinish(TIMEOUT_TIME - (System.currentTimeMillis() - str));
			if (Task.taskPending() <= 0) return;

			if (!skip) {
				int r = ifBreakWait();
				if (r == -1) {
					if (task != null) task.cancel(true);
					return;
				}
				if (r == 1) skip = true;
				str = System.currentTimeMillis();
			}

		} while (true);
	}

	private static int ifBreakWait() {
		if (activeWindow == null) return 1;

		Object[] options = {"继续等", "不等了", "不再提示"};
		int m = JOptionPane.showOptionDialog(activeWindow,
			"等了一分钟了，还没下完\n" +
			"如果你觉得下的太慢了，直接关闭窗口好了\n" +
			"我们会实时保存下载进度", "询问", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);

		switch (m) {
			case JOptionPane.YES_OPTION: return 0;
			default:
			case JOptionPane.NO_OPTION: Task.clearTasks(); return -1;
			case JOptionPane.CANCEL_OPTION: return 1;
		}
	}

	// endregion
	// region config

	public static CMapping config;

	public static void save() {
		try (FileOutputStream fos = new FileOutputStream(new File(BASE, "launcher.json"))) {
			fos.write(config.toJSON().getBytes(StandardCharsets.UTF_8));
		} catch (IOException e) {
			error("配置保存失败\n详情看控制台");
			e.printStackTrace();
		}
	}

	public static void load() {
		if (config != null) return;

		File conf = new File(BASE, "launcher.json");
		if (conf.isFile()) {
			try {
				ChineseInputStream bom = new ChineseInputStream(new FileInputStream(conf));
				if (!bom.getCharset().equals("UTF8")) { // 检测到了则是 UTF-8
					CLIUtil.warning("文件的编码中含有BOM(推荐使用UTF-8无BOM格式!), 识别的编码: " + bom.getCharset());
				}

				config = new JSONParser().charset(Charset.forName(bom.getCharset())).parseRaw(bom).asMap();
				config.dot(true);
				CEntry path = config.getOrNull("mc_conf.native_path");
				if (path != null) {
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

	public static int runClient(CMapping mc_conf, int processFlags, Consumer<Process> consumer) throws IOException {
		Map<String, String> env = new MyHashMap<>();
		File nativePath = new File(mc_conf.getString("native_path"));
		env.put("natives_directory", '"' + Tokenizer.addSlashes(nativePath.getAbsolutePath()) + '"');
		env.put("classpath", '"' + Tokenizer.addSlashes(new StringBuilder(mc_conf.getString("libraries")).append(mc_conf.getString("jar"))) + '"');
		env.put("launcher_name", "FMD");
		env.put("launcher_version", VERSION);
		env.put("classpath_separator", File.pathSeparator);
		x:
		try {
			do {
				nativePath = nativePath.getParentFile();
				if (nativePath == null) break x;
			} while (!nativePath.getName().equals(".minecraft"));
			env.put("library_directory", new File(nativePath, "libraries").getAbsolutePath());
		} catch (Throwable e) {
			e.printStackTrace();
		}

		String java = mc_conf.getString("java");
		if (java.isEmpty()) java = "java";
		SimpleList<String> args = new SimpleList<>();
		args.add(java);
		replace(mc_conf.getString("jvmArg"), env, args);
		args.add(mc_conf.getString("mainClass"));

		String playerName = mc_conf.getString("player_name");
		if (playerName.equals("")) playerName = CONFIG.getString("通用.玩家名字");

		String authName = null, authToken = null, authUUID = null;
		File def = new File(CONFIG.getString("通用.外部accessToken"));
		if (def.isFile()) {
			try {
				CMapping map = JSONParser.parses(roj.io.IOUtil.readUTF(def)).asMap();
				authName = map.getString("name");
				authToken = map.getString("token");
				authUUID = map.getString("uuid");
			} catch (ParseException e) {
				e.printStackTrace();
			}
		}
		if (authUUID == null) {
			authToken = authUUID = UUID.nameUUIDFromBytes(playerName.getBytes(StandardCharsets.UTF_8)).toString();
			authName = playerName;
		}

		Map<String, String> mcEnv = new MyHashMap<>();
		mcEnv.put("user_type", "Legacy");
		mcEnv.put("version_type", "FMD");
		mcEnv.put("launcher_name", "FMD");
		mcEnv.put("launcher_version", VERSION);
		mcEnv.put("player_name", playerName);
		mcEnv.put("auth_access_token", authToken);
		mcEnv.put("auth_uuid", authUUID);
		mcEnv.put("assets_index_name", mc_conf.getString("assets"));
		mcEnv.put("assets_root", '"' + Tokenizer.addSlashes(mc_conf.getString("assets_root") + File.separatorChar + "assets") + '"');
		mcEnv.put("game_directory", mc_conf.getString("root"));
		mcEnv.put("version_name", "FMDv" + VERSION);
		mcEnv.put("auth_player_name", authName);
		mcEnv.put("user_properties", "{}");
		replace(mc_conf.getString("mcArg"), mcEnv, args);

		CLIUtil.info("启动客户端...");

		return runProcess(args, new File(mc_conf.getString("root")), processFlags, consumer);
	}

	private static final Tokenizer l = Tokenizer.arguments();
	private static void replace(String arg, Map<String, String> env, SimpleList<String> args) {
		CharList sb = new CharList();
		Template.replaceOnce(env, arg, sb);
		try {
			l.init(sb);
			while (l.hasNext()) {
				Word w = l.next();
				if (w.type() == Word.EOF) break;
				args.add(w.val());
			}
		} catch (ParseException e) {
			e.printStackTrace();
		}
	}

	// region prepare MC

	public static Object[] getRunConf(File mcRoot, File mcJson, File mcNative, CMapping cfg) throws IOException {
		return getRunConf(mcRoot, mcJson, mcNative, Collections.emptyList(), true, cfg);
	}

	public static Object[] getRunConf(File mcRoot, File mcJson, File nativePath, Collection<String> skipped, boolean cleanNatives, CMapping cfg) throws IOException {
		return getRunConf(mcRoot, mcJson, nativePath, skipped, cleanNatives, cfg.getBool("版本隔离"), cfg.getString("libraries地址"), cfg.getString("附加JVM参数"), cfg.getString("附加MC参数"));
	}

	public static Object[] getRunConf(File mcRoot, File mcJson, File nativePath, Collection<String> skipped, boolean cleanNatives, boolean insulation, String mirror, String jvmArg, String mcArg) throws IOException {
		CMapping mc_conf = new CCommMap();
		mc_conf.putComment("java", "自定义java路径");
		mc_conf.put("java", "");

		mc_conf.put("assets_root", mcRoot.getAbsolutePath());
		mc_conf.put("root", insulation ? mcJson.getParentFile().getAbsolutePath() : mcRoot.getAbsolutePath());
		mc_conf.put("native_path", nativePath.getAbsolutePath());

		if (nativePath.isDirectory()) {
			if (cleanNatives && !IOUtil.deletePath(nativePath)) {
				CLIUtil.error("无法清空natives, 请手动删除");
				return null;
			}
		} else if (!nativePath.mkdirs()) {
			CLIUtil.error("无法创建natives");
			return null;
		}

		Map<String, String> libraries = new MyHashMap<>();
		boolean is113andAbove = false;
		File mcJar;
		CMapping jsonDesc;

		try {
			jsonDesc = JSONParser.parses(IOUtil.readUTF(mcJson)).asMap();

			File librariesPath = new File(mcRoot, "/libraries/");

			MyHashMap<String, Object> imArgs = new MyHashMap<>(8);
			imArgs.put("libraryPath", librariesPath);
			imArgs.put("nativePath", nativePath);
			imArgs.put("libraries", libraries);
			imArgs.put("mirror", mirror);
			imArgs.put("handler", Task);

			Object[] arr = parseJsonData(imArgs, new File(mcRoot, "/versions/"), skipped, jsonDesc);

			mcJar = (File) arr[0];

			mc_conf.put("jar", mcJar.getAbsolutePath());

			mc_conf.put("assets", (String) arr[1]);
			mc_conf.put("mainClass", (String) arr[2]);

			final int flag = (int) arr[5];
			if ((flag & 1) != 0) {
				is113andAbove = true;

				String[] arr1 = (String[]) arr[3];
				mc_conf.put("mcArg", arr1[0] + ' ' + mcArg);
				mc_conf.put("jvmArg", jvmArg + ' ' + arr1[1]);
			} else {
				mc_conf.put("mcArg", ((String) arr[3]) + ' ' + mcArg);
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

		return new Object[] {mc_conf, mcJar, libraries, jsonDesc, is113andAbove};
	}

	// region parse Manifest

	private static Object[] parseJsonData(MyHashMap<String, Object> imArgs, File versionsPath, Collection<String> skipped, CMapping desc) throws IOException, ParseException {
		Map<String, Version> versions = new MyHashMap<>();

		for (String s : skipped) {
			versions.put(s, Version.INFINITY);
		}

		if (DEBUG && !versions.isEmpty()) {
			CLIUtil.info("注: 跳过的依赖 " + versions.keySet());
		}

		imArgs.put("versions", versions);
		Object[] arr = mergeInherit(imArgs, versionsPath, desc, null, null, null, null, -1);

		int minLauncherVersion = (int) arr[4];
		if (minLauncherVersion > 18) {
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

	@NotNull
	private static String buildNewArgs(CList mcArg) {
		StringBuilder arg = new StringBuilder();

		for (CEntry entry : mcArg) {
			switch (entry.getType()) {
				case STRING: arg.append(slash(entry.asString())).append(' '); break;
				case MAP:
					if (isRuleFit(entry.asMap().get("rules").asList())) {
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
				default: throw new IllegalArgumentException("未知类型: " + entry.toJSON());
			}
		}
		return arg.toString();
	}

	private static String slash(String s) {
		int index = s.indexOf('=');
		if (index != -1) {
			if (s.contains(" ")) {
				return s.substring(0, index + 1)+'"'+ Tokenizer.addSlashes(s.substring(index+1))+'"';
			}
		}
		return s;
	}

	@SuppressWarnings("unchecked")
	private static Object[] mergeInherit(Map<String, Object> imArg, File version, CMapping mapping, File jar, String asset, String mainClass, String arg, int ver) throws IOException, ParseException {
		CList list = mapping.get("libraries").asList();

		if (list.size() > 0) {
			String mirror = (String) imArg.get("mirror");
			Map<String, String> libraries = (Map<String, String>) imArg.get("libraries");
			Map<String, Version> versions = (Map<String, Version>) imArg.get("versions");
			File nativePath = (File) imArg.get("nativePath");
			File libraryPath = (File) imArg.get("libraryPath");
			TaskHandler handler = (TaskHandler) imArg.get("handler");
			DownMcFile manager = new DownMcFile();
			for (int i = 0; i < list.size(); i++) {
				detectLibrary(list.get(i).asMap(), versions, libraryPath, nativePath, libraries, mirror, manager);
			}
			handler.pushTask(manager);
		}

		String jarName = mapping.getString("jar");
		if (jarName.length() > 0) {
			jar = new File(version, jarName + '/' + jarName + ".jar");
		}

		int ver1 = mapping.getInteger("minimumLauncherVersion");
		if (ver1 > ver) {
			ver = ver1;
		}

		String assets1 = mapping.getString("assets");
		if (assets1.length() > 0) {
			asset = assets1;
		}

		String mainClass1 = mapping.getString("mainClass");
		if (mainClass1.length() > 0) {
			mainClass = mainClass1;
		}

		String arg1 = mapping.getString("minecraftArguments");
		if (arg1.length() > 0) {
			arg = arg1;
		}

		String inherit = mapping.getString("inheritsFrom");
		if (inherit.length() > 0) {
			File dir = new File(version, inherit + '/' + inherit + ".json");
			CMapping desc = JSONParser.parses(IOUtil.readUTF(dir)).asMap();
			Object[] arr = mergeInherit(imArg, version, desc, jar, asset, mainClass, arg, ver);
			if (jar == null) jar = (File) arr[0];
			if (asset == null) asset = (String) arr[1];
			if (mainClass == null) mainClass = (String) arr[2];
			if (arg == null) arg = (String) arr[3];
			if ((ver1 = (int) arr[4]) > ver) {
				ver = ver1;
			}
			mapping.merge(desc, true, true);
		}

		if (jar == null) jar = new File(version, (arg1 = mapping.getString("id")) + '/' + arg1 + ".jar");

		return new Object[] {jar, asset, mainClass, arg, ver, 0};
	}

	private static void detectLibrary(CMapping data, Map<String, Version> versions, File libPath, File nativePath, Map<String, String> libraries, String mirror, DownMcFile task) {
		if (!isRuleFit(data.get("rules").asList())) {
			if (DEBUG) CLIUtil.info(data.getString("name") + " 不符合加载规则" + data.get("rules").toShortJSON());
			return;
		}

		// todo remove duplicate code
		String t1 = data.getString("name");
		int i = TextUtil.gIndexOf(t1, ':');
		CharList sb = new CharList().append(t1).replace('.', '/', 0, i);
		List<String> parts = TextUtil.split(new ArrayList<>(4), sb, ':');

		String ext = "jar";
		final String s = parts.get(parts.size() - 1);
		int extPos = s.lastIndexOf('@');
		if (extPos != -1) {
			ext = s.substring(extPos + 1);
			parts.set(parts.size() - 1, s.substring(0, extPos));
		}

		sb.clear();

		String name = sb.append(parts.get(0)).append(':').append(parts.get(1)).toString();

		Version prevVer = versions.get(name);
		Version currVer = new Version(parts.get(2));
		boolean sameVersion = false;

		if (prevVer != null) {
			switch (currVer.compareTo(prevVer)) {
				case -1:
					if (DEBUG) System.out.println("跳过旧版本 " + currVer + " 的 " + name + " (新版本:" + prevVer + ')');
					return;
				case 0:
					sameVersion = true;
					break;
				case 1:
					if (DEBUG) System.out.println("使用新版本 " + currVer + " 的 " + name + " (旧版本:" + prevVer + ')');
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
		if (nt) {
			String t;
			switch (OS.CURRENT) {
				case UNIX: t = "unix"; break;
				case WINDOWS: t = "windows"; break;
				case MAC_OS: t = "osx"; break;
				default: CLIUtil.warning("未知系统版本 " + OS.CURRENT); return;
			}
			sb.append('-').append(classifiers = natives.getString(t).replace("${arch}", OS.ARCH));
		}

		if (parts.size() > 3) {
			if (nt) CLIUtil.warning("这是哪里来的第二个classifier? " + parts);
			sb.append('-').append(parts.get(3));
		}

		String file = sb.append('.').append(ext).toString();

		if (DEBUG) CLIUtil.info("Name "+name+" File "+file);

		File libFile = new File(libPath, file);
		if (!libFile.isFile()) {
			CLIUtil.warning("库 "+libFile+" 不存在！请尝试用启动器的检查完整性来修复！");
		} else if (nt) {
			extractNatives(data, libFile, nativePath);
			return;
		}

		if (sameVersion) {
			if (DEBUG) CLIUtil.warning(name + " 版本相同且不是Natives!");
			return;
		}

		versions.put(name, currVer);
		libraries.put(name, file);
	}

	private static boolean isRuleFit(CList rules) {
		boolean fitRule = rules.size() == 0;
		for (CEntry entry : rules) {
			CMapping entry1 = entry.asMap();
			switch (entry1.getString("action")) {
				case "allow":
					if (canFitRule0(entry1)) {
						fitRule = true;
					}
					break;
				case "disallow":
					if (canFitRule0(entry1)) {
						fitRule = false;
					}
					break;
			}
		}

		return fitRule;
	}

	private static boolean canFitRule0(CMapping ruleEntry) {
		if (ruleEntry.size() == 1) return true;
		if (ruleEntry.containsKey("os")) {
			CMapping os = ruleEntry.get("os").asMap();
			if (!os.containsKey("name")) return true;
			String ver = os.getString("version");
			if (ver.length() > 0) {
				try {
					Pattern pattern = Pattern.compile(ver);
					String test = System.getProperty("os.name").replace("Windows ", "");
					if (!pattern.matcher(test).matches()) return false;
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
		if (ruleEntry.containsKey("features")) {
			CMapping features = ruleEntry.get("features").asMap();
			for (Map.Entry<String, CEntry> entry : features.entrySet()) {
				if (!canFitFeature(entry)) return false;
			}
			return true;
		}
		throw new IllegalArgumentException("未知规则: " + ruleEntry.toJSON());
	}

	private static boolean canFitFeature(Map.Entry<String, CEntry> entry) {
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
				CLIUtil.warning("FMD发现了一个未知规则: '" + k + "' 请手动判断这个规则是否符合");
				CLIUtil.warning(k + ": " + value.asString());
				try {
					return CLIUtil.readBoolean("请输入T或F并按回车: ");
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
		}
		throw new IllegalArgumentException("未知规则类型: " + value.getType());
	}

	// endregion

	private static void extractNatives(CMapping data, File libFile, File nativePath) {
		nativePath.mkdirs();

		CMapping extractInfo = data.get("extract").asMap();
		CList exclude = extractInfo.get("exclude").asList();

		TrieTreeSet trieTree = new TrieTreeSet();
		for (CEntry entry : exclude) {
			trieTree.add(entry.asString());
		}
		trieTree.add("META-INF");

		if (DEBUG) System.out.println("解压natives " + libFile);
		try {
			ZipFile zipFile = new ZipFile(libFile);
			Enumeration<? extends ZipEntry> enumeration = zipFile.entries();
			while (enumeration.hasMoreElements()) {
				ZipEntry zipEntry = enumeration.nextElement();
				final String name = zipEntry.getName();
				if (!zipEntry.isDirectory() && !trieTree.strStartsWithThis(name) && (name.endsWith(".dll") || name.endsWith(".so"))) {
					try (FileOutputStream fos = new FileOutputStream(new File(nativePath, name))) {
						fos.write(roj.io.IOUtil.read(zipFile.getInputStream(zipEntry)));
					}
				} else if (DEBUG) {
					CLIUtil.info("排除文件 " + zipEntry);
				}
			}
		} catch (IOException e) {
			CLIUtil.warning("Natives 无法读取!");
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
		public void execute() throws Exception {
			runClient(config.get("mc_conf").asMap(), log ? 3 : 2, this);
			run = true;
		}

		public boolean isDone() {
			return run;
		}

		@Override
		public boolean isCancelled() {
			return false;
		}

		@Override
		public boolean cancel(boolean force) {
			if (run || process == null) return false;
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

	// endregion
	// region download Util

	public static File downloadMinecraftFile(CMapping downloads, String name, String mirror) throws IOException {
		final CMapping map = downloads.get(name).asMap();
		String sha1 = map.get("sha1").asString();

		File tmpFile = new File(TMP_DIR, sha1 + '.' + name);

		downloadMinecraftFile(map, tmpFile, mirror);
		return tmpFile;
	}

	public static void downloadMinecraftFile(CMapping map, File target, String mirror) throws IOException {
		String url = replaceMirror(map, mirror);

		if (!target.exists()) {
			CLIUtil.info("开始下载 " + url);
			DownMcFile man = new DownMcFile();
			man.add(url, target, map.getString("sha1"), null);
			man.invoke();
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

	private static final class DownMcFile extends AsyncTask<Void> {
		private final List<Entry> entries = new ArrayList<>();

		public void setWaitable() {
			wait = true;
			except = System.currentTimeMillis() + TIMEOUT_TIME;
		}

		static final class Entry {
			final String url;
			final File target;
			final String digest;
			String lastDigest;
			int retry;

			Waitable future;
			Runnable callback;

			public Entry(String name, File file, String digest, Runnable callback) {
				this.url = name;
				this.target = file;
				if (digest != null) digest = digest.toLowerCase();
				this.digest = digest;
				this.callback = callback;
			}
		}

		private final IOUtil uc;
		private MessageDigest DIG;
		private final ProgressGroupedMulti hdr = new ProgressGroupedMulti();

		private long except;
		private boolean wait;

		static final int maxTask = CONFIG.getInteger("通用.最大同时下载数");

		public DownMcFile() {
			this("sha1");
		}

		public DownMcFile(String alg) {
			uc = new IOUtil();
			try {
				DIG = MessageDigest.getInstance(alg);
			} catch (NoSuchAlgorithmException ignored) {}
		}

		public void add(String url, File file, String hash, Runnable cb) {
			entries.add(new Entry(url, file, hash, cb));
		}

		void refresh() {
			for (int i = 0; i < entries.size(); i++) {
				try {
					Entry e = entries.get(i);
					if (e.future != null) {
						e.future.cancel();
						e.future = null;
					}
				} catch (Exception ignored) {}
			}
		}

		@Override
		public Void invoke() throws IOException {
			if (wait && System.currentTimeMillis() > except) {
				int r = ifBreakWait();
				if (r == -1) {
					cancel(true);
					return null;
				}
				if (r == 1) {wait = false;} else refresh();
				except = System.currentTimeMillis() + TIMEOUT_TIME;
			}

			int count = 0;
			for (int i = entries.size() - 1; i >= 0; i--) {
				Entry e = entries.get(i);
				if (e.future != null) {
					if (e.future.isDone()) {
						try {
							e.future.waitFor();
							e.future = null;
							if (verifyFile(e)) {
								if (e.callback != null) e.callback.run();
								entries.remove(i);
							}
						} catch (Throwable ex) {
							handleError(e, ex);
						}
					} else {
						count++;
					}
				}
			}

			for (int i = entries.size() - 1; i >= 0; i--) {
				Entry e = entries.get(i);
				if (count < maxTask && e.future == null) {
					count++;
					if (e.retry > 3) {
						CLIUtil.warning(e.url.substring(e.url.lastIndexOf('/') + 1) + " 失败三次, 取消");
						entries.remove(i);
						continue;
					}
					try {
						e.future = DownloadTask.downloadMTD(e.url, e.target, hdr.subProgress());
					} catch (Throwable ex) {
						handleError(e, ex);
					}
				}
			}
			try {
				Thread.sleep(1);
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
			if (!entries.isEmpty()) return null;

			return null;
		}

		private boolean verifyFile(Entry e) throws IOException {
			if (e.target.isFile()) {
				if (e.digest != null && !e.digest.isEmpty()) {
					DIG.reset();
					try (FileInputStream in = new FileInputStream(e.target)) {
						ByteList bb = IOUtil.getSharedByteBuf();
						bb.ensureCapacity(4096);
						byte[] list = bb.list;

						do {
							int r = in.read(list);
							if (r < 0) break;
							DIG.update(list, 0, r);
						} while (true);
					}

					String DIGEST = uc.encodeHex(DIG.digest());
					if (!DIGEST.equalsIgnoreCase(e.digest)) {
						if (DIGEST.equals(e.lastDigest)) {
							CLIUtil.warning("二次SHA1相同,镜像问题? " + e.url);
						} else {
							e.lastDigest = DIGEST;
							e.future = null;
							e.retry++;

							if (DEBUG) CLIUtil.warning("DIG For " + e.url + " : " + DIGEST);
							if (!e.target.delete()) {
								CLIUtil.warning(e.target.getName() + "删除失败!");
							}
							return false;
						}
					} else {
						CLIUtil.success(e.url.substring(e.url.lastIndexOf('/') + 1) + " 完毕. (" + entries.size() + "剩余)");
						return true;
					}
				}
				return true;
			}
			return false;
		}

		private static void handleError(Entry e, Throwable ex) {
			e.future = null;

			if (ex instanceof CancellationException) {
				CLIUtil.warning("下载被取消");
				e.retry = 99;
				return;
			}

			CLIUtil.error(e.url.substring(e.url.lastIndexOf('/') + 1) + " 下载失败.");
			ex.printStackTrace();

			e.retry++;
		}

		//TODO
		@Override
		public boolean cancel(boolean force) {
			refresh();
			entries.clear();
			return true;
		}

		@Override
		public boolean repeating() {
			return !entries.isEmpty();
		}
	}

	// endregion
	// region Process management

	@SuppressWarnings("unchecked")
	public static int runProcess(List<String> tokens, File dir, int flag, Object obj) throws IOException {
		if (DEBUG) System.out.println("参数: '" + tokens + "'");
		ProcessBuilder pb = new ProcessBuilder(tokens).directory(dir).redirectErrorStream(true);
		if ((flag & 1) != 0) {
			pb.redirectOutput(ProcessBuilder.Redirect.INHERIT).redirectError(ProcessBuilder.Redirect.INHERIT);
		} else {
			File debug = (flag & 4) == 0 ? new File(dir, "minecraft.log") : (File) obj;
			pb.redirectOutput(ProcessBuilder.Redirect.appendTo(debug)).redirectError(ProcessBuilder.Redirect.appendTo(debug));
		}

		Process process = pb.start();
		if (obj instanceof Consumer) ((Consumer<Process>) obj).accept(process);

		// 会导致无法窗口化, 可能是缓冲区满了....
		if ((flag & 2) != 0) {
			boolean x = false;
			while (process.isAlive()) {
				try {
					process.waitFor();
				} catch (InterruptedException ignored) {
					x = true;
				}
			}
			if (x) Thread.currentThread().interrupt();

			CLIUtil.info("进程终止");
			return process.exitValue();
		}
		return 0;
	}

	// endregion
}