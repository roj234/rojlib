package roj.asmx.launcher.boot;

import roj.ci.annotation.ReplaceConstant;
import sun.misc.Unsafe;

import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.lang.module.Configuration;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.URL;
import java.util.*;
import java.util.function.Function;

/**
 * @author Roj234
 * @since 2023/8/4 15:41
 */
@ReplaceConstant
public final class MainM extends Main {
	static final Module MyModule = MainM.class.getModule();

	MainM() {super();}
	static {ClassLoader.registerAsParallelCapable();}

	@Override
	public InputStream getInitialResource(String name) {
		try {
			return MyModule.getResourceAsStream(name);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	// 说实话，我感觉我的用法不对头
	private static MethodHandle newModule, implAddReads, implAddExports, newModuleLayer;
	private static VarHandle getMap;

	@SuppressWarnings("unchecked")
	public static Module defineSubModule(ModuleDescriptor descriptor, ModuleLayer parentLayer, ClassLoader cl, URI uri) throws Throwable {
		parentLayer = ModuleLayer.boot();
		Configuration configuration = Configuration.resolveAndBind(ModuleFinder.of(), Collections.singletonList(parentLayer.configuration()), ModuleFinder.of(), Collections.emptyList());
		var newLayer = (ModuleLayer)newModuleLayer.invoke(configuration, Collections.singletonList(parentLayer), null);
		Module m = (Module) newModule.invoke(newLayer, cl, descriptor, uri);
		((Map<String, Module>) getMap.get(m.getLayer())).put(m.getName(), m);
		return m;
	}
	public static void doAddReads(ModuleLayer layer, Module module, String require) throws Throwable {
		Module readModule = layer.findModule(require).orElse(null);
		if (readModule != null) {
			implAddReads.invoke(module, readModule, true);
		}
	}
	public static void doAddReads(Module module, Module require) throws Throwable {
		implAddReads.invoke(module, require, true);
	}

	@SuppressWarnings("unchecked")
	public static void main(String[] args) throws Throwable {
		Field f = Unsafe.class.getDeclaredField("theUnsafe");
		f.setAccessible(true);
		Unsafe uu = (Unsafe) f.get(null);

		Field implLookup = MethodHandles.Lookup.class.getDeclaredField("IMPL_LOOKUP");
		var ImplLookup = (MethodHandles.Lookup) uu.getObject(uu.staticFieldBase(implLookup), uu.staticFieldOffset(implLookup));

		newModule = ImplLookup.findConstructor(Module.class, MethodType.methodType(void.class, ModuleLayer.class, ClassLoader.class, ModuleDescriptor.class, URI.class));
		newModuleLayer = ImplLookup.findConstructor(ModuleLayer.class, MethodType.methodType(void.class, Configuration.class, List.class, Function.class));
		implAddReads = ImplLookup.findVirtual(Module.class, "implAddReads", MethodType.methodType(void.class, Module.class, boolean.class));
		implAddExports = ImplLookup.findVirtual(Module.class, "implAddExports", MethodType.methodType(void.class, String.class, Module.class));
		getMap = ImplLookup.findVarHandle(ModuleLayer.class, "nameToModule", Map.class);

		URL location = MainM.class.getProtectionDomain().getCodeSource().getLocation();
		MainM classLoader = new MainM();

		Set<String> packages = ModuleInfo.getPackages();
		var imports = new HashSet<String>();
		// TODO specify in config ?
		imports.add("java.base");
		imports.add("java.desktop");
		imports.add("java.sql");
		imports.add("java.management");
		imports.add("jdk.unsupported");

		ModuleDescriptor desc = ModuleDescriptor.newOpenModule("${project_name}").packages(packages).build();

		Module delegatingModule = (Module) newModule.invoke(null, classLoader, desc, location.toURI());
		for (Module module : ModuleLayer.boot().modules()) {
			if (imports.contains(module.getName())) {
				doAddReads(delegatingModule, module);
			}
		}
		doAddReads(delegatingModule, MainM.class.getModule());

		for (String pkg : packages) {
			// force sealed
			classLoader.definePackage(
					pkg,
					null,
					null,
					null,
					null,
					null,
					null,
					location
			);
		}

		Class<?> loader = classLoader.findClass("roj.asmx.launcher.Loader");
		MethodHandles.lookup().findStatic(loader, "init", MethodType.methodType(void.class, String[].class)).invokeExact(args);
		main.run();
	}
}