package roj.platform;

import roj.archive.zip.ZipArchive;
import roj.collect.LFUCache;
import roj.collect.MyHashMap;
import roj.collect.SimpleList;
import roj.collect.TrieTreeSet;
import roj.config.YAMLParser;
import roj.config.data.CMapping;
import roj.io.CorruptedInputException;
import roj.io.FastFailException;
import roj.io.IOUtil;
import roj.io.source.FileSource;
import roj.math.Version;
import roj.reflect.ClassDefiner;
import roj.text.logging.Logger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Iterator;

/**
 * @author Roj234
 * @since 2023/12/25 0025 16:08
 */
public class PluginManager {
	final Logger LOGGER = Logger.getLogger(getClass().getSimpleName());
	final MyHashMap<String, PluginDescriptor> plugins = new MyHashMap<>();

	private final ClassLoader env = getClass().getClassLoader();
	private final File dataFolder;
	private final LFUCache<String, PluginDescriptor> loadedClasses = new LFUCache<>(1000,1);

	public PluginManager(File dataFolder) { this.dataFolder = dataFolder; }

	protected boolean isCriticalPlugin(PluginDescriptor pd) {
		return pd.fileName == null;
	}

	public void loadPlugins() {
		for (PluginDescriptor pd : plugins.values()) {
			for (String s : pd.loadBefore) {
				PluginDescriptor dep = plugins.get(s);
				if (dep != null) dep.loadAfter.add(pd.id);
			}
		}

		for (Iterator<PluginDescriptor> itr = plugins.values().iterator(); itr.hasNext(); ) {
			PluginDescriptor pd = itr.next();
			try {
				loadPlugin(pd, true);
			} catch (Throwable e) {
				LOGGER.error("插件{}加载失败", e, pd);
				unloadPlugin(pd);
				itr.remove();
			}
		}

		for (Iterator<PluginDescriptor> itr = plugins.values().iterator(); itr.hasNext(); ) {
			PluginDescriptor pd = itr.next();
			try {
				enablePlugin(pd);
			} catch (Throwable e) {
				LOGGER.error("插件{}启用失败", e, pd);
				unloadPlugin(pd);
				itr.remove();
			}
		}
	}

	static final int UNLOAD = 0, LOADING = 1, LOADED = 2, ENABLED = 3, DISABLED = 4;
	private boolean loadPlugin(PluginDescriptor pd, boolean must) throws Throwable {
		if (pd == null) return false;
		switch (pd.state) {
			case UNLOAD:
				pd.state = LOADING;

				for (String s : pd.depend) {
					PluginDescriptor dep = plugins.get(s);
					boolean b;
					try {
						b = loadPlugin(dep, true);
					} catch (Throwable e) {
						throw new IllegalStateException("插件 "+pd+" 的前置 "+dep+" 加载失败",e);
					}
					if (!b) throw new IllegalStateException("插件 "+pd+" 缺少必须的前置: "+s);
				}
				for (String s : pd.loadAfter) {
					PluginDescriptor dep = plugins.get(s);
					try {
						loadPlugin(dep, false);
					} catch (Throwable e) {
						throw new IllegalStateException("插件 "+pd+" 的可选前置 "+dep+" 加载失败",e);
					}
				}
			break;
			case LOADING:
				if (must) throw new IllegalStateException("前置循环引用至 "+pd);
			break;
			default: return true;
		}

		LOGGER.info("正在加载插件 {}", pd);
		pd.cl = pd.pcl = new PluginClassLoader(env, pd);
		Class<?> klass = pd.pcl.findClass(pd.mainClass);
		pd.instance = (Plugin) klass.newInstance();
		pd.instance.init(this, new File(dataFolder, pd.id), pd);
		pd.instance.onLoad();

		pd.state = LOADED;
		return true;
	}
	public void readPlugins() {
		for (File file : dataFolder.listFiles()) {
			String ext = IOUtil.extensionName(file.getName()).toLowerCase();
			if (ext.equals("jar") || ext.equals("zip")) readPlugin(file);
		}
		loadPlugins();
	}

