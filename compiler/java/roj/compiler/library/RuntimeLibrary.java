package roj.compiler.library;

import org.jetbrains.annotations.Nullable;
import roj.archive.zip.ZipEntry;
import roj.archive.zip.ZipFile;
import roj.archive.zip.ZipPacker;
import roj.asm.ClassNode;
import roj.asm.ClassView;
import roj.asm.Opcodes;
import roj.asm.attr.Attribute;
import roj.asm.attr.ModuleAttribute;
import roj.ci.annotation.Public;
import roj.collect.ArrayList;
import roj.collect.HashMap;
import roj.collect.HashSet;
import roj.compiler.LavaCompiler;
import roj.compiler.resolve.Resolver;
import roj.io.IOUtil;
import roj.io.XDataInput;
import roj.io.XDataInputStream;
import roj.reflect.Bypass;
import roj.util.ByteList;
import roj.util.DynByteBuf;
import roj.util.JVM;
import roj.util.OperationDone;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * @author Roj234
 * @since 2024/10/10 1:01
 */
public final class RuntimeLibrary extends ClassLoaderLibrary {
	@Public
	private interface J9 {
		//jdk.internal.jrtfs.SystemImage
		boolean modulesImageExists();
		Object moduleImageFile();
		//jdk.internal.jimage.ImageReader
		Object open(Object path);
		String[] getEntryNames(Object imageReader);
		byte[] getResource(Object imageReader, String name);
	}

	private static final List<Library> MODULES = init();
	private static List<Library> init() {
		File symTable = Resolver.getCacheDirectory("LocalSymbolCache.zip");
		if (symTable == null) return Collections.emptyList();

		var prop = System.getProperties();
		var version = prop.getProperty("java.vendor", "")+"-"+prop.getProperty("java.vm.version", "")+"-"+prop.getProperty("os.arch", "")+"-"+prop.getProperty("os.name", "");

		if (symTable.isFile()) {
			try {
				return loadSymbolCache(symTable, version);
			} catch (Exception ignored) {}
		}

		try {
			if (JVM.VERSION <= 8) {
				File file = IOUtil.getJar(Object.class);
				return Collections.singletonList(new JarLibrary(file));
			}

			LavaCompiler.debugLogger().debug("正在初始化LibraryRuntimeJ9Plus...");

			Class<?> _jir = Class.forName("jdk.internal.jimage.ImageReader");
			J9 j9 = Bypass.builder(J9.class)
						  .access(Class.forName("jdk.internal.jrtfs.SystemImage"), new String[] {"modulesImageExists", "moduleImageFile"}, new String[] {"modulesImageExists", "moduleImageFile"}, null)
						  .delegate_o(_jir, new String[] {"open"})
						  .delegate(_jir, new String[] {"getEntryNames", "getResource"})
						  .build();

			if (!j9.modulesImageExists()) throw new IllegalStateException("module image not exist");

			HashMap<String, RuntimeLibrary> caches = new HashMap<>();
			Function<String, RuntimeLibrary> newCache = RuntimeLibrary::new;
			HashMap<String, Set<String>> publicPackages = new HashMap<>();

			try (var ir = (AutoCloseable) j9.open(j9.moduleImageFile())) {
				Function<String, Set<String>> newPackageList = mod -> {
					byte[] resource = j9.getResource(ir, "/"+mod+"/module-info.class");
					if (resource == null) return null;

					var set = new HashSet<String>();
					var data = ClassNode.parseSkeleton(resource);
					ModuleAttribute module = data.getAttribute(data.cp, Attribute.Module);
					for (ModuleAttribute.Export export : module.exports) {
						if (export.to.isEmpty()) {
							set.add(export.pkg);
						}
					}

					return set;
				};

				for (String path : j9.getEntryNames(ir)) {
					if (path.startsWith("/package/") || path.startsWith("/modules/") || !path.endsWith(".class")) continue;

					int i = path.indexOf('/', 1);
					var moduleName = path.substring(1, i);
					var className = path.substring(i + 1, path.length() - 6);

					var cache = caches.computeIfAbsent(moduleName, newCache);
					cache.classNamesAll.add(className);

					Set<String> packageList = publicPackages.computeIfAbsent(moduleName, newPackageList);
					if (packageList != null) {
						int packageIndex = className.lastIndexOf('/');
						if (packageIndex < 0) continue; // module-info itself
						if (!packageList.contains(className.substring(0, packageIndex))) continue;
					}

					if (!className.contains("$")) {
						var in = ClassView.parse(DynByteBuf.wrap(j9.getResource(ir, path)), false);
						if ((in.modifier & Opcodes.ACC_PUBLIC) == 0) continue;

						cache.classNamesExported.add(className);
					}
				}
			}

			var tmp = new ZipPacker(symTable);
			var ob = new ByteList.ToStream(tmp);

			tmp.setComment(version);
			for (var cache : caches.values()) {
				if (cache.classNamesExported.isEmpty()) continue;

				tmp.beginEntry(new ZipEntry(cache.module));
				cache.serialize(ob);
				ob.flush();
			}
			ob.close();

			return loadSymbolCache(symTable, version);
		} catch (Exception e) {
			LavaCompiler.debugLogger().error("RuntimeSymbolCache初始化失败", e);
			return Collections.emptyList();
		}
	}
	private static List<Library> loadSymbolCache(File symTable, String version) throws IOException {
		List<Library> modules = new ArrayList<>();

		try (var zf = new ZipFile(symTable)) {
			if (!version.equals(zf.getCommentString())) {
				LavaCompiler.debugLogger().debug("JVM更改了，作废本地符号缓存");
				throw OperationDone.INSTANCE;
			}

			for (ZipEntry entry : zf.entries()) {
				try (var in = XDataInputStream.wrap(zf.getInputStream(entry))) {
					modules.add(new RuntimeLibrary(entry.getName(), in));
				} catch (Exception e) {
					IOUtil.closeSilently(zf);
					throw e;
				}
			}
		}

		return modules;
	}

