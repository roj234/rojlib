package roj.mod;

import roj.asm.type.Desc;
import roj.asmx.mapper.Mapper;
import roj.collect.MyHashMap;
import roj.collect.SimpleList;
import roj.concurrent.TaskPool;
import roj.concurrent.timing.Scheduler;
import roj.config.JSONParser;
import roj.config.ParseException;
import roj.config.data.CMapping;
import roj.dev.HRRemote;
import roj.io.FastFailException;
import roj.io.IOUtil;
import roj.io.down.DownloadTask;
import roj.mod.plugin.Plugin;
import roj.ui.CLIUtil;
import roj.util.HighResolutionTimer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static roj.config.JSONParser.*;

/**
 * FMD Shared Data / Utility Methods
 *
 * @author Roj234
 * @since 2020/8/29 11:38
 */
public final class Shared {
	public static final boolean DEBUG;
	public static final String MC_BINARY = "forgeMcBin";
	public static final String VERSION = "2.3.0-alpha";

	public static final File BASE, TMP_DIR, CONFIG_DIR;

	static IFileWatcher watcher;
	static HRRemote hotReload;

	public static void launchHotReload() {
		if (hotReload == null && CONFIG.getBool("启用热重载")) {
			int port = 0xFFFF & CONFIG.getInteger("重载端口");
			if (port == 0) port = 4485;
			try {
				hotReload = new HRRemote(port);
			} catch (IOException e) {
				CLIUtil.warning("重载工具无法绑定端口", e);
			}
		}
	}

	public static final Scheduler PeriodicTask = Scheduler.getDefaultScheduler();
	public static final TaskPool Task = TaskPool.Common();
	public static CMapping CONFIG;

	static void loadConfig() {
		File file = new File(BASE, "config.json");
		try {
			CONFIG = new JSONParser().parseRaw(file, NO_DUPLICATE_KEY|LITERAL_KEY|UNESCAPED_SINGLE_QUOTE|LENIENT_COMMA).asMap();
			CONFIG.dot(true);
		} catch (ParseException | ClassCastException e) {
			CLIUtil.error("config.json 有语法错误! 请修正!", e);
		} catch (IOException e) {
			CLIUtil.error("config.json 读取失败!", e);
		}
	}

	static Project project;
	public static void setProject(String name) {
		if (name == null) name = project.name;

		try (FileOutputStream fos = new FileOutputStream(new File(BASE, "config/index"))) {
			fos.write(name.getBytes(StandardCharsets.UTF_8));
		} catch (IOException e) {
			CLIUtil.error("配置保存", e);
		}

		if (project != (project = Project.load(name)))
			AutoCompile.notifyIt();
	}
	public static void loadProject() {
		if (project == null) {
			String cf = null;
			File proj = null;

			File index = new File(BASE, "config/index");
			if (index.isFile()) {
				try {
					cf = IOUtil.readUTF(index);
					proj = new File(BASE, "/config/"+cf+".json");
				} catch (IOException e) {
					CLIUtil.warning("无法读取 " + index);
				}
			}

			if (proj != null) {
				try {
					project = Project.load(cf);
				} catch (Throwable e) {
					CLIUtil.warning("配置读取失败", e);
				}
			}
		}
	}

	public static final Mapper mapperFwd = new Mapper();
	public static boolean mappingIsClean;
	private static Mapper mapperRev;

	public static void loadMapper() {
		if (!mappingIsClean) {
			synchronized (mapperFwd) {
				if (mapperFwd.getClassMap().isEmpty()) {
					File map = new File(BASE, "/util/mcp-srg.srg");
					if (!map.isFile()) {
						CLIUtil.error("混淆映射表"+map+"不存在,建议重新安装");
						return;
					}
					mapperRev = null;
					try {
						mapperFwd.initEnv(map, new File(BASE, "/class/"), new File(BASE, "/util/mapCache.lzma"), false);
					} catch (Exception e) {
						CLIUtil.error("混淆映射表加载失败", e);
					}
					mappingIsClean = true;
				}
			}
		}
	}
	public static Mapper loadReverseMapper() {
		if (mapperRev == null) {
			loadMapper();
			mapperRev = new Mapper(mapperFwd);
			mapperRev.reverseSelf();
		}
		return mapperRev;
	}

