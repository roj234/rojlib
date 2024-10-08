package roj.plugin;

import roj.archive.zip.ZipFile;
import roj.collect.SimpleList;
import roj.collect.TrieTreeSet;
import roj.collect.XHashSet;
import roj.config.YAMLParser;
import roj.config.auto.SerializerFactory;
import roj.config.data.CMap;
import roj.io.CorruptedInputException;
import roj.io.FastFailException;
import roj.io.IOUtil;
import roj.io.source.FileSource;
import roj.math.Version;
import roj.text.logging.Logger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.regex.Pattern;

/**
 * @author Roj234
 * @since 2023/12/25 0025 16:08
 */
public class PluginManager {
	static final Logger LOGGER = Logger.getLogger("Panger");
	static final XHashSet.Shape<String, PluginDescriptor> PM_SHAPE = XHashSet.noCreation(PluginDescriptor.class, "id");
	final XHashSet<String, PluginDescriptor> plugins = PM_SHAPE.create();

	private final ClassLoader env = getClass().getClassLoader();
	private final File pluginFolder;

	public PluginManager(File pluginFolder) { this.pluginFolder = pluginFolder; }
	public File getPluginFolder() {return pluginFolder;}

	protected boolean isCriticalPlugin(PluginDescriptor pd) {return pd.fileName == null;}

	protected final void readPlugins() {
		File[] plugins = pluginFolder.listFiles();
		if (plugins == null) return;
		for (File file : plugins) {
			String ext = IOUtil.extensionName(file.getName());
			if (ext.equals("jar") || ext.equals("zip")) preloadPlugin(file);
		}
		loadPlugins();
	}
	protected final void loadPlugins() {
		for (var pd : plugins) {
			for (String s : pd.loadBefore) {
				var dep = plugins.get(s);
				if (dep != null) dep.loadAfter.add(pd.id);
			}
		}

		for (var itr = plugins.iterator(); itr.hasNext(); ) {
			var pd = itr.next();
			try {
				loadPlugin(pd);
			} catch (Throwable e) {
				LOGGER.error("插件{}加载失败", e, pd);
				itr.remove();
				unloadPlugin(pd);
			}
		}

		for (var itr = plugins.iterator(); itr.hasNext(); ) {
			var pd = itr.next();
			try {
				enablePlugin(pd);
			} catch (Throwable e) {
				LOGGER.error("插件{}启用失败", e, pd);
				itr.remove();
				unloadPlugin(pd);
			}
		}
	}

	static final int UNLOAD = 0, LOADING = 1, ERRORED = 2, LOADED = 3, ENABLED = 4, DISABLED = 5;
	final void loadPlugin(PluginDescriptor pd) {
		switch (pd.state) {
			default: return;
			case ERRORED: throw new IllegalStateException("插件加载失败");
			case LOADING: throw new IllegalStateException("前置循环引用");
			case UNLOAD: pd.state = LOADING;
				for (String id : pd.loadBefore) {
					var dep = plugins.get(id);
					if (dep != null && dep.state >= LOADED) throw new IllegalStateException("loadBefore["+id+"]已加载");
				}
				for (String s : pd.depend) {
					var dep = plugins.get(s);
					if (dep == null) throw new IllegalStateException("缺少前置: "+s);
					try {
						loadPlugin(dep);
					} catch (Throwable e) {
						throw new IllegalStateException("前置 "+dep.id+" 加载失败",e);
					}
				}
				for (String s : pd.loadAfter) {
					var dep = plugins.get(s);
					if (dep != null) try {
						loadPlugin(dep);
					} catch (Throwable e) {
						throw new IllegalStateException("可选前置 "+dep.id+" 加载失败",e);
					}
				}
		}

		LOGGER.debug("正在加载插件 {}", pd);
		Class<?> klass;
		try {
			if (pd.source != null) {
				var accessible = new PluginDescriptor[pd.depend.size()];
				for (int i = 0; i < pd.depend.size(); i++) accessible[i] = getPlugin(pd.depend.get(i));

				var pcl = new PluginClassLoader(env, pd, accessible);

				pd.cl = pcl;
				klass = pcl.loadClass(pd.mainClass);
			} else {
				klass = PluginManager.class.getClassLoader().loadClass(pd.mainClass);
			}
		} catch (ClassNotFoundException|NoClassDefFoundError|IOException e) {
			synchronized (pd.stateLock) {
				pd.state = LOADED;
				unloadPluginTrusted(pd);
				pd.state = ERRORED;
			}
			throw new FastFailException("无法初始化插件主类"+pd.mainClass, e);
		}

		var fn = SerializerFactory.dataContainer(klass);
		try {
			if (fn == null) {
				throw new FastFailException("在插件主类"+pd.mainClass+"中找不到无参构造器");
			} else {
				pd.instance = (Plugin) fn.apply(0);
				pd.instance.init(this, new File(pluginFolder, pd.id), pd);
				pd.instance.onLoad();

				pd.state = LOADED;
			}
		} catch (Exception e) {
			synchronized (pd.stateLock) {
				pd.state = LOADED;
				unloadPluginTrusted(pd);
				pd.state = ERRORED;
			}
			throw e;
		}
	}

	protected final void loadPluginFromFile(File plugin) throws Exception {
		var pd = preloadPlugin(plugin);
		if (pd != null) loadAndEnablePlugin(pd);
	}
	protected final void loadAndEnablePlugin(PluginDescriptor pd) throws Exception {
		plugins.add(pd);
		loadPlugin(pd);
		enablePlugin(pd);
	}

