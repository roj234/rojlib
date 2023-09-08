package roj.mod;

import roj.archive.zip.ZEntry;
import roj.archive.zip.ZipArchive;
import roj.archive.zip.ZipFileWriter;
import roj.collect.MyHashMap;
import roj.collect.MyHashSet;
import roj.collect.SimpleList;
import roj.config.JSONParser;
import roj.config.ParseException;
import roj.config.data.CEntry;
import roj.config.data.CList;
import roj.config.data.CMapping;
import roj.io.IOUtil;
import roj.math.Version;
import roj.mod.fp.Fabric;
import roj.mod.fp.Forge;
import roj.mod.fp.LegacyForge;
import roj.mod.fp.WorkspaceBuilder;
import roj.mod.mapping.MappingFormat;
import roj.text.TextUtil;
import roj.ui.CmdUtil;
import roj.ui.EasyProgressBar;
import roj.ui.UIUtil;
import roj.util.ByteList;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.*;

import static roj.mapper.Mapper.DONT_LOAD_PREFIX;
import static roj.mod.MCLauncher.*;
import static roj.mod.Shared.*;

/**
 * FMD Install window
 *
 * @author Roj234
 * @since 2021/5/31 21:17
 */
public class FMDInstall {
	private static int changeVersion() throws IOException {
		Shared._lock();

		CMapping cfgGen = CONFIG.get("通用").asMap();

		File mcRoot = new File(cfgGen.getString("MC目录"));
		if (!mcRoot.isDirectory()) mcRoot = UIUtil.readFile("MC目录(.minecraft)");
		if (!new File(mcRoot, "/versions/").isDirectory()) mcRoot = new File(mcRoot, ".minecraft");

		List<File> versions = MCLauncher.findVersions(new File(mcRoot, "/versions/"));

		File mcJson = null;
		if (versions.isEmpty()) {
			CmdUtil.error("没有找到任何MC版本！请确认目录是否正确", true);
			return -1;
		} else {
			String versionJson = CONFIG.getString("MC版本JSON");
			if (!versionJson.isEmpty()) {
				for (int i = 0; i < versions.size(); i++) {
					File file = versions.get(i);
					if (file.getName().equals(versionJson)) {
						mcJson = file;
					}
				}
			}
			if (mcJson == null) mcJson = versions.get(UIUtil.selectOneFile(versions, "MC版本"));
		}

		return setupWorkspace(mcRoot, mcJson, new InputDelegate());
	}

	@SuppressWarnings("unchecked")
	static int setupWorkspace(File mcRoot, File mcJson, InputDelegate gui) throws IOException {
		watcher.terminate();

		CMapping cfgGen = CONFIG.get("通用").asMap();

		// region retain game info
		MyHashSet<String> skipped = new MyHashSet<>();
		FMDMain.readTextList(skipped::add, "忽略的libraries");
		File nativePath = new File(mcJson.getParentFile(), "$natives");

		Object[] result = getRunConf(mcRoot, mcJson, nativePath, skipped, true, cfgGen);
		if (result == null) return -1;

		File jar = (File) result[1];
		Collection<String> libraries = ((Map<String, String>) result[2]).values();
		CMapping json = (CMapping) result[3];
		boolean is113orHigher = (boolean) result[4];

		if (!json.getString("type").equals("release")) {
			CmdUtil.error("不支持快照版本");
			return -1;
		}

		MCLauncher.load();
		config.put("mc_conf", (CMapping) result[0]);
		config.put("mc_version", json.getString("id"));
		MCLauncher.save();

		String mcVersion = json.get("clientVersion").asString();
		if (mcVersion.isEmpty()) mcVersion = new Version(jar.getName()).toString();
		// endregion

		WorkspaceBuilder wb = createBuilder(libraries, is113orHigher);

		CMapping downloads = json.get("downloads").asMap();

		boolean canDownloadOfficial = downloads.containsKey("client_mappings");
		gui.init(new Version(mcVersion), json, wb.getId());

		CMapping mfJson = gui.getSelectedMappingFormat();
		MappingFormat fmt;
		try {
			CMapping global = new JSONParser().parseRaw(new File(BASE, "util/mapping/@common.json")).asMap();
			mfJson.merge(global, true, true);
			fmt = new MappingFormat(mfJson);
		} catch (ParseException | IOException e) {
			e.printStackTrace();
			return -1;
		}

		Map<String, Object> cfg = new MyHashMap<>();

		cfg.put("tmp", new SimpleList<>());
		cfg.put("mc_json", json);
		cfg.put("version", mcVersion);

		File override = new File(BASE, "util/override.cfg");
		if (override.isFile()) cfg.put("override", IOUtil.readUTF(override));

		String mirror = cfgGen.getBool("下载MC相关文件使用镜像") ? cfgGen.getString("镜像地址") : null;
		File mcServer = downloadMinecraftFile(downloads, "server", mirror);

		File libraryRoot = new File(mcRoot, "/libraries/");

		if (fmt.hasMCP()) MCPVersionDetect.doDetect(cfg, gui);

		// region clean previous mapping
		File mcpSrgPath = MCP2SRG_PATH;
		if (mcpSrgPath.isFile() && !mcpSrgPath.delete()) CmdUtil.warning("无法删除旧的映射数据");
		File cache0 = new File(BASE, "/util/mapCache.lzma");
		if (cache0.isFile() && !cache0.delete()) CmdUtil.error("无法删除映射缓存 mapCache.lzma!", true);
		ATHelper.getBackupFile().empty();
		// endregion
		if (System.getProperty("fmd.maponly") != null) {
			CmdUtil.info("设置了fmd.maponly 仅生成映射表！");
			MappingFormat.MapResult maps = fmt.map(cfg, TMP_DIR);
			maps.tsrgCompile.saveMap(mcpSrgPath);
			maps.tsrgDeobf.saveMap(new File(BASE, "deobf.map"));
		}

		wb.jsonPath = mcJson;
		wb.mf = fmt;
		wb.mf_cfg = cfg;
		wb.file.putInt(0, jar);
		wb.file.putInt(1, mcServer);
		wb.loadLibraries(libraryRoot, libraries);
		Task.pushTask(wb);

		copyLibrary(libraryRoot, libraries, wb);

		if (!wb.awaitSuccess()) return -1;

		CmdUtil.info("Persistent AT...");
		FMDMain.preAT();
		return 0;
	}