	public static final Map<String, String> srg2mcp = new MyHashMap<>(1000, 1.5f);
	public static void loadSrg2Mcp() {
		Map<String, String> srg2mcp = Shared.srg2mcp;
		if (srg2mcp.isEmpty()) {
			loadMapper();

			Mapper fwd = mapperFwd;
			for (Map.Entry<Desc, String> entry : fwd.getFieldMap().entrySet()) {
				srg2mcp.put(entry.getValue(), entry.getKey().name);
			}

			for (Map.Entry<Desc, String> entry : fwd.getMethodMap().entrySet()) {
				srg2mcp.put(entry.getValue(), entry.getKey().name);
			}
		}
	}

	private static ServerSocket ProcessLock;
	private static AtomicInteger ThreadLock;
	public static void _lock() {
		if (ProcessLock != null) {
			if (!ThreadLock.compareAndSet(0, 1)) {
				throw new FastFailException("无法获取线程锁");
			}
		} else {
			int port = CONFIG.getInteger("单例锁端口");
			if (port == 0) return;
			try {
				ProcessLock = new ServerSocket(port);
			} catch (Throwable e) {
				throw new FastFailException("无法获取单例锁: "+e.getMessage());
			}
			ThreadLock = new AtomicInteger();
		}
	}
	public static void _unlock() {
		if (ThreadLock != null) ThreadLock.set(0);
	}

	static List<Plugin> plugins;
	static {
		HighResolutionTimer.activate();

		String basePath = System.getProperty("fmd.base_path", "");
		BASE = new File(basePath).getAbsoluteFile();

		loadConfig();
		if (CONFIG == null) System.exit(-2);

		TMP_DIR = new File(BASE, "tmp");

		if (!TMP_DIR.isDirectory() && !TMP_DIR.mkdir()) {
			CLIUtil.error("无法创建临时文件夹: " + TMP_DIR.getAbsolutePath());
			System.exit(-2);
		}

		File classDir = new File(BASE, "class");
		if (!classDir.isDirectory() && !classDir.mkdir()) {
			CLIUtil.error("无法创建库文件夹: " + classDir.getAbsolutePath());
			System.exit(-2);
		}

		CONFIG_DIR = new File(BASE, "config");
		if (!CONFIG_DIR.isDirectory() && !CONFIG_DIR.mkdirs()) {
			CLIUtil.error("无法创建配置文件夹: " + CONFIG_DIR.getAbsolutePath());
			System.exit(-2);
		}

		DEBUG = CONFIG.getBool("调试模式");

		CMapping cfgGen = CONFIG.get("通用").asMap();
		int threads = cfgGen.getInteger("最大线程数");
		DownloadTask.defChunkStart = threads > 0 ? 4096 : Integer.MAX_VALUE;
		DownloadTask.defMaxChunks = threads;
		DownloadTask.userAgent = cfgGen.getString("UserAgent");
		IOUtil.timeout = cfgGen.getInteger("下载超时");

		IFileWatcher w = null;
		if (CONFIG.getBool("文件修改监控")) {
			try {
				w = new FileWatcher();
			} catch (IOException e) {
				CLIUtil.warning("无法启动文件监控", e);
			}

			AutoCompile.Debounce = CONFIG.getInteger("自动编译防抖");
			if (CONFIG.getBool("自动编译")) {
				AutoCompile.setEnabled(true);
			}
		}
		if (w == null) w = new IFileWatcher();
		watcher = w;

		plugins = new SimpleList<>();
		for (String s : CONFIG.getOrCreateList("活动的插件").asStringList()) {
			try {
				Plugin o = (Plugin) Class.forName(s.replace('/', '.')).newInstance();
				plugins.add(o);
			} catch (Throwable e) {
				e.printStackTrace();
			}
		}

		if (CONFIG.getBool("子类实现")) {
			mapperFwd.flag |= Mapper.MF_FIX_SUBIMPL;
		}
	}
}