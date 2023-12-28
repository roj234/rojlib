package roj.platform;

import roj.concurrent.TaskPool;
import roj.concurrent.timing.Scheduler;
import roj.config.ConfigMaster;
import roj.config.ParseException;
import roj.config.Parser;
import roj.config.data.CMapping;
import roj.config.serial.ToSomeString;
import roj.config.serial.ToYaml;
import roj.io.IOUtil;
import roj.net.http.srv.Router;
import roj.text.TextWriter;
import roj.text.logging.Logger;
import roj.ui.terminal.CommandNode;

import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * @author Roj234
 * @since 2023/12/25 0025 16:00
 */
public abstract class Plugin {
	public final Logger logger = Logger.getLogger(getClass().getSimpleName());

	private PluginManager pluginManager;
	private PluginDescriptor desc;

	private CMapping config;
	private File dataFolder, configFile;

	final void init(PluginManager pm, File dataFolder, PluginDescriptor pd) {
		this.pluginManager = pm;
		this.desc = pd;
		this.dataFolder = dataFolder;
		this.configFile = new File(dataFolder, "config.yml");
	}

	public final File getDataFolder() { return dataFolder; }
	public final PluginManager getPluginManager() { return pluginManager; }
	public final boolean isEnabled() { return desc.state == PluginManager.ENABLED; }
	public final PluginDescriptor getDescription() { return desc; }
	protected Logger getLogger() { return logger; }

	protected final CMapping getConfig() {
		if (config == null) reloadConfig();
		return config;
	}
	protected final void reloadConfig() {
		try {
			Parser<?> parser = new ConfigMaster("yml").parser();

			config = parser.parseRaw(configFile).asMap();
			InputStream defaults = getResource("config.yml");
			if (defaults != null) config.merge(parser.parseRaw(defaults).asMap(), true, true);
		} catch (ParseException|IOException e) {
			throw new IllegalArgumentException("无法读取配置文件"+configFile.getName(),e);
		}
	}
	protected final void saveConfig() {
		try (TextWriter tw = TextWriter.to(configFile)) {
			ToSomeString sb = new ToYaml(4).sb(tw);
			config.forEachChild(sb);
			sb.finish();
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
			logger.error("无法找到资源文件{}",path);
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
		return getClassLoader().getResourceAsStream(path);
	}
	protected final ClassLoader getClassLoader() { return desc.pcl == null ? getClass().getClassLoader() : desc.pcl; }

	protected final void registerRoute(String subpath, Router router) {
		DefaultPluginSystem.initHttp().registerSubpathRouter(subpath, router);
	}
	protected final void unregisterRoute(String subpath) {
		DefaultPluginSystem.initHttp().unregisterSubpathRouter(subpath);
	}

	protected final void registerCommand(CommandNode node) {
		assert node.getName() != null;
		DefaultPluginSystem.CMD.register(node);
	}
	protected final void unregisterCommand(String name) {
		DefaultPluginSystem.CMD.unregister(name);
	}

	public final Scheduler getScheduler() { return Scheduler.getDefaultScheduler(); }
	public final TaskPool getExecutor() { return TaskPool.Common(); }

	public String toString() { return desc.getFullDesc(); }

	protected void onLoad() {}
	protected void onEnable() throws Exception {}
	protected void onDisable() {}
}