	public static final Library ALL_MODULES = getSystemModules(Collections.emptyList());
	public static Library getSystemModules(Collection<String> moduleNames) {
		var lib = new CombinedLibrary();
		for (Library module : MODULES) {
			if (moduleNames.isEmpty() || moduleNames.contains(module.moduleName())) {
				lib.addLibrary(module);
			}
		}
		return lib;
	}

	private final String module;
	private final HashSet<String> classNamesAll = new HashSet<>(), classNamesExported = new HashSet<>();

	public RuntimeLibrary(String module) {super(null);this.module = module;}
	public RuntimeLibrary(String module, XDataInput buf) throws IOException {
		super(null);
		this.module = module;

		var len = buf.readVUInt();
		classNamesExported.ensureCapacity(len);
		for (int i = 0; i < len; i++) classNamesExported.add(buf.readVUIUTF());

		len = buf.readVUInt();
		classNamesAll.ensureCapacity(classNamesExported.size() + len);
		classNamesAll.addAll(classNamesExported);
		for (int i = 0; i < len; i++) classNamesAll.add(buf.readVUIUTF());
	}
	private void serialize(DynByteBuf buf) {
		buf.putVUInt(classNamesExported.size());
		for (var className : classNamesExported) buf.putVUIUTF(className);

		buf.putVUInt(classNamesAll.size() - classNamesExported.size());
		for (var className : classNamesAll) {
			if (!classNamesExported.contains(className))
				buf.putVUIUTF(className);
		}
	}

	@Override public String moduleName() {return module;}
	@Override public Collection<String> indexedContent() {return classNamesAll;}

	@Override public void exportedContent(@Nullable String moduleName, Consumer<String> callback) {classNamesExported.forEach(callback);}
	@Override public ClassNode get(CharSequence name) {
		//if (!classNamesAll.contains(name)) return null;

		var data = super.get(name);
		if (data != null) Library.removeUnrelatedAttribute(data);
		else LavaCompiler.debugLogger().warn("无法读取模块{}的类{}", module, name);
		return data;
	}
}