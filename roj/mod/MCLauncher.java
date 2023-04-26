package roj.mod;

import roj.RequireUpgrade;
import roj.archive.zip.ZipArchive;
import roj.asm.Opcodes;
import roj.asm.Parser;
import roj.asm.tree.ConstantData;
import roj.asm.tree.Method;
import roj.asm.tree.attr.AttrCode;
import roj.asm.tree.insn.InsnList;
import roj.collect.MyHashMap;
import roj.collect.MyHashSet;
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
import roj.config.word.ITokenizer;
import roj.config.word.Tokenizer;
import roj.config.word.Word;
import roj.io.ChineseInputStream;
import roj.io.IOUtil;
import roj.io.down.DownloadTask;
import roj.io.down.ProgressGroupedMulti;
import roj.io.down.ProgressMulti;
import roj.math.Version;
import roj.text.CharList;
import roj.text.Template;
import roj.text.TextUtil;
import roj.text.UTFCoder;
import roj.ui.CmdUtil;
import roj.ui.UIUtil;
import roj.util.ByteList;
import roj.util.OS;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
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
 * @since 2021/5/31 21:17
 */
@RequireUpgrade
public class MCLauncher extends JFrame {
	@Nullable
	static JFrame activeWindow;

	public static void main(String[] args) {
		UIUtil.systemLook();
		load();

		File mcRoot = new File(CONFIG.getString("通用.MC目录"));

		if (!mcRoot.isDirectory() && !mcRoot.mkdirs()) {
			error("无法创建minecraft目录");
			return;
		}

		Shared.watcher.terminate();

		activeWindow = new MCLauncher();
		activeWindow.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
	}

	public MCLauncher() {
		super("Roj234的启动器 " + VERSION);
		UIUtil.setLogo(this, "MCL_logo.png");

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

		button = new JButton("清日志");
		button.addActionListener(MCLauncher::clearLogs);
		button.setToolTipText("一键清理垃圾文件");
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

		setMinimumSize(new Dimension(240, 100));
		setLayout(new FlowLayout());
		pack();
		setBounds(0, 0, 320, 100);
		UIUtil.center(this);
		setResizable(true);
		setVisible(true);
	}

	public static void clearLogs(ActionEvent e) {
		if (checkMCRun()) return;

		if (!config.containsKey("mc_conf")) {
			error("没有选择版本!");
			return;
		}

		File basePath = new File(config.getString("mc_conf.root"));
		File debugLog = new File(basePath, "minecraft.log");
		if (e != null || debugLog.length() > 8388608) {
			IOUtil.deleteFile(debugLog);
		}
		File logs = new File(basePath, "logs");
		IOUtil.deletePath(logs);
		File crashes = new File(basePath, "crash-reports");
		IOUtil.deletePath(crashes);

		CmdUtil.warning("清除了没啥用的日志文件!");
	}

	// region Select version

