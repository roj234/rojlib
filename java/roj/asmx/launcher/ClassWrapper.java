package roj.asmx.launcher;

import roj.archive.zip.ZipFile;
import roj.asm.AsmShared;
import roj.asm.tree.ConstantData;
import roj.asm.util.Context;
import roj.asmx.ITransformer;
import roj.collect.TrieTreeSet;
import roj.io.FastFailException;
import roj.io.IOUtil;
import roj.io.source.FileSource;
import roj.text.logging.Level;
import roj.util.ByteList;
import roj.util.Helpers;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.CodeSigner;
import java.security.CodeSource;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import static roj.asmx.launcher.Bootstrap.LOGGER;

/**
 * @author Roj233
 * @since 2020/11/9 23:10
 */
public class ClassWrapper implements Function<String, Class<?>> {
	private static final Class<?> ERROR_CLASS = Integer.TYPE;
	private static final EntryPoint ENTRY_POINT = (EntryPoint) ClassWrapper.class.getClassLoader();

	private final List<ZipFile> fastZips = new ArrayList<>();

	private INameTransformer nameTransformer;
	private final List<ITransformer> transformers = new ArrayList<>();

	private final TrieTreeSet loadExcept = new TrieTreeSet(), transformExcept = new TrieTreeSet();

	private final Map<String, Class<?>> loadedClasses = new ConcurrentHashMap<>(1024);

	public ClassWrapper() {
		loadExcept.addAll(Arrays.asList(
			"java.",
			"javax."
		));
		transformExcept.addAll(Arrays.asList(
			"roj.asm.",
			"roj.reflect."
		));

		try {
			// preload transforming classes
			ConstantData data = new Context("_classwrapper_", IOUtil.getResource("roj/asmx/launcher/ClassWrapper.class")).getData();
		} catch (Exception e) {
			throw new IllegalStateException("预加载转换器相关类时出现异常", e);
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

		if (loadExcept.strStartsWithThis(name)) return ENTRY_POINT.PARENT.loadClass(name);

		String newName;
		if (nameTransformer == null) {
			newName = name;
		} else {
			newName = nameTransformer.mapName(name);

			clazz = loadedClasses.get(newName);
			if (clazz != null) {
				loadedClasses.put(name, clazz);

				if (clazz != ERROR_CLASS) return clazz;
				throw new ClassNotFoundException(newName);
			}
		}

		ByteList buf = AsmShared.getBuf();
		AsmShared.local().setLevel(true);
		CodeSource cs;

		try {
			name = newName.replace('.', '/').concat(".class");

			block:
			try {
				for (int i = 0; i < fastZips.size(); i++) {
					ZipFile za = fastZips.get(i);
					InputStream in = za.getStream(name);
					if (in != null) {
						buf.readStreamFully(in);
						URL url = new URL("file", "", ((FileSource)za.source()).getFile().getAbsolutePath().replace(File.separatorChar, '/')+"!"+name);
						cs = new CodeSource(url, (CodeSigner[]) null);
						break block;
					}
				}

				InputStream in = ENTRY_POINT.PARENT.getResourceAsStream(name);
				if (in == null) in = ClassLoader.getSystemResourceAsStream(name);
				if (in != null) {
					buf.readStreamFully(in);
					cs = new CodeSource(ENTRY_POINT.PARENT.getResource(name), (CodeSigner[]) null);
				} else {
					throw new FastFailException("no file");
				}
			} catch (IOException e) {
				LOGGER.log(Level.ERROR, "读取类'{}'时发生异常", e, name);
				throw e;
			}

			if (!transformExcept.strStartsWithThis(newName)) transform(name, newName.replace('.', '/'), buf);
			clazz = ENTRY_POINT.defineClassA(newName, buf.list, 0, buf.wIndex(), cs);

			loadedClasses.put(name, clazz);
			loadedClasses.put(newName, clazz);
			return clazz;
		} catch (Throwable e) {
			loadedClasses.put(name, ERROR_CLASS);
			loadedClasses.put(newName, ERROR_CLASS);
			throw new ClassNotFoundException(newName, e);
		} finally {
			AsmShared.local().setLevel(false);
		}
	}

	private int reentrant = -1;
	public final void transform(String name, String transformedName, ByteList list) {
		Context ctx = new Context(name, list);
		boolean changed = false;
		List<ITransformer> ts = transformers;
		int prev = reentrant;
		for (int i = 0; i < ts.size(); i++) {
			reentrant = i;
			try {
				boolean changed1 = ts.get(i).transform(transformedName, ctx);
				if (changed1 && prev == i) LOGGER.warn("类转换器'{}'可能造成了循环调用", ts.get(i).getClass().getName());
				changed |= changed1;
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
		reentrant = prev;
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

	public void enableFastZip(URL url) throws IOException {
		ZipFile e = new ZipFile(new File(url.getPath().substring(1)));
		if (fastZips.isEmpty()) e.getStream(e.entries().iterator().next()).close(); // INIT
		fastZips.add(e);
	}
}