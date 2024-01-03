package roj.mod;

import roj.archive.zip.ZEntry;
import roj.archive.zip.ZipArchive;
import roj.archive.zip.ZipFileWriter;
import roj.collect.MyHashMap;
import roj.collect.MyHashSet;
import roj.collect.SimpleList;
import roj.config.JSONParser;
import roj.config.ParseException;
import roj.config.data.CMapping;
import roj.io.IOUtil;
import roj.math.Version;
import roj.mod.fp.Fabric;
import roj.mod.fp.LegacyForge;
import roj.mod.fp.WorkspaceBuilder;
import roj.mod.mapping.MappingFormat;
import roj.mod.mapping.VersionRange;
import roj.ui.CLIUtil;
import roj.ui.GUIUtil;
import roj.ui.ProgressBar;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

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
	public static final File MCP2SRG_PATH = new File(BASE, "util/mcp-srg.srg");

	public static void main(String[] args) throws IOException {
		GUIUtil.systemLook();
		CONFIG.size();
		Shared._lock();

		File mcRoot = getMcRoot();
		List<File> versions = MCLauncher.findVersions(new File(mcRoot, "/versions/"));

		if (versions.isEmpty()) {
			CLIUtil.error("没有找到任何版本！FMD需要在已安装的模组客户端上进行！", true);
			return;
		}

		File mcJson = versions.get(CLIUtil.selectOneFile(versions, "模组客户端"));

		System.exit(setupWorkspace(mcRoot, mcJson));
	}

	private static File getMcRoot() {
		String path = CONFIG.getString("通用.MC目录");
		while (true) {
			File mcRoot = new File(path, "versions");
			String[] s = mcRoot.list();
			if (s == null || s.length <= 0) return mcRoot;

			File file = GUIUtil.fileLoadFrom("选择【.minecraft】文件夹", null, JFileChooser.DIRECTORIES_ONLY);

			if (file == null) {
				error("用户取消操作.");
				return null;
			}

			path = file.getAbsolutePath();
			CONFIG.put("通用.MC目录", path);
		}
	}

	@SuppressWarnings("unchecked")
	static int setupWorkspace(File mcRoot, File mcJson) throws IOException {
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
			CLIUtil.error("不支持快照版本");
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

		CMapping mfJson = new FMDInstall().getSelectedMappingFormat(new Version(mcVersion), wb.getId());
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

		if (fmt.hasMCP()) MCPVersionDetect.doDetect(cfg);

		// region clean previous mapping
		Files.deleteIfExists(MCP2SRG_PATH.toPath());
		Files.deleteIfExists(new File(BASE, "/util/mapCache.lzma").toPath());
		Files.deleteIfExists(ATHelper.AT_BACKUP_LIB.toPath());
		// endregion
		if (System.getProperty("fmd.maponly") != null) {
			CLIUtil.info("设置了fmd.maponly 仅生成映射表！");
			MappingFormat.MapResult maps = fmt.map(cfg, TMP_DIR);
			maps.tsrgCompile.saveMap(MCP2SRG_PATH);
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

		CLIUtil.info("Persistent AT...");
		FMDMain.preAT();
		return 0;
	}

	private CMapping getSelectedMappingFormat(Version version, String id) throws IOException {
		SimpleList<CMapping> list = new SimpleList<>();

		File[] files = new File(BASE, "util/mapping").listFiles();
		for (File file : files) {
			if (file.getName().equals("@common.json")) continue;
			try {
				CMapping map = JSONParser.parses(IOUtil.readUTF(file)).asMap();
				VersionRange range = VersionRange.parse(map.getString("version"));
				if (range.suitable(version) && map.getString("type").equals(id)) list.add(map);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		CLIUtil.info("有多个 Mapping , 请选择(输入编号)");

		for (int i = 0; i < list.size(); i++) {
			String name = list.get(i).getString("title");
			CLIUtil.fg(CLIUtil.WHITE, (i & 1) == 1);
			System.out.println(i + ". " + name);
			CLIUtil.reset();
		}

		return list.get(CLIUtil.getNumberInRange(0, list.size()));
	}

	private static WorkspaceBuilder createBuilder(Collection<String> lib, boolean highVersion) {
		for (String s : lib) {
			if (s.contains("minecraftforge")) {
				return highVersion ? null : new LegacyForge();
			} else if (s.contains("fabricmc")) {
				return new Fabric();
			}
		}
		CLIUtil.error("无法为当前版本找到合适的WorkspaceBuilder (您是否安装了模组加载器如forge?)");
		return null;
	}

	private static void copyLibrary(File baseDir, Collection<String> lib, WorkspaceBuilder proc) throws IOException {
		ProgressBar bar = new ProgressBar("复制库文件");
		int finished = 0;
		int total = lib.size();

		File merged = new File(BASE, "class/"+DONT_LOAD_PREFIX+"libraries.jar");
		MyHashMap<String, String> dupChecker = new MyHashMap<>();
		try (ZipFileWriter zfw = new ZipFileWriter(merged, false)) {
			for (Iterator<String> itr = lib.iterator(); itr.hasNext(); bar.update((double) ++finished / total, 1)) {
				String pkg = itr.next();

				File file = new File(baseDir, pkg);
				if (!file.isFile()) {
					CLIUtil.error("library不存在: " + pkg);
					continue;
				}
				if (proc.mergeLibraryHook(file, pkg)) continue;

				final boolean _USE_PATCHY = false;
				if (pkg.startsWith("com/mojang/patchy") && !_USE_PATCHY) {
					if (DEBUG) CLIUtil.info("跳过Patchy " + pkg);
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
									CLIUtil.warning("重复的 "+entry.getName()+" 在 "+prevPkg+" 和 "+pkg, true);
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
			bar.end("失败", CLIUtil.RED);
			throw e;
		}
	}
}