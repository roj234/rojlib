package roj.launcher;

import roj.archive.zip.ZipArchive;
import roj.asm.AsmShared;
import roj.asm.util.Context;
import roj.collect.TrieTreeSet;
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
	}

	public void registerTransformer(ITransformer transformer) {
		transformers.add(transformer);
		if (nameTransformer == null && transformer instanceof INameTransformer)
			nameTransformer = (INameTransformer) transformer;
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

		if (loadExcept.strStartsWithThis(name)) return PARENT.loadClass(name);

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
						LOGGER.log(Level.ERROR, "exception reading {}", e, name);
						throw e;
					}
				} else {
					throw new IOException("no file");
				}
			} else {
				try {
					for (int i = 0; i < zipArchives.size(); i++) {
						InputStream in = zipArchives.get(i).getStream(name);
						if (in != null) {
							buf.readStreamFully(in);
							cs = null;
							break block1;
						}
					}
				} catch (IOException e) {
					LOGGER.log(Level.ERROR, "exception reading {}", e, name);
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

	private void transform(String name, String transformedName, ByteList list) {
		Context ctx = new Context(name, list);
		boolean changed = false;
		List<ITransformer> ts = transformers;
		for (int i = 0; i < ts.size(); i++) {
			try {
				changed |= ts.get(i).transform(transformedName, ctx);
			} catch (Throwable e) {
				ctx.getData().dump();
				throw e;
			}
		}
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
