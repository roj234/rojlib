package roj.plugins.ci;

import roj.asmx.mapper.Mapper;
import roj.collect.SimpleList;
import roj.concurrent.TaskPool;
import roj.concurrent.timing.Scheduler;
import roj.config.JSONParser;
import roj.config.ParseException;
import roj.config.data.CMap;
import roj.io.FastFailException;
import roj.io.IOUtil;
import roj.plugins.ci.plugin.Plugin;
import roj.ui.Terminal;
import roj.util.HighResolutionTimer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static roj.config.JSONParser.NO_DUPLICATE_KEY;

/**
 * FMD Shared Data / Utility Methods
 *
 * @author Roj234
 * @since 2020/8/29 11:38
 */
public final class Shared {
	public static final boolean DEBUG;
	public static final String VERSION = "1.0";

	public static final File BASE, PROJECT_DIR;

	static IFileWatcher watcher;
	static HRServer hotReload;

	public static final Scheduler PeriodicTask = Scheduler.getDefaultScheduler();
	public static final TaskPool Task = TaskPool.Common();
	public static CMap CONFIG;

	static Project project;
	public static void setProject(String name) {
		if (name == null) name = project.name;

		try (FileOutputStream fos = new FileOutputStream(new File(BASE, "projects/index"))) {
			fos.write(name.getBytes(StandardCharsets.UTF_8));
		} catch (IOException e) {
			Terminal.error("配置保存", e);
		}

		if (project != (project = Project.load(name)))
			AutoCompile.notifyUpdate();
	}

	public static final Mapper mapperFwd = new Mapper();
	public static boolean mappingIsClean;

	public static void loadMapper() {
		if (!mappingIsClean) {
			synchronized (mapperFwd) {
				if (mapperFwd.getClassMap().isEmpty()) {
					File map = new File(BASE, "util/mcp-srg.srg");
					if (!map.isFile()) {
						Terminal.error("混淆映射表"+map+"不存在,建议重新安装");
						return;
					}
					try {
						mapperFwd.initEnv(map, new File(BASE, "libs"), new File(BASE, "util/mapCache.lzma"), false);
					} catch (Exception e) {
						Terminal.error("混淆映射表加载失败", e);
					}
					mappingIsClean = true;
				}
			}
		}
	}

	private static final AtomicInteger ThreadLock = new AtomicInteger();
	public static void _lock() {
		if (!ThreadLock.compareAndSet(0, 1)) {
			throw new FastFailException("其他线程正在编译");
		}
	}
	public static void _unlock() {ThreadLock.set(0);}

	static List<Plugin> plugins;
	static {
		HighResolutionTimer.activate();

		String basePath = System.getProperty("fmd.base_path", "");
		BASE = new File(basePath).getAbsoluteFile();

		try {
			File file = new File(BASE, "config.json");
			CONFIG = new JSONParser().parse(file, NO_DUPLICATE_KEY).asMap();
			CONFIG.dot(true);
		} catch (ParseException | ClassCastException e1) {
			Terminal.error("config.json 有语法错误! 请修正!", e1);
		} catch (IOException e1) {
			Terminal.error("config.json 读取失败!", e1);
		}
		if (CONFIG == null) System.exit(-2);

		PROJECT_DIR = new File(BASE, "projects");
		if (!PROJECT_DIR.isDirectory() && !PROJECT_DIR.mkdirs()) {
			Terminal.error("无法创建项目文件夹: "+PROJECT_DIR.getAbsolutePath());
			System.exit(-2);
		}

		DEBUG = CONFIG.getBool("调试模式");

		IFileWatcher w = null;
		if (CONFIG.getBool("文件修改监控")) {
			try {
				w = new FileWatcher();
			} catch (IOException e) {
				Terminal.warning("无法启动文件监控", e);
			}

			if (CONFIG.getBool("自动编译")) {
				AutoCompile.setEnabled(CONFIG.getInteger("自动编译防抖"));
			}
		}
		if (w == null) w = new IFileWatcher();
		watcher = w;

		plugins = new SimpleList<>();
		for (String s : CONFIG.getOrCreateList("活动的插件").toStringList()) {
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

		int port1 = CONFIG.getInteger("重载端口");
		if (port1 != 0) {
			if (port1 < 0 || port1 > 65535) port1 = 4485;
			try {
				hotReload = new HRServer(port1);
			} catch (IOException e) {
				Terminal.warning("单例锁冲突(如果你确实要多开，请在配置文件中修改端口为0)", e);
				System.exit(-2);
			}
		}

		File index = new File(BASE, "projects/index");
		if (index.isFile()) {
			try {
				project = Project.load(IOUtil.readUTF(index));
			} catch (IOException e) {
				Terminal.warning("项目配置读取失败", e);
			}
		}
	}
}