	private static WorkspaceBuilder createBuilder(Collection<String> lib, boolean highVersion) {
		for (String s : lib) {
			if (s.contains("minecraftforge")) {
				return highVersion ? new Forge() : new LegacyForge();
			} else if (s.contains("fabricmc")) {
				return new Fabric();
			}
		}
		CmdUtil.error("无法为当前版本找到合适的WorkspaceBuilder (您是否安装了模组加载器如forge?)");
		return null;
	}

	private static void copyLibrary(File baseDir, Collection<String> lib, WorkspaceBuilder proc) throws IOException {
		EasyProgressBar bar = new EasyProgressBar("复制库文件");
		int finished = 0;
		int total = lib.size();

		File merged = new File(BASE, "class/"+DONT_LOAD_PREFIX+"libraries.jar");
		MyHashMap<String, String> dupChecker = new MyHashMap<>();
		try (ZipFileWriter zfw = new ZipFileWriter(merged, false)) {
			for (Iterator<String> itr = lib.iterator(); itr.hasNext(); bar.update((double) ++finished / total, 1)) {
				String pkg = itr.next();

				File file = new File(baseDir, pkg);
				if (!file.isFile()) {
					CmdUtil.error("library不存在: " + pkg);
					continue;
				}
				if (proc.mergeLibraryHook(file, pkg)) continue;

				final boolean _USE_PATCHY = false;
				if (pkg.startsWith("com/mojang/patchy") && !_USE_PATCHY) {
					if (DEBUG) CmdUtil.info("跳过Patchy " + pkg);
					continue;
				}

				try (ZipArchive mzf = new ZipArchive(file, ZipArchive.FLAG_BACKWARD_READ)) {
					for (ZEntry entry : mzf.getEntries().values()) {
						if (entry.getName().endsWith(".class")) {
							String prevPkg = dupChecker.get(entry.getName());
							if (prevPkg != null) {
								if (!pkg.equals(prevPkg)) {
									prevPkg = prevPkg.substring(prevPkg.lastIndexOf('/') + 1);
									pkg = pkg.substring(pkg.lastIndexOf('/') + 1);
									CmdUtil.warning("重复的 " + entry.getName() + " 在 " + prevPkg + " 和 " + pkg, true);
								}
							} else {
								zfw.copy(mzf, entry);

								dupChecker.put(entry.getName(), pkg);
							}
						}
					}
				}
			}
			bar.end("成功");
		} catch (Exception e) {
			bar.end("失败", CmdUtil.Color.RED);
			throw e;
		} finally {
			bar.dispose();
		}
	}

