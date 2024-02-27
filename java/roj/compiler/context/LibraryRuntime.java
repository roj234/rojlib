package roj.compiler.context;

import roj.asm.Parser;
import roj.asm.tree.ConstantData;
import roj.collect.MyHashMap;
import roj.compiler.plugins.asm.ASM;
import roj.crypt.CRC32s;
import roj.io.IOUtil;
import roj.reflect.Bypass;
import roj.text.Interner;

import java.io.File;
import java.io.IOException;
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

	private final MyHashMap<CharSequence, ConstantData> info = new MyHashMap<>();
	private final MyHashMap<String, String> classToModules = new MyHashMap<>();

	private void initMap() {
		if (!classToModules.isEmpty()) return;

		synchronized (this) {
			if (!classToModules.isEmpty()) return;

			if (ASM.TARGET_JAVA_VERSION > 8) {
				try {
					Rwkk rwkk = Bypass.builder(Rwkk.class).weak()
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

						classToModules.put(Interner.intern(path.substring(i+1, path.length()-6)), Interner.intern(module));
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			} else {
				File file = IOUtil.getJar(Object.class);
				throw new UnsupportedOperationException("暂不支持java8"+file);
			}
		}
	}

	@Override
	public int fileHashCode() {
		var prop = System.getProperties();
		var b = IOUtil.getSharedByteBuf();
		b.putChars(prop.getProperty("java.vendor", "")).putShort(0)
		 .putChars(prop.getProperty("java.vm.version", "")).putShort(0)
		 .putChars(prop.getProperty("os.arch", "")).putShort(0)
		 .putChars(prop.getProperty("os.name", "")).putShort(0)
		 .putChars(prop.getProperty("sun.boot.library.path", ""));
		return CRC32s.once(b.list, 0, b.wIndex());
	}
	// 尽管如此，我暂时也不知道怎么拿到module-info
	@Override
	public Set<String> content() {initMap();return classToModules.keySet();}

	@Override
	public ConstantData get(CharSequence name) {
		MyHashMap.AbstractEntry<CharSequence, ConstantData> entry = info.getEntry(name);
		if (entry != null) return entry.getValue();
		synchronized (info) {
			String name1 = name.toString();
			ConstantData v = apply(name1);
			info.put(name1, v);
			return v;
		}
	}
	@Override
	public String getModule(String className) {initMap();return classToModules.get(className);}

	@Override
	public InputStream getResource(CharSequence name) throws IOException {return ClassLoader.getSystemResourceAsStream(name.toString());}

	private ConstantData apply(String s) {
		try {
			String cn = s.concat(".class");
			InputStream in = ClassLoader.getSystemResourceAsStream(cn);
			if (in == null) return null;
			return Parser.parseConstants(IOUtil.read(in));
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}
}