package roj.plugin;

import org.jetbrains.annotations.Nullable;
import roj.collect.Hasher;
import roj.collect.MyHashSet;
import roj.concurrent.TaskPool;
import roj.concurrent.task.ITask;
import roj.concurrent.timing.LoopTaskWrapper;
import roj.concurrent.timing.ScheduleTask;
import roj.concurrent.timing.Scheduler;
import roj.config.Flags;
import roj.config.ParseException;
import roj.config.YAMLParser;
import roj.config.data.CMap;
import roj.config.serial.ToYaml;
import roj.io.IOUtil;
import roj.net.http.server.Router;
import roj.net.http.server.auto.OKRouter;
import roj.text.TextWriter;
import roj.text.logging.Logger;
import roj.ui.terminal.CommandNode;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Set;
import java.util.function.ToLongFunction;

/**
 * @author Roj234
 * @since 2023/12/25 0025 16:00
 */
public abstract class Plugin {
	private Logger logger;

	private PluginManager pluginManager;
	private PluginDescriptor desc;

	protected CMap config;
	private File dataFolder, configFile;

	final void init(PluginManager pm, File dataFolder, PluginDescriptor pd) {
		this.pluginManager = pm;
		this.desc = pd;
		this.dataFolder = dataFolder;
		this.configFile = new File(dataFolder, "config.yml");
		this.logger = Logger.getLogger(pd.id);
	}

	public final File getDataFolder() {return dataFolder;}
	public final PluginManager getPluginManager() {return pluginManager;}
	public final PluginDescriptor getDescription() {return desc;}
	public Logger getLogger() {return logger;}

	protected final CMap getConfig() {
		if (config == null) reloadConfig();
		return config;
	}
	protected void reloadConfig() {
		try {
			var parser = new YAMLParser();
			saveDefaultConfig();
			config = parser.parse(configFile, Flags.LENIENT).asMap();

			var defaults = getResource("config.yml");
			if (defaults != null) config.merge(parser.parse(defaults, Flags.LENIENT).asMap(), true, true);
		} catch (ParseException|IOException e) {
			throw new IllegalArgumentException("无法读取配置文件"+configFile.getName(),e);
		}
	}
	protected final void saveConfig() {
		try (var tw = TextWriter.to(configFile)) {
			var sb = new ToYaml("    ").sb(tw);
			config.accept(sb);
		} catch (IOException e) {
			logger.error("无法保存配置文件{}",e,configFile);
		}
	}
	protected final void saveDefaultConfig() {
		if (!configFile.exists()) saveResource("config.yml", false);
	}

	protected final void saveResource(String path, boolean replace) {
		if (path == null || path.equals("")) throw new IllegalArgumentException("资源的路径不能为空");

		path = path.replace('\\', '/');

		InputStream in = getResource(path);
		if (in == null) {
			logger.error("无法找到资源文件{}", path);
			return;
		}

		File outFile = new File(dataFolder, path);
		File outDir = outFile.getParentFile();
		if (!outDir.exists()) outDir.mkdirs();

		if (!outFile.exists() || replace) {
			try (FileOutputStream fos = new FileOutputStream(outFile)) {
				IOUtil.copyStream(in, fos);
			} catch (IOException e) {
				logger.error("无法保存资源文件{}到{}",e,path,outFile);
			}
		}
	}
	protected final Reader getTextResource(String file) {
		InputStream in = getResource(file);
		return in == null ? null : new InputStreamReader(in, StandardCharsets.UTF_8);
	}
	public final InputStream getResource(String path) {
		if (path == null) throw new IllegalArgumentException("Filename cannot be null");
		return desc.cl.getResourceAsStream(path);
	}

	private Set<String> pPaths = Collections.emptySet();
	protected final void registerRoute(String path, Router router) {router(path).addPrefixDelegation(path, router);}
	protected final void registerRoute(String path, Router router, String... interceptors) {router(path).addPrefixDelegation(path, router, interceptors);}
	private OKRouter router(String path) {
		synchronized (desc.stateLock) {
			if (pPaths.isEmpty()) pPaths = new MyHashSet<>(4);
			if (pPaths.add(path)) return Panger.initHttp();
		}
		throw new IllegalStateException("路径"+path+"已注册");
	}
	protected final void unregisterRoute(String path) {
		synchronized (desc.stateLock) {
			if (pPaths.remove(path)) Panger.initHttp().removePrefixDelegation(path);
		}
	}
	private Set<String> pIntecs = Collections.emptySet();
	protected final void registerInterceptor(String name, OKRouter.Dispatcher interceptor) {
		synchronized (desc.stateLock) {
			if (pIntecs.isEmpty()) pIntecs = new MyHashSet<>(4);
			if (pIntecs.add(name)) Panger.initHttp().setInterceptor(name, interceptor);
		}
	}
	@Nullable
	protected final OKRouter.Dispatcher getInterceptor(String name) {return Panger.initHttp().getInterceptor(name);}
	protected final void unregisterInterceptor(String name) {
		synchronized (desc.stateLock) {
			if (pIntecs.remove(name)) Panger.initHttp().removeInterceptor(name);
		}
	}