	private PluginDescriptor preloadPlugin(File plugin) {
		try {
			var pd = getMetadata(plugin);
			if (pd == null) {
				LOGGER.warn("{} 不是插件, 忽略", plugin);
			} else {
				var prev = plugins.get(pd.id);
				if (prev != null) {
					LOGGER.warn("插件 {} 已加载另外的版本 {}", pd, prev.version);
					if (prev.version.compareTo(pd.version) >= 0) {
						return prev;
					} else {
						unloadPlugin(prev);
					}
				}

				plugins.add(pd);
				return pd;
			}
		} catch (Exception e) {
			LOGGER.error("加载插件 {} 出错",e,plugin);
		}

		return null;
	}
	private static final Pattern PLUGIN_ID = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9\\-_.]*$");
	private PluginDescriptor getMetadata(File plugin) throws IOException {
		try (ZipFile za = new ZipFile(plugin)) {
			InputStream in = za.getStream("plugin.yml");
			if (in == null) return null;

			CMap config = new YAMLParser().parse(in).asMap();

			var pd = new PluginDescriptor();
			pd.fileName = plugin.getName();
			pd.source = new FileSource(plugin, false);
			pd.id = config.getString("id");
			if (!PLUGIN_ID.matcher(pd.id).matches()) throw new FastFailException("插件id不合法");
			pd.version = new Version(config.getString("version", "1"));
			pd.charset = Charset.forName(config.getString("charset", "UTF-8"));
			pd.mainClass = config.getString("mainClass");
			if (pd.mainClass.isEmpty()) throw new FastFailException("mainClass未指定");

			pd.depend = config.getList("depend").toStringList();
			pd.loadBefore = config.getList("loadBefore").toStringList();
			pd.loadAfter = config.getList("loadAfter").toStringList();

			SimpleList<String> path = config.getList("extraPath").toStringList();
			pd.extraPath = new TrieTreeSet();
			pd.extraPath.add("plugins"+File.separatorChar+plugin.getName());
			pd.extraPath.add("plugins"+File.separatorChar+pd.id+"\\");
			for (String s : path) pd.extraPath.add(new File(s).getAbsolutePath());

			pd.reflectiveClass = new TrieTreeSet(config.getList("reflectivePackage").toStringList());
			pd.dynamicLoadClass = config.getBool("dynamicLoadClass");
			pd.loadNative = config.getBool("loadNative");

			pd.desc = config.getString("desc");
			pd.authors = config.getList("authors").toStringList();
			pd.website = config.getString("website");
			return pd;
		} catch (Exception e) {
			throw new CorruptedInputException("无效的 plugin.yml", e);
		}
	}

	public PluginDescriptor getPlugin(String id) { return plugins.get(id); }
	public void enablePlugin(PluginDescriptor pd) throws Exception {
		for (String s : pd.depend) {
			var dep = plugins.get(s);
			try {
				enablePlugin(dep);
			} catch (Throwable e) {
				throw new IllegalStateException("前置 "+dep.id+" 启用失败",e);
			}
		}
		for (String s : pd.loadAfter) {
			var dep = plugins.get(s);
			if (dep != null) try {
				enablePlugin(dep);
			} catch (Throwable e) {
				throw new IllegalStateException("可选前置 "+dep.id+" 启用失败",e);
			}
		}

		synchronized (pd.stateLock) {
			if (pd.state < LOADED) throw new IllegalStateException("无法启用未加载的插件 "+pd);
			if (pd.state == ENABLED) return;

			LOGGER.debug("正在启用插件 {}", pd);
			try {
				pd.instance.onEnable();
				pd.state = ENABLED;
			} catch (Throwable e) {
				pd.state = ENABLED;
				unloadPluginTrusted(pd);
				pd.state = ERRORED;
				throw e;
			}
		}
	}
	public void disablePlugin(PluginDescriptor pd) {
		synchronized (pd.stateLock) {
			if (pd.state == UNLOAD) return;
			try {
				if (pd.state == ENABLED && pd.instance != null) pd.instance.onDisable();
				pd.state = DISABLED;
			} catch (Throwable e) {
				LOGGER.error("无法禁用插件 {}",e,pd);
			} finally {
				Plugin instance = pd.instance;
				if (instance != null) instance.postDisable();
			}
		}
	}

	protected final void unloadPlugins() {
		for (PluginDescriptor pd : plugins)
			unloadPluginTrusted(pd);
		System.gc();
	}
	public void unloadPlugin(String name) {
		PluginDescriptor pd;
		synchronized (plugins) {pd = plugins.removeKey(name);}
		if (pd == null) return;
		unloadPluginTrusted(pd);
		System.gc();
	}
	public void unloadPlugin(PluginDescriptor pd) {
		if (isCriticalPlugin(pd)) throw new IllegalArgumentException("不能禁用关键插件"+pd);
		synchronized (plugins) {if (!plugins.remove(pd)) return;}
		unloadPluginTrusted(pd);
	}
	private void unloadPluginTrusted(PluginDescriptor pd) {
		synchronized (pd.stateLock) {
			if (pd.state == UNLOAD) return;
			LOGGER.debug("正在卸载插件 {}", pd);

			disablePlugin(pd);
			pd.instance = null;
			if (pd.cl != null) {
				try {
					pd.cl.close();
				}  catch (Throwable e) {
					LOGGER.error("卸载插件 {} 出错", e, pd);
				}
				pd.cl = null;
			}

			pd.state = UNLOAD;
		}
	}

	public PluginDescriptor getOwner(Class<?> clazz) {return clazz.getClassLoader() instanceof PluginClassLoader pcl ? pcl.desc : getPlugin("Core");}
}