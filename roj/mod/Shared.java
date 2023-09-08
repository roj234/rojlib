package roj.mod;

import roj.collect.MyHashMap;
import roj.collect.SimpleList;
import roj.concurrent.FastLocalThread;
import roj.concurrent.TaskPool;
import roj.concurrent.timing.Scheduler;
import roj.config.JSONParser;
import roj.config.ParseException;
import roj.config.data.CMapping;
import roj.dev.HRRemote;
import roj.io.ChineseInputStream;
import roj.io.IOUtil;
import roj.io.down.DownloadTask;
import roj.mapper.Mapper;
import roj.mapper.util.Desc;
import roj.misc.CpFilter;
import roj.mod.plugin.Plugin;
import roj.ui.CmdUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * FMD Shared Data / Utility Methods
 *
 * @author Roj234
 * @since 2020/8/29 11:38
 */
public final class Shared {
	public static final boolean DEBUG;
	public static final String MC_BINARY = "forgeMcBin";
	public static final String VERSION = "2.1.0";

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
				CmdUtil.warning("重载工具无法绑定端口", e);
			}
		}
	}

	public static final Scheduler PeriodicTask = Scheduler.getDefaultScheduler();
	public static final TaskPool Task = TaskPool.CpuMassive();

	public static void async(Runnable... run) {
		Thread[] t = new Thread[run.length];
		for (int i = 0; i < run.length; i++) {
			Thread o = t[i] = new FastLocalThread(run[i]);
			o.setDaemon(true);
			o.start();
		}
		for (Thread thread : t) {
			try {
				thread.join();
			} catch (InterruptedException ignored) {}
		}
	}

	public static CMapping CONFIG;

	static void loadConfig() {
		File file = new File(BASE, "config.json");
		try {
			ChineseInputStream bom = new ChineseInputStream(new FileInputStream(file));
			if (!bom.getCharset().equals("UTF8")) { // 检测到了则是 UTF-8
				CmdUtil.warning("文件的编码中含有BOM(推荐使用UTF-8无BOM格式!), 识别的编码: " + bom.getCharset());
			}

			CONFIG = JSONParser.parses(IOUtil.readAs(bom, bom.getCharset()), 7).asMap();
			CONFIG.dot(true);
		} catch (ParseException | ClassCastException e) {
			CmdUtil.error(file.getAbsolutePath() + " 有语法错误! 请修正!", e);
		} catch (IOException e) {
			CmdUtil.error(file.getAbsolutePath() + " 读取失败!", e);
		}
	}

	static Project project;
	public static void setProject(String name) {
		if (name == null) name = project.name;

		try (FileOutputStream fos = new FileOutputStream(new File(BASE, "config/index"))) {
			fos.write(name.getBytes(StandardCharsets.UTF_8));
		} catch (IOException e) {
			CmdUtil.error("配置保存", e);
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
					CmdUtil.warning("无法读取 " + index);
				}
			}

			if (proj != null) {
				try {
					project = Project.load(cf);
				} catch (Throwable e) {
					CmdUtil.warning("配置读取失败", e);
					proj = null;
				}
			}

			if (proj == null) {
				try {
					FMDMain._project(new String[] {"p"}, null);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

	public static final Mapper mapperFwd = new Mapper();
	private static Mapper mapperRev;

	public static void loadMapper() {
		if (mapperFwd.getClassMap().isEmpty()) {
			synchronized (mapperFwd) {
				if (mapperFwd.getClassMap().isEmpty()) {
					File map = new File(BASE, "/util/mcp-srg.srg");
					if (!map.isFile()) {
						CmdUtil.error("正向映射表 " + map + " 不存在,建议重新安装");
						return;
					}
					mapperRev = null;
					try {
						mapperFwd.initEnv(map, new File(BASE, "/class/"), new File(BASE, "/util/mapCache.lzma"), false);
						if (DEBUG) CmdUtil.success("正向映射表已加载");
					} catch (Exception e) {
						CmdUtil.error("正向映射表加载失败", e);
					}
					mapperFwd.classNameChanged();
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
				throw new IllegalStateException("无法获取单例锁");
			}
		} else {
			try {
				ProcessLock = new ServerSocket(CONFIG.getInteger("单例锁端口"));
			} catch (Throwable e) {
				throw new IllegalStateException("无法获取单例锁", e);
			}
			ThreadLock = new AtomicInteger();
		}
	}
	public static void _unlock() {
		if (ThreadLock != null) {
			ThreadLock.set(0);
		}
	}

	static List<Plugin> plugins;

	static {
		String basePath = System.getProperty("fmd.base_path", "");
		BASE = new File(basePath).getAbsoluteFile();

		loadConfig();
		if (CONFIG == null) System.exit(-2);

		boolean launchOnly = System.getProperty("fmd.launch_only") != null || CONFIG.getBool("启动器模式");

		TMP_DIR = new File(BASE, "tmp");

		if (!TMP_DIR.isDirectory() && !TMP_DIR.mkdir()) {
			CmdUtil.error("无法创建临时文件夹: " + TMP_DIR.getAbsolutePath());
			System.exit(-2);
		}

		File classDir = new File(BASE, "class");
		if (!launchOnly && !classDir.isDirectory() && !classDir.mkdir()) {
			CmdUtil.error("无法创建库文件夹: " + classDir.getAbsolutePath());
			System.exit(-2);
		}

		CONFIG_DIR = new File(BASE, "config");
		if (!launchOnly && !CONFIG_DIR.isDirectory() && !CONFIG_DIR.mkdirs()) {
			CmdUtil.error("无法创建配置文件夹: " + CONFIG_DIR.getAbsolutePath());
			System.exit(-2);
		}

		DEBUG = CONFIG.getBool("调试模式");
		if (DEBUG) {
			try {
				CpFilter.registerShutdownHook();
			} catch (NoClassDefFoundError ignored) {}
		}

		CMapping cfgGen = CONFIG.get("通用").asMap();
		int threads = cfgGen.getInteger("最大线程数");
		DownloadTask.defChunkStart = threads > 0 ? 4096 : Integer.MAX_VALUE;
		DownloadTask.defMaxChunks = threads;
		DownloadTask.userAgent = cfgGen.getString("UserAgent");
		IOUtil.timeout = cfgGen.getInteger("下载超时");

		IFileWatcher w = null;
		if (!launchOnly) {
			if (CONFIG.getBool("文件修改监控")) {
				try {
					w = new FileWatcher();
				} catch (IOException e) {
					CmdUtil.warning("无法启动文件监控", e);
				}

				AutoCompile.Debounce = CONFIG.getInteger("自动编译防抖");
				if (CONFIG.getBool("自动编译")) {
					AutoCompile.setEnabled(true);
				}
			}

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
				mapperFwd.flag |= Mapper.FLAG_FIX_SUBIMPL;
			}
		}

		if (w == null) w = new IFileWatcher();
		watcher = w;
	}
}