	private static void selectVersion(ActionEvent event) {
		if (checkMCRun()) return;

		File mcRoot = new File(CONFIG.getString("通用.MC目录"));
		if (!mcRoot.isDirectory() && !mcRoot.mkdirs()) {
			error("无法创建minecraft目录");
			return;
		}

		List<File> versions = findVersions(new File(mcRoot, "/versions/"));

		File mcJson;
		if (versions.size() < 1) {
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
				if (obj[i].equals(cv)) {
					break;
				}
			}
			if (i >= obj.length) i = 0;

			String s = (String) JOptionPane.showInputDialog(activeWindow, "请选择你的MC版本:\n", "询问", JOptionPane.QUESTION_MESSAGE, null, obj, obj[i]);
			if (s == null) return;
			for (i = 0; i < obj.length; i++) {
				if (s == obj[i]) break;
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
		if (files != null) {
			for (File file1 : files) {
				if (file1.isDirectory()) {
					File file2 = new File(file1, "/" + file1.getName() + ".json");
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
		int m = JOptionPane.showOptionDialog(activeWindow, "等了一分钟了，还没下完\n" + "然后插句嘴，如果你觉得下的太慢了，直接关闭窗口好了\n" + "我们会实时保存下载进度", "询问", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE, null,
											 options, options[0]);

		switch (m) {
			case JOptionPane.YES_OPTION: // con
				return 0;
			default:
			case JOptionPane.NO_OPTION: // not
				Task.clearTasks();
				return -1;
			case JOptionPane.CANCEL_OPTION: // ignore
				return 1;
		}
	}

	static final class Waiter implements ITask {
		private final PrintStream out;

		public Waiter(PrintStream out) {
			this.out = out;
		}

		@Override
		public void execute() {
			try {
				Thread.sleep(1500);
			} catch (InterruptedException ignored) {}
			out.println("剩余任务: " + Task.taskPending());
		}

		@Override
		public boolean continueExecuting() {
			return Task.taskPending() > 0;
		}
	}

	// endregion
	// region download MC

	private static void download(ActionEvent event) {
		CMapping cfgLan = CONFIG.get("通用").asMap();

		File mcRoot = new File(cfgLan.getString("MC目录"));
		if (!mcRoot.isDirectory() && !mcRoot.mkdirs()) {
			error("无法创建minecraft目录");
		}

		Object[] options = {"MC", config.getString("mc_version") + "的Forge"};
		int m = JOptionPane.showOptionDialog(activeWindow, "你下嘛？", "询问", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
		if (m == JOptionPane.YES_OPTION) {
			CList versions = getMcVersionList(cfgLan);
			if (versions == null) return;

			new VersionSelect(versions.raw(), false);
		} else if (m == JOptionPane.NO_OPTION) {
			if (config.getString("mc_version").contains("forge")) {
				if (JOptionPane.showConfirmDialog(activeWindow, "似乎这个版本装了forge,继续吗?", "询问", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null) != JOptionPane.YES_OPTION) return;
			}

			if (!config.containsKey("mc_conf")) {
				error("请选择一个MC版本");
				return;
			}

			String mcVer = config.getString("mc_version");

			CList versions;
			try {
				CharList out = new CharList(10000);
				ByteList.decodeUTF(-1, out, IOUtil.downloadFileToMemory(cfgLan.getString("forge版本manifest地址").replace("<mc_ver>", mcVer)));

				versions = JSONParser.parses(out, JSONParser.INTERN).asList();
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
		if (versions == null) {
			try {
				CharList out = new CharList(100000);
				ByteList.decodeUTF(-1, out, IOUtil.downloadFileToMemory(cfgLan.getString("mc版本manifest地址")));

				cache_mc_versions = versions = JSONParser.parses(out, JSONParser.INTERN).asMap().get("versions").asList();
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
		if (mcJson == null) {
			String jar = mc_conf.getString("jar");
			try {
				mcJson = JSONParser.parses(IOUtil.readUTF(new FileInputStream(jar.substring(0, jar.lastIndexOf('.')) + ".json"))).asMap();
			} catch (ParseException e) {
				throw new IOException("无效的MC配置json", e);
			}
		}

		File assets = new File(mc_conf.getString("assets_root") + File.separatorChar + "assets");
		if (!assets.isDirectory() && !assets.mkdirs()) {
			throw new IOException("无法创建assets目录");
		}
		File index = new File(assets, "/indexes/");
		if (!index.isDirectory() && !index.mkdir()) {
			throw new IOException("无法创建assets/indexes目录");
		}
		index = new File(index, '/' + mc_conf.getString("assets") + ".json");
		if (!index.isFile()) {
			downloadMinecraftFile(mcJson.get("assetIndex").asMap(), index, mirrorA);
		}
		CMapping objects;
		try {
			objects = JSONParser.parses(IOUtil.readUTF(new FileInputStream(index))).asMap().get("objects").asMap();
		} catch (ParseException e) {
			throw new IOException("无效的索引json", e);
		}

		assets = new File(assets, "/objects/");
		CharList tmp = new CharList();

		MyHashSet<String> hashes = new MyHashSet<>();
		DownMcFile manager = new DownMcFile();

		for (Map.Entry<String, CEntry> entry : objects.entrySet()) {
			CMapping val = entry.getValue().asMap();
			String hash = val.getString("hash");
			if (!hashes.add(hash)) // 跳过重复的文件
			{continue;}
			String url = tmp.append(hash, 0, 2).append('/').append(hash).toString();
			tmp.clear();
			File asset = new File(assets, url);
			if (!asset.isFile() || asset.length() != val.getInteger("size")) {
				if (asset.isFile() && !asset.delete()) {
					CmdUtil.error("无法删除错误文件 " + asset);
					continue;
				}
				File pa = asset.getParentFile();
				if (!pa.isDirectory() && !pa.mkdirs()) {
					CmdUtil.error("无法创建文件 " + pa);
					continue;
				}

				val.put("sha1", val.getString("hash"));
				val.remove("size");
				val.remove("hash");
				val.put("url", mirrorB + url);
				String url1 = replaceMirror(val, mirrorB);

				manager.add(url1, asset, val.getString("sha1"), null);
			}
		}

		int cnt = manager.entries.size();
		if (cnt > 0) {
			manager.setWaitable();
			Task.pushTask(manager);
		}

		return cnt;
	}

	public static boolean installMinecraftClient(File mcRoot, File mcJson, boolean doAssets) {
		CMapping cfgGen = CONFIG.get("通用").asMap();

		Object[] result;
		CMapping jsonDesc, mc_conf;
		try {
			File mcNative = new File(mcJson.getParentFile(), "/$natives/");
			result = getRunConf(mcRoot, mcJson, mcNative, cfgGen);
			config.put("mc_conf", mc_conf = (CMapping) result[0]);
			jsonDesc = (CMapping) result[3];
			config.put("mc_version", jsonDesc.getString("id"));

			save();
		} catch (IOException e) {
			error("获取RunConfig出了点错...\n请查看控制台");
			e.printStackTrace();
			return false;
		}

		waitFor(null);

		config.put("high_ver", result[4] == Boolean.TRUE);

		if (!doAssets) return true;

		if (!cfgGen.getString("assets地址").isEmpty()) {
			CmdUtil.info("补全assets...");
			try {
				int count = completeAssets(cfgGen.getBool("下载MC相关文件使用镜像") ? cfgGen.getString("镜像地址") : null, cfgGen.getString("assets地址"), mc_conf, jsonDesc);
				CmdUtil.success("assets补全完毕, " + count);
			} catch (IOException e) {
				error("asset补全出了点错...\n请查看控制台");
				e.printStackTrace();
			}
		}
		return true;
	}

	public static void onClickInstall(CMapping map, boolean fg) {
		CMapping cfgGen = CONFIG.get("通用").asMap();

		if (!fg) {
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
				if (!json.isFile()) {
					DownloadTask.downloadMTD(url, target).waitFor();
				}
				download = JSONParser.parses(IOUtil.readUTF(new FileInputStream(json.isFile() ? json : target))).asMap().get("downloads").asMap().get("client").asMap();
			} catch (IOException | ParseException e) {
				error("下载/解析版本json出了点错...\n请查看控制台");
				e.printStackTrace();
				return;
			}

			File mcFile = new File(versionPath, name + ".jar");

			try {
				downloadMinecraftFile(download, mcFile, cfgGen.getBool("下载MC相关文件使用镜像") ? cfgGen.getString("镜像地址") : null);
			} catch (IOException e) {
				error("下载版本jar出了点错...\n请查看控制台");
				e.printStackTrace();
				return;
			}

			if (!json.isFile() && !target.renameTo(json)) {
				error("版本json没法修改");
				return;
			}

			if (activeWindow instanceof MCLauncher) info(name + "下载成功.");
		} else {
			CharList tmp0 = new CharList(20).append(config.getString("mc_version")).append('-').append(map.getString("version"));
			if (map.containsKey("branch", roj.config.data.Type.STRING)) {
				tmp0.append('-').append(map.getString("branch"));
			}

			CharList tmp1 = new CharList(50).append(cfgGen.getString("协议")).append(cfgGen.getString("forge地址")).append("/net/minecraftforge/forge/");
			tmp1.append(tmp0);
			tmp1.append("/forge-").append(tmp0);

			String url = tmp1.append("-installer.jar").toString();

			CMapping found = null;
			for (CEntry entry : map.get("files").asList()) {
				CMapping ent = entry.asMap();
				if (ent.getString("category").equals("installer")) {
					found = ent;
					break;
				}
			}
			String md5 = null;
			if (found == null || !found.containsKey("hash")) {CmdUtil.warning("没有找到installer类别,无法校验文件MD5, raw: " + map.toJSON());} else md5 = found.getString("hash");

			File tmpFile = new File(TMP_DIR, tmp0.insert(0, "forge-").append("-installer.jar").toString());

			try {
				downloadAndVerifyMD5(url, md5, tmpFile);
			} catch (IOException e) {
				error("Forge下载失败! \n请看控制台");
				e.printStackTrace();
				return;
			}

			if (activeWindow instanceof MCLauncher) info("Forge由广告链接支持,若自动化了它的下载及安装\n请考虑点这里赞助 https://www.patreon.com/LexManos/\n点击确定继续安装");

			try {
				if (installForge(tmpFile, map.getString("version"), config.getBool("high_ver"))) {
					waitFor(null);
					if (activeWindow instanceof MCLauncher) info("安装成功,您可以手动删除tmp目录下的\nfg-xxx-tmp-lib目录和xxx-fgInst-tmp.jar");
				}
			} catch (IOException e) {
				error("Forge安装失败! \n请看控制台");
				e.printStackTrace();
			}
		}
	}

	private static boolean installForge(File fgInstaller, String fgVersion, boolean highVersion) throws IOException {
		CMapping cfgGen = CONFIG.get("通用").asMap();

		File mcRoot = new File(cfgGen.getString("MC目录"));
		if (!mcRoot.isDirectory() && !mcRoot.mkdirs()) {
			error("无法创建minecraft目录");
			return false;
		}

		final String ver = config.getString("mc_version") + "-forge-" + fgVersion;
		File installDir = new File(mcRoot, "/versions/" + ver + '/');
		if (installDir.isDirectory()) {
			//error("forge可能已安装,若之前安装失败,请手动删除目录 " + installDir.getAbsolutePath());
			//return false;
		} else if (!installDir.mkdirs()) {
			error("forge存放目录创建失败");
			return false;
		}

		ZipFile zf = new ZipFile(fgInstaller);

		ZipEntry instProf = zf.getEntry("install_profile.json");
		if (instProf == null) {
			zf.close();
			error("找不到安装配置 install_profile.json");
			return false;
		}

		CMapping instConf;
		try {
			instConf = JSONParser.parses(IOUtil.readUTF(zf.getInputStream(instProf))).asMap();
		} catch (ParseException e) {
			e.printStackTrace();
			zf.close();
			error("安装配置解析失败");
			return false;
		}

		if (instConf.containsKey("icon")) {
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
				bl.readStreamFully(in).writeToStream(fos);
				in.close();
				bl.clear();
			}

			File mcLibPath = new File(mcRoot, "/libraries/");
			if (instConf.get("processors").asList().size() > 0 || "true".equals(System.getProperty("fmd.forceDownloadForgeExternalLibraries"))) {
				CmdUtil.info("开始下载Forge安装所需jar...");

				File libraryPath = new File(TMP_DIR, "fg-" + ver + "-tmp-lib");
				if (!libraryPath.isDirectory() && !libraryPath.mkdir()) {
					CmdUtil.warning("无法创建临时目录 " + libraryPath.getAbsolutePath());
				}

				DownMcFile manager = new DownMcFile();

				Map<String, Version> versions = new MyHashMap<>();
				Map<String, String> libraries = new MyHashMap<>();
				final String libUrl = cfgGen.getString("libraries地址");
				for (CEntry entry : instConf.get("libraries").asList()) {
					final CMapping data = entry.asMap();
					if (data.getString("name").startsWith("net.minecraftforge:forge:") && data.get("downloads").asMap().get("artifact").asMap().getString("url").isEmpty()) continue;

					detectLibrary(data, versions, libraryPath, null, libraries, libUrl, manager);
				}
				Task.pushTask(manager);

				String tmp = libraryPath.getAbsolutePath() + File.separatorChar;
				CharList cpTmp = new CharList(1000);
				for (String lib : libraries.values()) {
					cpTmp.append(tmp).append(lib).append(File.pathSeparatorChar);
				}
				int cpLen = cpTmp.length();

				Set<String> forced = CONFIG.get("通用.作为MC自身lib的key").asList().asStringSet();
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
							bl.readStreamFully(in).writeToStream(fos);
							in.close();
							bl.clear();
						}

					} else if (s.startsWith("[")) {
						v = new File(forced.contains(entry.getKey()) ? mcLibPath : libraryPath, IOUtil.mavenPath(s.substring(1, s.length() - 1)).toString()).getAbsolutePath();
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
							bl.readStreamFully(in).writeToStream(fos);
							in.close();
							bl.clear();
						}
					}
				}
				zf.close();

				waitFor(null);
				CmdUtil.info("开始安装Forge...");
				if (instConf.getInteger("spec") == 1) CmdUtil.error("太妙了！forge正在把我要下载的东西单线程重新下一遍！失败了别找我哦\n 按确定继续");

				new File(BASE, "forge_install.log").delete();

				List<String> keys = new ArrayList<>();
				CList processors = instConf.get("processors").asList();
				for (int i = 0; i < processors.size(); i++) {
					CMapping op = processors.get(i).asMap();
					CList sides = op.getOrCreateList("sides");
					if (sides.size() != 0) {
						boolean cl = false;
						for (int j = 0; j < sides.size(); j++) {
							if (sides.get(j).asString().equals("client")) {
								cl = true;
								break;
							}
						}
						if (!cl) {
							CmdUtil.info("服务端处理器: " + i + ", 跳过");
						}
					}
					File path = new File(libraryPath, IOUtil.mavenPath(op.getString("jar")).toString());
					if (!path.isFile()) {
						CmdUtil.error("无法找到文件 " + path.getAbsolutePath());
						return false;
					}

					keys.clear();
					keys.add("java");
					keys.add("-cp");
					keys.add(cpTmp.append(path.getAbsolutePath()).toString());
					cpTmp.setLength(cpLen);

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
							s = new File(libraryPath, IOUtil.mavenPath(s.substring(1, s.length() - 1)).toString()).getAbsolutePath();
						} else {
							int j = s.indexOf('{');
							if (j != -1) {
								CharList cl = new CharList(s.length()).append(s, 0, j);
								for (; j < s.length(); j++) {
									char c = s.charAt(j);
									if (c == '{') {
										int k = j;
										while (s.charAt(k) != '}') k++;
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

					if (runProcess(keys, TMP_DIR, 6, new File(BASE, "forge_install.log")) != 0) {
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
					if (!ze.isDirectory() && ze.getName().startsWith("maven/")) {
						File dest = new File(mcLibPath, ze.getName().substring(6));
						System.out.println("导出 " + ze.getName());

						File p = dest.getParentFile();
						if (!p.isDirectory() && !p.mkdirs()) {
							CmdUtil.warning("无法创建目录");
						}

						try (FileOutputStream fos = new FileOutputStream(dest)) {
							final InputStream in = zf.getInputStream(ze);
							bl.readStreamFully(in).writeToStream(fos);
							in.close();
							bl.clear();
						}
					}
				}
				zf.close();
			}

			final File dst = new File(installDir, ver + ".json");
			if (!mcJsonTmp.renameTo(dst)) {
				IOUtil.copyFile(mcJsonTmp, dst);
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

			File dest = new File(mcRoot, "/libraries/" + IOUtil.mavenPath(install.getString("path")));
			File parent = dest.getParentFile();
			if (!parent.isDirectory() && !parent.mkdirs()) {
				error("无法创建forge jar保存目录 " + dest.getAbsolutePath());
				return false;
			}

			ByteList bl = new ByteList();
			try (FileOutputStream fos = new FileOutputStream(dest)) {
				bl.readStreamFully(zf.getInputStream(forge));
				bl.writeToStream(fos);
			}

			try (FileOutputStream fos = new FileOutputStream(new File(installDir, ver + ".json"))) {
				bl.putUTFData(instConf.get("versionInfo").toShortJSONb()).writeToStream(fos);
				bl.clear();
			}

			zf.close();
		}

		CmdUtil.success("安装完毕.");
		return true;
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
			if (activeWindow != null) activeWindow.requestFocus();
		}

		@Override
		protected void search(ActionEvent event) {
			result.removeAll();
			int y = 2;

			String str = searchText.getText().toLowerCase();
			List<String> entries = new ArrayList<>();
			final String id = fg ? "version" : "id";
			for (int i = 0; i < versions.size(); i++) {
				CEntry entry = versions.get(i);
				CMapping map = entry.asMap();
				if (map.getString(id).toLowerCase().startsWith(str)) {
					entries.add(map.getString(id));
					if (entries.size() == 100) break;
				}
			}

			if (entries.isEmpty()) {
				JLabel labelNotify = new JLabel("没有结果!");
				labelNotify.setForeground(Color.RED);
				labelNotify.setBounds(220, y, 80, 15);
				y += 22;
				result.add(labelNotify);
			} else if (entries.size() >= 100) {
				JLabel labelNotify = new JLabel("结果超过" + 100 + "个!");
				labelNotify.setForeground(Color.RED);
				labelNotify.setBounds(200, y, 100, 15);
				y += 22;
				result.add(labelNotify);
			}
			for (String entry : entries) {
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
			String text = ((JButton) event.getSource()).getText();
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
					CmdUtil.warning("文件的编码中含有BOM(推荐使用UTF-8无BOM格式!), 识别的编码: " + bom.getCharset());
				}

				config = JSONParser.parses(IOUtil.readAs(bom, bom.getCharset())).asMap();
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

	// region edit

	private static void config(ActionEvent event) {
		if (!config.containsKey("mc_conf", roj.config.data.Type.MAP)) {
			error("请选择版本!");
		} else {
			if (!config.getBool("tip1")) {
				error("这里的修改在重新选择版本后会被通用配置覆盖\n" + "启动配置保存在launcher.json\n" + "通用配置保存在config.json");
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
			label.setBounds(10, 10, 80, 25);
			add(label);

			label = new JLabel("JVM参数:");
			label.setBounds(10, 40, 80, 25);
			add(label);

			label = new JLabel("MC参数:");
			label.setBounds(10, 70, 80, 25);
			add(label);

			final CMapping mc_conf = config.get("mc_conf").asMap();

			nameInput = new JTextField(mc_conf.getString("player_name"), 0);
			nameInput.setBounds(80, 10, 500, 25);
			add(nameInput);

			jvmInput = new JTextField(mc_conf.getString("jvmArg"), 0);
			jvmInput.setBounds(80, 40, 500, 25);
			add(jvmInput);

			mcInput = new JTextField(mc_conf.getString("mcArg"), 0);
			mcInput.setBounds(80, 70, 500, 25);
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
			CMapping mc_conf = config.get("mc_conf").asMap();
			String s = nameInput.getText();
			if (s.isEmpty()) {mc_conf.remove("player_name");} else mc_conf.put("player_name", s);
			mc_conf.put("jvmArg", jvmInput.getText().trim());
			mc_conf.put("mcArg", mcInput.getText().trim());
			MCLauncher.save();
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

	public static int runClient(CMapping mc_conf, int processFlags, Consumer<Process> consumer) throws IOException {
		Map<String, String> env = new MyHashMap<>();
		File nativePath = new File(mc_conf.getString("native_path"));
		env.put("natives_directory", '"' + ITokenizer.addSlashes(nativePath.getAbsolutePath()) + '"');
		env.put("classpath", '"' + ITokenizer.addSlashes(new StringBuilder(mc_conf.getString("libraries")).append(mc_conf.getString("jar"))) + '"');
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
		replace(mc_conf.get("jvmArg").asList().get(0), env, args);
		args.add(mc_conf.getString("mainClass"));

		String playerName = mc_conf.getString("player_name");
		if (playerName.equals("")) playerName = CONFIG.getString("通用.玩家名字");

		String authName = null, authToken = null, authUUID = null;
		File def = new File(CONFIG.getString("通用.外部accessToken"));
		if (def.isFile()) {
			try {
				CMapping map = JSONParser.parses(IOUtil.readUTF(def)).asMap();
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
		mcEnv.put("assets_root", '"' + ITokenizer.addSlashes(mc_conf.getString("assets_root") + File.separatorChar + "assets") + '"');
		mcEnv.put("game_directory", mc_conf.getString("root"));
		mcEnv.put("version_name", "FMDv" + VERSION);
		mcEnv.put("auth_player_name", authName);
		mcEnv.put("user_properties", "{}");
		replace(mc_conf.get("mcArg"), mcEnv, args);

		CmdUtil.info("启动客户端...");

		return runProcess(args, new File(mc_conf.getString("root")), processFlags, consumer);
	}

	private static final Tokenizer l = new Tokenizer().literalEnd(" \r\n\t\f").defaultC2C(0);
	private static void replace(CEntry arg, Map<String, String> env, SimpleList<String> args) {
		CharList sb = new CharList();
		Template.replaceOnce(env, arg.asString(), sb);
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

	// region UI
	private static void runClient(ActionEvent event) {
		if (checkMCRun()) return;
		clearLogs(null);

		if (!config.containsKey("mc_conf")) {
			error("没有选择版本!");
			return;
		}

		Task.pushTask(task = new RunMinecraftTask(false));
	}

	private static void debug(ActionEvent event) {
		if (checkMCRun()) return;
		clearLogs(null);

		if (!config.containsKey("mc_conf")) {
			error("没有选择版本!");
			return;
		}

		Task.pushTask(task = new RunMinecraftTask(true));
	}

	// endregion

	// region prepare MC

	public static Object[] getRunConf(File mcRoot, File mcJson, File mcNative, CMapping cfg) throws IOException {
		return getRunConf(mcRoot, mcJson, mcNative, Collections.emptyList(), true, cfg);
	}

	public static Object[] getRunConf(File mcRoot, File mcJson, File nativePath, Collection<String> skipped, boolean cleanNatives, CMapping cfg) throws IOException {
		return getRunConf(mcRoot, mcJson, nativePath, skipped, cleanNatives, cfg.getBool("版本隔离"), cfg.getString("libraries地址"), cfg.getString("附加JVM参数"), cfg.getString("附加MC参数"));
	}

	public static Object[] getRunConf(File mcRoot, File mcJson, File nativePath, Collection<String> skipped, boolean cleanNatives, boolean insulation, String mirror, String jvmArg, String mcArg) throws IOException {
		CMapping mc_conf = new CCommMap();
		mc_conf.getComments().put("java", "自定义java路径");
		mc_conf.put("java", "");

		mc_conf.put("assets_root", mcRoot.getAbsolutePath());
		mc_conf.put("root", insulation ? mcJson.getParentFile().getAbsolutePath() : mcRoot.getAbsolutePath());
		mc_conf.put("native_path", nativePath.getAbsolutePath());

		if (nativePath.isDirectory()) {
			if (cleanNatives && !IOUtil.deletePath(nativePath)) {
				CmdUtil.error("无法清空natives, 请手动删除");
				return null;
			}
		} else if (!nativePath.mkdirs()) {
			CmdUtil.error("无法创建natives");
			return null;
		}

		Map<String, String> libraries = new MyHashMap<>();
		boolean is113andAbove = false;
		File mcJar;
		CMapping jsonDesc;

		try {
			jsonDesc = JSONParser.parses(IOUtil.readUTF(new FileInputStream(mcJson))).asMap();

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
			CmdUtil.info("注: 跳过的依赖 " + versions.keySet());
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

	@Nonnull
	private static String buildNewArgs(CList mcArg) {
		StringBuilder arg = new StringBuilder();

		for (CEntry entry : mcArg) {
			switch (entry.getType()) {
				case STRING:
					arg.append(slash(entry.asString())).append(' ');
					break;
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
				default:
					throw new IllegalArgumentException("未知类型: " + entry.toJSON());
			}
		}
		return arg.toString();
	}

	private static String slash(String s) {
		int index = s.indexOf('=');
		if (index != -1) {
			if (s.contains(" ")) {
				return s.substring(0, index + 1) + '"' + ITokenizer.addSlashes(s.substring(index + 1)) + '"';
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
			CMapping desc = JSONParser.parses(IOUtil.readUTF(new FileInputStream(dir))).asMap();
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
			if (DEBUG) CmdUtil.info(data.getString("name") + " 不符合加载规则" + data.get("rules").toShortJSON());
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
				default: CmdUtil.warning("未知系统版本 " + OS.CURRENT); return;
			}
			sb.append('-').append(classifiers = natives.getString(t).replace("${arch}", OS.ARCH));
		}

		if (parts.size() > 3) {
			if (nt) CmdUtil.warning("这是哪里来的第二个classifier? " + parts);
			sb.append('-').append(parts.get(3));
		}

		String file = sb.append('.').append(ext).toString();

		if (DEBUG) {
			CmdUtil.info("Name " + name + " File " + file);
		}
		File libFile = new File(libPath, file);
		if (!libFile.isFile()) {
			if (mirror != null) {
				final CMapping downloads = data.containsKey("downloads") ? data.get("downloads").asMap() : data;
				Runnable cb = nt ? () -> {
					extractNatives(data, libFile, nativePath);
				} : name.endsWith("log4j-core") ? () -> {
					fixLog4j(libFile);
				} : null;
				downloadLibrary(downloads, mirror, libFile, task, cb, classifiers, file);
				if (nt) return;
			} else {
				if (DEBUG) CmdUtil.warning(libFile + " 不存在!");
			}
		} else if (nt) {
			extractNatives(data, libFile, nativePath);
			return;
		} else {
			// Log4j2漏洞
			if (name.endsWith("log4j-core")) fixLog4j(libFile);
		}

		if (sameVersion) {
			if (DEBUG) CmdUtil.warning(name + " 版本相同且不是Natives!");
			return;
		}

		versions.put(name, currVer);
		libraries.put(name, file);
	}

	private static void fixLog4j(File lib) {
		try {
			ZipArchive mzf = new ZipArchive(lib);
			byte[] b = mzf.get("org/apache/logging/log4j/core/lookup/JndiLookup.class");
			if (b != null) {
				ConstantData data = Parser.parseConstants(b);
				Method mn = data.getUpgradedMethod("lookup");

				AttrCode code = mn.getCode();
				InsnList insn = code.instructions;
				if (insn.size() > 3) {
					code.clear();

					insn.ldc("JNDI功能已关闭");
					insn.one(Opcodes.ARETURN);
					code.stackSize = 1;
					code.localSize = 3;
					mzf.put("org/apache/logging/log4j/core/lookup/JndiLookup.class", new ByteList(Parser.toByteArray(data)));
					mzf.store();
					CmdUtil.success("修补了Log4j2漏洞");
				} else {
					CmdUtil.success("之前修补了Log4j2漏洞");
				}
			}
			mzf.close();
		} catch (Throwable e) {
			CmdUtil.warning("无法修补Log4j2漏洞: ", e);
		}
	}

	private static void downloadLibrary(CMapping map, String mirror, File libFile, DownMcFile task, Runnable cb, String classifiers, String libFileName) {
		File libParent = libFile.getAbsoluteFile().getParentFile();
		if (!libParent.isDirectory() && !libParent.mkdirs()) throw new IllegalStateException("无法创建library保存文件夹  " + libParent.getAbsolutePath());

		CMapping artifact;
		if (classifiers != null) {
			artifact = map.get("classifiers").asMap().get(classifiers).asMap();
		} else if (!map.containsKey("artifact")) { // mc
			artifact = new CMapping();
			if (!map.containsKey("url")) {
				if (DEBUG) CmdUtil.warning(libFile + " 没有下载地址");
				// artifact.put("url", MAIN_CONFIG.get("通用").asMap().getString("协议") + "libraries.minecraft.net/" + libFileName);
			} else {
				artifact.put("url", map.getString("url") + libFileName);
			}
		} else {
			artifact = map.get("artifact").asMap();
		}
		if (artifact.getString("url").isEmpty()) {
			final CMapping map1 = CONFIG.get("通用").asMap();
			artifact.put("url", map1.getString("协议") + (map1.getString("libraries地址").isEmpty() ? map1.getString("forge地址") : map1.getString("libraries地址")) + '/' + libFileName);
		}

		if (!libFile.exists()) {
			String url = replaceMirror(artifact, mirror.isEmpty() ? null : mirror);
			String sha1 = artifact.getString("sha1");
			task.add(url, libFile, sha1, cb);
		}
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
						fos.write(IOUtil.read(zipFile.getInputStream(zipEntry)));
					}
				} else if (DEBUG) {
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

	public static void info(String s) {
		JOptionPane.showMessageDialog(activeWindow, s, "提示", JOptionPane.INFORMATION_MESSAGE);
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
			CmdUtil.info("开始下载 " + url);
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

	public static void downloadAndVerifyMD5(String url, String md5, File target) throws IOException {
		if (!target.exists()) {
			CmdUtil.info("开始下载 " + url);
			DownMcFile man = new DownMcFile("md5");
			man.add(url, target, md5, null);
			man.invoke();
		}
	}

	public static void downloadAndVerifyMD5(String url, File target) throws IOException {
		if (!target.exists()) {
			ByteList buf = IOUtil.downloadFileToMemory(url+".md5");
			downloadAndVerifyMD5(url, buf.readUTF(buf.readableBytes()), target);
		}
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

		private final UTFCoder uc;
		private MessageDigest DIG;
		private final ProgressGroupedMulti hdr = new ProgressGroupedMulti();

		private long except;
		private boolean wait;

		static final int maxTask = CONFIG.getInteger("通用.最大同时下载数");

		public DownMcFile() {
			this("sha1");
		}

		public DownMcFile(String alg) {
			uc = new UTFCoder();
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
		public void execute() {
			if (wait && System.currentTimeMillis() > except) {
				int r = ifBreakWait();
				if (r == -1) {
					cancel(true);
					return;
				}
				if (r == 1) {wait = false;} else refresh();
				except = System.currentTimeMillis() + TIMEOUT_TIME;
			}

			int count = 0;
			try {
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
							CmdUtil.warning(e.url.substring(e.url.lastIndexOf('/') + 1) + " 失败三次, 取消");
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
				Thread.sleep(1);
				if (!entries.isEmpty()) return;
			} catch (Throwable e) {
				exception = new ExecutionException(e);
			}
			synchronized (this) {
				out = null;
				notifyAll();
			}
		}

		@Override
		public Void invoke() {
			while (!entries.isEmpty()) {
				Entry e = entries.remove(entries.size() - 1);
				while (true) {
					if (e.retry > 3) {
						CmdUtil.warning(e.url.substring(e.url.lastIndexOf('/') + 1) + " 失败三次, 取消");
						break;
					}

					try {
						e.future = DownloadTask.downloadMTD(e.url, e.target, new ProgressMulti());
						e.future.waitFor();
						if (!verifyFile(e)) {
							entries.add(0, e);
						}
						break;
					} catch (Throwable ex) {
						handleError(e, ex);
					}
				}
			}
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
							CmdUtil.warning("二次SHA1相同,镜像问题? " + e.url);
						} else {
							e.lastDigest = DIGEST;
							e.future = null;
							e.retry++;

							if (DEBUG) CmdUtil.warning("DIG For " + e.url + " : " + DIGEST);
							if (!e.target.delete()) {
								CmdUtil.warning(e.target.getName() + "删除失败!");
							}
							return false;
						}
					} else {
						CmdUtil.success(e.url.substring(e.url.lastIndexOf('/') + 1) + " 完毕. (" + entries.size() + "剩余)");
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
				CmdUtil.warning("下载被取消");
				e.retry = 99;
				return;
			}

			CmdUtil.error(e.url.substring(e.url.lastIndexOf('/') + 1) + " 下载失败.");
			ex.printStackTrace();

			e.retry++;
		}

		@Override
		public boolean cancel(boolean force) {
			canceled = true;
			refresh();
			entries.clear();
			return true;
		}

		@Override
		public boolean continueExecuting() {
			return !entries.isEmpty();
		}
	}

	// endregion
	// region Process management

	@SuppressWarnings("unchecked")
	public static int runProcess(List<String> tokens, File dir, int flag, Object obj) throws IOException {
		if (DEBUG) System.out.println("参数: '" + tokens + "'");
		ProcessBuilder pb = new ProcessBuilder(tokens).directory(dir);
		if ((flag & 1) != 0) {pb.redirectOutput(ProcessBuilder.Redirect.INHERIT).redirectError(ProcessBuilder.Redirect.INHERIT);} else {
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
				} catch (InterruptedException ignored) {x = true;}
			}
			if (x) Thread.currentThread().interrupt();

			CmdUtil.info("进程终止");
			return process.exitValue();
		}
		return 0;
	}

	// endregion
}