	public void loadPlugin(File plugin) {
		readPlugin(plugin);
		loadPlugins();
	}
	public void readPlugin(File plugin) {
		try {
			PluginDescriptor pd = getMetadata(plugin);
			if (pd == null) {
				LOGGER.warn("{} 不是插件, 忽略", plugin);
				return;
			} else {
				PluginDescriptor prev = plugins.get(pd.id);
				if (prev != null) {
					LOGGER.warn("插件 {} 已加载另外的版本 {}", pd, prev.version);
					if (prev.version.compareTo(pd.version) >= 0) {
						return;
					} else {
						unloadPlugin(prev);
					}
				}
			}
			plugins.put(pd.id, pd);
		} catch (Exception e) {
			LOGGER.error("加载插件 {} 出错",e,plugin);
		}
	}
	private PluginDescriptor getMetadata(File plugin) throws IOException {
		try (ZipArchive za = new ZipArchive(plugin)) {
			InputStream in = za.getInput("plugin.yml");
			if (in == null) return null;

			CMapping config = new YAMLParser().parseRaw(in).asMap();

			PluginDescriptor pd = new PluginDescriptor();
			pd.fileName = plugin.getName();
			pd.source = new FileSource(plugin);
			pd.id = config.getString("id");
			if (pd.id.isEmpty()) throw new FastFailException("id未指定");
			pd.version = new Version(config.getString("version", "1"));
			pd.charset = Charset.forName(config.getString("charset", "UTF-8"));
			pd.mainClass = config.getString("mainClass");
			if (pd.mainClass.isEmpty()) throw new FastFailException("mainClass未指定");

			pd.depend = config.getList("depend").asStringList();
			pd.loadBefore = config.getList("loadBefore").asStringList();
			pd.loadAfter = config.getList("loadAfter").asStringList();

			SimpleList<String> path = config.getList("extraPath").asStringList();
			pd.extraPath = new TrieTreeSet();
			for (String s : path) pd.extraPath.add(new File(s).getAbsolutePath());

			pd.reflectiveClass = new TrieTreeSet(config.getList("reflectivePackage").asStringList());
			pd.dynamicLoadClass = config.getBool("dynamicLoadClass");
			pd.loadNative = config.getBool("loadNative");

			pd.desc = config.getString("desc");
			pd.authors = config.getList("authors").asStringList();
			pd.website = config.getString("website");
			return pd;
		} catch (Exception e) {
			throw new CorruptedInputException("无效的 plugin.yml", e);
		}
	}

	protected void registerPlugin(PluginDescriptor pd, Plugin plugin) {
		if (plugin != null) {
			pd.instance = plugin;
			plugin.init(this, dataFolder, pd);
			plugin.onLoad();
			pd.state = LOADED;
		} else {
			assert isCriticalPlugin(pd);
			pd.state = ENABLED;
		}
		plugins.put(pd.id, pd);
	}

	public PluginDescriptor getPlugin(String id) { return plugins.get(id); }
	public void enablePlugin(String id) { enablePlugin(getPlugin(id)); }
	public void enablePlugin(Plugin plugin) { enablePlugin(plugin.getDescription()); }
	public void enablePlugin(PluginDescriptor pd) {
		if (pd.state < LOADED) throw new IllegalStateException("无法启用未加载的插件 "+pd);
		try {
			if (pd.state != ENABLED) pd.instance.onEnable();
			pd.state = ENABLED;
		} catch (Exception e) {
			LOGGER.error("无法启用插件 {}",e,pd);
		}
	}
	public void disablePlugin(String id) { disablePlugin(getPlugin(id)); }
	public void disablePlugin(Plugin plugin) { disablePlugin(plugin.getDescription()); }
	public void disablePlugin(PluginDescriptor pd) {
		if (pd.state == UNLOAD) return;
		try {
			if (pd.state == ENABLED && pd.instance != null) pd.instance.onDisable();
			pd.state = DISABLED;
		} catch (Exception e) {
			LOGGER.error("无法禁用插件 {}",e,pd);
		}
	}

	public void unloadPlugins() {
		for (PluginDescriptor pd : plugins.values()) {
			unloadPlugin(pd);
		}
		synchronized (loadedClasses) { loadedClasses.clear(); }
		plugins.clear();
		System.gc();
	}
	public void unloadPlugin(String name) {
		PluginDescriptor pd = plugins.get(name);
		if (pd == null) return;
		if (isCriticalPlugin(pd)) throw new IllegalArgumentException("不能禁用关键插件"+pd);
		plugins.remove(name);
		synchronized (loadedClasses) { loadedClasses.clear(); }
		unloadPlugin(pd);
		System.gc();
	}
	private void unloadPlugin(PluginDescriptor pd) {
		LOGGER.info("正在卸载插件 {}", pd);
		disablePlugin(pd);
		pd.instance = null;
		if (pd.pcl != null) {
			try {
				pd.pcl.close();
			}  catch (Throwable e) {
				LOGGER.error("卸载插件 {} 出错", e, pd);
			}
			pd.cl = pd.pcl = null;
		}

		pd.state = UNLOAD;
	}

	public PluginDescriptor getPluginDescriptor(Class<?> clazz) {
		if (clazz != null) {
			ClassLoader cl = clazz.getClassLoader();
			if (cl instanceof PluginClassLoader) return ((PluginClassLoader) cl).desc;
		}

		StackTraceElement[] trace = new Throwable().getStackTrace();
		for (StackTraceElement element : trace) {
			PluginDescriptor pd = loadedClasses.get(element.getClassName());
			if (pd != null) return pd;

			synchronized (loadedClasses) {
				pd = loadedClasses.get(element.getClassName());
				if (pd != null) return pd;

				for (PluginDescriptor pd1 : plugins.values()) {
					if (pd1.pcl != null) {
						if (ClassDefiner.findLoadedClass(pd1.pcl, element.getClassName()) != null) {
							loadedClasses.put(element.getClassName(), pd1);
							return pd1;
						}
					}
				}
			}
		}

		return getPlugin("Core");
	}
}