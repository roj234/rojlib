package roj.compiler.context;

import roj.archive.zip.ZEntry;
import roj.archive.zip.ZipFile;
import roj.archive.zip.ZipFileWriter;
import roj.asm.Parser;
import roj.asm.cp.ConstantPool;
import roj.asm.tree.ConstantData;
import roj.asm.tree.MethodNode;
import roj.collect.MyHashMap;
import roj.collect.SimpleList;
import roj.collect.ToIntMap;
import roj.io.IOUtil;
import roj.reflect.Bypass;
import roj.reflect.ReflectionUtils;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * @author Roj234
 * @since 2024/10/10 0010 1:01
 */
public final class LibraryCache implements Library {
	private interface J9 {
		//jdk.internal.jrtfs.SystemImage
		boolean modulesImageExists();
		Object moduleImageFile();
		//jdk.internal.jimage.ImageReader
		Object open(Object path);
		String[] getEntryNames(Object imageReader);
		byte[] getResource(Object imageReader, String name);
	}

	public static final List<Library> RUNTIME = Objects.requireNonNull(getRuntime(ReflectionUtils.JAVA_VERSION));
	public static List<Library> getRuntime(int javaVersion) {
		try {
			var symTable = new File(GlobalContext.cacheFolder, "JavaSymbol"+javaVersion+".zip");
			if (symTable.isFile()) {
				try {
					return loadSymbolCache(symTable);
				} catch (Exception ignored) {}
			}

			if (ReflectionUtils.JAVA_VERSION != javaVersion) {
				GlobalContext.debugLogger().warn("请求的版本{}与当前版本{}不匹配！请使用对应版本的Java生成符号缓存", javaVersion, ReflectionUtils.JAVA_VERSION);
				return null;
			}

			if (ReflectionUtils.JAVA_VERSION <= 8) {
				File file = IOUtil.getJar(Object.class);
				return Collections.singletonList(new LibraryZipFile(file));
			}

			GlobalContext.debugLogger().debug("正在初始化LibraryRuntimeJ9Plus...");

			Class<?> _jir = Class.forName("jdk.internal.jimage.ImageReader");
			J9 j9 = Bypass.builder(J9.class).weak().inline()
						  .access(Class.forName("jdk.internal.jrtfs.SystemImage"), new String[] {"modulesImageExists", "moduleImageFile"}, new String[] {"modulesImageExists", "moduleImageFile"}, null)
						  .delegate_o(_jir, new String[] {"open"})
						  .delegate(_jir, new String[] {"getEntryNames", "getResource"})
						  .build();

			if (!j9.modulesImageExists()) throw new IllegalStateException("module image not exist");

			MyHashMap<String, LibraryCache> caches = new MyHashMap<>();
			Function<String, LibraryCache> newCache = LibraryCache::new;

			try (var ir = (AutoCloseable) j9.open(j9.moduleImageFile())) {
				for (String path : j9.getEntryNames(ir)) {
					if (path.startsWith("/package/") || path.startsWith("/modules/") || !path.endsWith(".class")) continue;

					int i = path.indexOf('/', 1);
					String module = path.substring(1, i);

					ConstantData data;
					try {
						data = Parser.parseConstants(j9.getResource(ir, path));

						var list = data.attributesNullable();
						if (list != null) {
							list.removeByName("NestMembers");
							list.removeByName("NestHost");
							list.removeByName("BootstrapMethods");
							list.removeByName("SourceFile");
							list.removeByName("EnclosingMethod");
						}
					} catch (Exception e) {
						String className = path.substring(i + 1, path.length() - 6);
						GlobalContext.debugLogger().warn("无法解析模块{}中的类{}",module,className);
						continue;
					}
					caches.computeIfAbsent(module, newCache).add(data);
				}
			}

			var prop = System.getProperties();
			var version = prop.getProperty("java.vendor", "")+"-"+prop.getProperty("java.vm.version", "")+"-"+prop.getProperty("os.arch", "")+"-"+prop.getProperty("os.name", "");

			var tmp = new ZipFileWriter(symTable);
			var ob = new ByteList.ToStream(tmp);
			for (var cache : caches.values()) {
				cache.module = cache.version;
				cache.version = version;
				GlobalContext.debugLogger().trace("正在保存模块{}",cache.versionCode());

				tmp.beginEntry(new ZEntry(cache.module));
				cache.serialize(ob);
				ob.flush();
			}
			ob.close();

			return loadSymbolCache(symTable);
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}
	private static List<Library> loadSymbolCache(File symTable) throws IOException {
		List<Library> output = new SimpleList<>();

		try (var zf = new ZipFile(symTable)) {
			for (ZEntry entry : zf.entries()) {
				output.add(new LibraryCache(zf.get(entry, IOUtil.getSharedByteBuf())));
			}
		}

		return output;
	}

	private String module, version;
	private final SimpleList<ConstantPool> pool;
	private final ToIntMap<String> table = new ToIntMap<>();
	private final DynByteBuf data;

	public LibraryCache(String version) {
		this.version = version;
		this.pool = SimpleList.asModifiableList(new ConstantPool());
		this.data = new ByteList();
	}

	public void add(ConstantData data) {
		for (MethodNode method : data.methods()) {
			var attributes = method.attributesNullable();
			if (attributes != null) attributes.removeByName("Code");
		}

		if (pool.getLast().array().size() + data.cp.array().size() >= 0xFFFF) {
			pool.add(new ConstantPool());
		}
		table.putInt(data.name, this.data.wIndex() | (pool.size()-1) << 24);

		data.parsed();
		data.cp = null;
		data.getBytesNoCp(this.data.putInt(data.version), pool.getLast());
	}

	public void setVersion(String version) {this.version = version;}
	public void serialize(DynByteBuf buf) {
		buf.putAscii("LAVASYM3").putVUIUTF(version);
		buf.putVUInt(pool.size());
		for (int i = 0; i < pool.size(); i++) {
			pool.get(i).write(buf, false);
		}
		buf.putVUInt(table.size());
		for (var entry : table.selfEntrySet()) {
			buf.putVUIUTF(entry.k);
			buf.putVUInt(entry.v);
		}
		buf.put(data);
	}

	public LibraryCache(DynByteBuf buf) {
		if (!buf.readAscii(8).equals("LAVASYM3")) throw new IllegalStateException("Magic number error");
		version = buf.readVUIUTF();

		var len = buf.readVUInt();
		pool = new SimpleList<>(len);
		for (int i = 0; i < len; i++) {
			var p = new ConstantPool();
			p.read(buf, ConstantPool.CHAR_STRING);
			pool.add(p);
		}

		len = buf.readVUInt();
		table.ensureCapacity(len);
		for (int i = 0; i < len; i++) {
			table.putInt(buf.readVUIUTF(), buf.readVUInt());
		}

		data = new ByteList(buf.toByteArray());
		module = Library.super.moduleName();
	}

	@Override public String versionCode() {return module+'-'+version;}
	@Override public String moduleName() {return module;}
	@Override public Collection<String> content() {return table.keySet();}
	@Override
	public ConstantData get(CharSequence name) {
		int offset = table.getOrDefault(name, -1);
		if (offset < 0) return null;

		var cp = pool.get(offset >>> 24);
		offset &= 0xFFFFFF;

		var slice = data.slice(offset, data.wIndex() - offset);
		return Parser.parseConstantsNoCp(slice, cp, slice.readInt());
	}
}