	private Set<CommandNode> pCmds = Collections.emptySet();
	protected final void registerCommand(CommandNode node) {
		synchronized (desc.stateLock) {
			if (pCmds.isEmpty()) pCmds = new MyHashSet<>(4, Hasher.identity());
			if (pCmds.add(node)) Panger.CMD.register(node);
		}
	}
	protected final void unregisterCommand(CommandNode node) {
		synchronized (desc.stateLock) {
			if (pCmds.remove(node)) Panger.CMD.unregister(node);
		}
	}

	private volatile PSched pSched;
	public final Scheduler getScheduler() {
		if (pSched == null) {
			synchronized (desc.stateLock) {
				if (pSched == null) pSched = new PSched(this, Scheduler.getDefaultScheduler());
			}
		}
		return pSched;
	}

	protected void onLoad() {}
	protected void onEnable() throws Exception {}
	protected void onDisable() {}

	final void postDisable() {
		if (pSched != null) pSched.cancelAll();
		for (CommandNode cmd : pCmds) Panger.CMD.unregister(cmd);
		for (String path : pPaths) unregisterRoute(path);
		for (String path : pIntecs) unregisterInterceptor(path);
		pCmds = Collections.emptySet();
		pPaths = Collections.emptySet();
		pIntecs = Collections.emptySet();
	}

	public String toString() { return desc.getFullDesc(); }

	private static final class PSched extends Scheduler {
		final Plugin plugin;
		final MyHashSet<ScheduleTask> userTasks = new MyHashSet<>(Hasher.identity());

		private final Scheduler sched;
		public PSched(Plugin plugin, Scheduler sched) {
			super((ToLongFunction<ScheduleTask>) null);
			this.plugin = plugin;
			this.sched = sched;
		}

		@Override
		public ScheduleTask delay(ITask task, long delayMs) {
			ScheduleTask delay = sched.delay(task, delayMs);
			synchronized (userTasks) { userTasks.add(delay); }
			if (task instanceof PLoopTask p) p.realTask = delay;
			return delay;
		}
		@Override
		public ScheduleTask runAsync(ITask task) {
			PLongTask wrapper = new PLongTask(task);
			synchronized (userTasks) { userTasks.add(wrapper); }
			TaskPool.Common().submit(wrapper);
			return wrapper;
		}
		public ScheduleTask loop(ITask task, long intervalMs) { return delay(new PLoopTask(sched, task, intervalMs, -1), 0); }
		public ScheduleTask loop(ITask task, long intervalMs, int count) { return delay(new PLoopTask(sched, task, intervalMs, count), 0); }
		public ScheduleTask loop(ITask task, long intervalMs, int count, long delayMs) { return delay(new PLoopTask(sched, task, intervalMs, count), delayMs); }
		public void submit(ITask task) { runAsync(task); }

		public void cancelAll() {
			synchronized (userTasks) {
				for (var task : userTasks) task.cancel();
				userTasks.clear();
			}
		}

		private final class PLongTask implements ITask, ScheduleTask {
			private final ITask task;

			public PLongTask(ITask task) { this.task = task; }

			public ITask getTask() { return task; }
			public boolean isExpired() { return !userTasks.contains(this); }
			public boolean isCancelled() { return task.isCancelled(); }
			public boolean cancel() { return task.cancel(); }

			@Override
			public void execute() throws Exception {
				long start = System.currentTimeMillis();

				try {
					task.execute();
				} finally {
					synchronized (userTasks) {
						userTasks.remove(this);
					}
				}

				start = System.currentTimeMillis() - start;
				if (start > 1000) plugin.getLogger().warn("[Long]任务{}花费了太长的时间", task);
			}
		}
		private final class PLoopTask extends LoopTaskWrapper {
			ScheduleTask realTask;

			PLoopTask(Scheduler sched, ITask task, long interval, int repeat) {
				super(sched, task, interval, repeat, true);
			}

			@Override
			public long getNextRun() {
				long run = super.getNextRun();
				if (run == 0) {
					if (repeat == 0) {
						synchronized (userTasks) {
							userTasks.remove(realTask);
						}
					} else {
						plugin.getLogger().warn("[Loop]任务{}花费了太长的时间", task);
					}
				}
				return run;
			}
		}
	}
}