package roj.compiler.context;

import roj.asm.Parser;
import roj.asm.tree.IClass;
import roj.collect.MyHashMap;
import roj.io.IOUtil;
import roj.reflect.DirectAccessor;
import roj.reflect.ReflectionUtils;
import roj.util.Helpers;

import java.io.File;
import java.io.InputStream;
import java.util.Set;

/**
 * @author Roj234
 * @since 2022/9/17 0017 18:15
 */
public final class LibraryRuntime implements Library {
	private interface Rwkk {
		//jdk.internal.jrtfs.SystemImage
		boolean modulesImageExists();
		Object moduleImageFile();
		//jdk.internal.jimage.ImageReader
		Object open(Object path);
		Object getEntryNames(Object imageReader);
	}

	public static final Library INSTANCE = new LibraryRuntime();

	private LibraryRuntime() {}

	private final MyHashMap<CharSequence, IClass> info = new MyHashMap<>();
	private final MyHashMap<String, String> classToModules = new MyHashMap<>();

	private void initMap() {
		if (!classToModules.isEmpty()) return;

		synchronized (this) {
			if (!classToModules.isEmpty()) return;

			if (ReflectionUtils.JAVA_VERSION > 8) try {
				Rwkk rwkk = DirectAccessor.builder(Rwkk.class).weak()
					.access(Class.forName("jdk.internal.jrtfs.SystemImage"), new String[] {"modulesImageExists", "moduleImageFile"}, new String[] {"modulesImageExists", "moduleImageFile"}, null)
					.delegate_o(Class.forName("jdk.internal.jimage.ImageReader"), new String[] {"open", "getEntryNames"})
					.build();

				if (!rwkk.modulesImageExists()) throw new IllegalStateException("module image not exist");

				Object ir = rwkk.open(rwkk.moduleImageFile());
				String[] dir = (String[]) rwkk.getEntryNames(ir);
				for (String path : dir) {
					if (path.startsWith("/package/") || path.startsWith("/modules/") || !path.endsWith(".class")) continue;

					int i = path.indexOf('/', 1);
					String module = path.substring(1, i);
					//int j = path.lastIndexOf('/');
					//String ackage = path.substring(i+1, j);
					//String name = path.substring(j+1, path.length()-6);

					classToModules.put(path.substring(i+1, path.length()-6).intern(), module.intern());
					//System.out.println("module "+module+" package "+ackage+" name "+name);
				}
			} catch (Exception e) {
				e.printStackTrace();
			} else {
				File file = Helpers.getJarByClass(Object.class);
				throw new UnsupportedOperationException("暂不支持java8"+file);
			}
		}
	}

	@Override
	public String getModule(String className) {initMap();return classToModules.get(className);}

	@Override
	public Set<String> content() {initMap();return classToModules.keySet();}

	@Override
	public IClass get(CharSequence name) {
		MyHashMap.AbstractEntry<CharSequence, IClass> entry = info.getEntry(name);
		if (entry != null) return entry.getValue();
		synchronized (info) {
			String name1 = name.toString();
			IClass v = apply(name1);
			info.put(name1, v);
			return v;
		}
	}

	private IClass apply(String s) {
		try {
			String cn = s.concat(".class");
			InputStream in = LibraryRuntime.class.getClassLoader().getResourceAsStream(cn);
			if (in == null) return null;
			return Parser.parseConstants(IOUtil.getSharedByteBuf().readStreamFully(in).toByteArray());
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}
}