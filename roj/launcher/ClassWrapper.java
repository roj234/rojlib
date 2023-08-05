package roj.launcher;

import roj.archive.zip.ZipArchive;
import roj.asm.AsmShared;
import roj.asm.tree.ConstantData;
import roj.asm.util.Context;
import roj.collect.TrieTreeSet;
import roj.io.IOUtil;
import roj.text.logging.Level;
import roj.util.ByteList;
import roj.util.Helpers;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.security.CodeSource;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import static roj.launcher.Bootstrap.LOGGER;

/**
 * @author Roj233
 * @since 2020/11/9 23:10
 */
public class ClassWrapper implements Function<String, Class<?>> {
	private static final Class<?> ERROR_CLASS = Integer.TYPE;
	private static final EntryPoint PARENT = (EntryPoint) ClassWrapper.class.getClassLoader();

	private final List<ZipArchive> zipArchives = new ArrayList<>();

	private INameTransformer nameTransformer;
	private final List<ITransformer> transformers = new ArrayList<>();

	private final TrieTreeSet loadExcept = new TrieTreeSet(), transformExcept = new TrieTreeSet();

	private final Map<String, Class<?>> loadedClasses = new ConcurrentHashMap<>(1024);

	public ClassWrapper() {
		transformExcept.addAll(Arrays.asList(
			"roj.asm.",
			"javax.",
			"argo.",
			"org.objectweb.asm.",
			"com.google.common.",
			"org.bouncycastle."
		));

		try {
			// preload transforming classes
			ConstantData data = new Context("_classwrapper_", IOUtil.readRes("roj/launcher/ClassWrapper.class")).getData();
		} catch (Exception e) {
			LOGGER.log(Level.ERROR, "预加载转换器相关类时出现异常", e);
		}
	}

	public void registerTransformer(ITransformer tr) {
		transformers.add(tr);
		if (tr instanceof INameTransformer) {
			if (nameTransformer == null) {
				nameTransformer = (INameTransformer) tr;
				LOGGER.trace("注册类名映射 '{}'", tr.getClass().getName());
			} else {
				LOGGER.debug("类名映射 '{}' 因为已存在 '{}' 而被跳过", tr.getClass().getName(), nameTransformer.getClass().getName());
			}
		} else {
			LOGGER.trace("注册类转换器 '{}'", tr.getClass().getName());
		}
	}

	@Override
	public Class<?> apply(String s) {
		try {
			return findClass(s);
		} catch (Exception e) {
			Helpers.athrow(e);
			return Helpers.nonnull();
		}
	}

	private Class<?> findClass(String name) throws ClassNotFoundException {
		Class<?> clazz = loadedClasses.get(name);
		if (clazz != null) {
			if (clazz != ERROR_CLASS) return clazz;
			throw new ClassNotFoundException(name);
		}

		if (loadExcept.strStartsWithThis(name)) return PARENT.getClass().getClassLoader().loadClass(name);

		String oldName, newName;
		if (nameTransformer == null) {
			oldName = newName = name;
		} else {
			newName = nameTransformer.mapName(name);

			clazz = loadedClasses.get(newName);
			if (clazz != null) {
				if (clazz != ERROR_CLASS) return clazz;
				throw new ClassNotFoundException(newName);
			}

			oldName = nameTransformer.unmapName(name);
		}

		ByteList buf = AsmShared.getBuf();
		AsmShared.local().setLevel(true);
		CodeSource cs;

		try {
			name = oldName.replace('.', '/').concat(".class");

			block1:
			if (true) {
				URL url = PARENT.findResource(name);
				if (url != null) {
					try {
						URLConnection conn = url.openConnection();
						buf.readStreamFully(conn.getInputStream());
						cs = PARENT.getCodeSource(url, oldName, conn);
					} catch (IOException e) {
						LOGGER.log(Level.ERROR, "读取类'{}'时发生异常", e, name);
						throw e;
					}
				} else {
					throw new IOException("no "+name);
				}
			} else {
				try {
					for (int i = 0; i < zipArchives.size(); i++) {
						InputStream in = zipArchives.get(i).getInput(name);
						if (in != null) {
							buf.readStreamFully(in);
							cs = null;
							break block1;
						}
					}
				} catch (IOException e) {
					LOGGER.log(Level.ERROR, "读取类'{}'时发生异常", e, name);
					throw e;
				}
				throw new IOException("no file");
			}

			if (!transformExcept.strStartsWithThis(oldName)) transform(oldName, newName, buf);
			clazz = PARENT.defineClassA(newName, buf.list, 0, buf.wIndex(), cs);

			loadedClasses.put(oldName, clazz);
			loadedClasses.put(newName, clazz);
			return clazz;
		} catch (Throwable e) {
			loadedClasses.put(oldName, ERROR_CLASS);
			loadedClasses.put(newName, ERROR_CLASS);
			throw new ClassNotFoundException(oldName, e);
		} finally {
			AsmShared.local().setLevel(false);
		}
	}

	private int reentrant = -1;
	private void transform(String name, String transformedName, ByteList list) {
		Context ctx = new Context(name, list);
		boolean changed = false;
		List<ITransformer> ts = transformers;
		if (reentrant >= 0) LOGGER.warn("类转换器'{}'可能造成了循环调用", ts.get(reentrant).getClass().getName());
		for (int i = 0; i < ts.size(); i++) {
			reentrant = i;
			try {
				changed |= ts.get(i).transform(transformedName, ctx);
			} catch (Throwable e) {
				LOGGER.fatal("转换类'{}'时发生异常", e, name);
				try {
					ctx.getData().dump();
				} catch (Throwable e1) {
					LOGGER.fatal("保存'{}'的内容用于调试时发生异常", e1, name);
				}
				Helpers.athrow(e);
			}
		}
		reentrant = -1;
		if (changed) {
			list.clear();
			list.put(ctx.get());
		}
	}

	public List<ITransformer> getTransformersReadonly() { return transformers; }

	public void addClassLoaderExclusion(String toExclude) { loadExcept.add(toExclude); }
	public void addTransformerExclusion(String toExclude) { transformExcept.add(toExclude); }

	public void clearNegativeClass(Set<String> fileNames) {
		for (String key : fileNames) loadedClasses.remove(key, ERROR_CLASS);
	}
}