	private static void quickInst(String code) {
		if (code == null) code = JOptionPane.showInputDialog(activeWindow, "请输入一键安装代码\n示例: '1.16.5|36.0.42||1'", "询问", JOptionPane.QUESTION_MESSAGE);
		if (code == null) return;

		List<String> list = TextUtil.split(new ArrayList<>(4), code, '|', 8, true);
		if (list.size() < 4 || list.size() > 5) {
			error("安装代码无效");
			return;
		}

		MCLauncher.load();

		File mcRoot = getMcRoot();
		if (mcRoot == null) return;

		CList versions = MCLauncher.getMcVersionList(CONFIG.get("启动器配置").asMap());
		if (versions == null) return;

		CMapping target = null;
		for (CEntry entry : versions) {
			CMapping des = entry.asMap();
			if (des.getString("id").equals(list.get(0))) {
				target = des;
				break;
			}
		}

		if (target == null) {
			error("MC版本不存在");
			return;
		}

		File mcJar = new File(mcRoot, "/versions/" + target.getString("id") + '/' + target.getString("id") + ".jar");
		if (!mcJar.isFile()) MCLauncher.onClickInstall(target, false);

		File mcJson = new File(mcRoot, "/versions/" + target.getString("id") + '/' + target.getString("id") + ".json");
		if (!MCLauncher.installMinecraftClient(mcRoot, mcJson, false)) {
			error("下载native失败");
			return;
		}

		String mcVer = MCLauncher.config.getString("mc_version");

		CMapping cfgLan = CONFIG.get("启动器配置").asMap();
		try {
			ByteList bl = IOUtil.downloadFileToMemory(cfgLan.getString("forge版本manifest地址").replace("<mc_ver>", mcVer));
			versions = new JSONParser().parseRaw(bl).asList();
		} catch (ParseException | IOException e) {
			error("获取数据出了点错...\n请查看控制台");
			e.printStackTrace();
			return;
		}

		target = null;
		for (CEntry entry : versions) {
			CMapping des = entry.asMap();
			if (des.getString("version").equals(list.get(1))) {
				target = des;
				break;
			}
		}

		if (target == null) {
			error("Forge版本不存在");
			return;
		}

		mcJson = new File(mcRoot, "/versions/" + mcVer + "-forge-" + target.getString("version") + '/' + mcVer + "-forge-" + target.getString("version") + ".json");

		if (!mcJson.isFile()) MCLauncher.onClickInstall(target, true);

		if (!MCLauncher.installMinecraftClient(mcRoot, mcJson, false)) {
			error("下载forge的native失败");
			return;
		}

		doInstall(mcRoot, mcJson, list.subList(2, list.size()));
	}

	private static File getMcRoot() {
		CMapping cfgGen = CONFIG.get("通用").asMap();
		File mcRoot = new File(cfgGen.getString("MC目录"));
		if (!mcRoot.isDirectory()) {
			JFileChooser fileChooser = new JFileChooser(BASE);
			fileChooser.setDialogTitle("选择MC安装位置");
			fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

			int status = fileChooser.showOpenDialog(activeWindow);
			//没有选打开按钮结果提示
			if (status == JFileChooser.APPROVE_OPTION) {
				CONFIG.get("通用").asMap().put("MC目录", fileChooser.getSelectedFile().getAbsolutePath());
				return fileChooser.getSelectedFile();
			} else {
				error("用户取消操作.");
				return null;
			}
		}
		return mcRoot;
	}

	// todo notify install client first
	private static void doInstall(File mcRoot, File mcJson, List<String> prefilledAnswers) {
		try {
			if (setupWorkspace(mcRoot, mcJson, new Automatic(prefilledAnswers)) != 0) {
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

	public static void main(String[] args) throws IOException {
		UIUtil.systemLook();
		CONFIG.size();
		changeVersion();
		//quickInst(args.length == 0 ? null : args[0]);
	}

	// 1.12.2|forge|13.8.23.2783|mymapping|32
	static final class Automatic extends InputDelegate {
		final List<String> ans;

		public Automatic(List<String> answer) {
			ans = answer;
		}

		@Override
		String getMCPVersion() throws IOException {
			return super.getMCPVersion();
		}

		@Override
		String getMinecraftVersionForMCP() throws IOException {
			return super.getMinecraftVersionForMCP();
		}

		@Override
		CMapping getSelectedMappingFormat() throws IOException {
			return super.getSelectedMappingFormat();
		}
	}

	public static final File MCP2SRG_PATH = new File(BASE, "util/mcp-srg.srg");
}
