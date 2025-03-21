package roj.asmx.launcher;

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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * @author Roj234
 * @since 2023/8/4 0004 15:41
 */
public final class EntryPointM extends EntryPoint {
	static final Module MyModule = EntryPointM.class.getModule();

	EntryPointM() {super();}
	static {ClassLoader.registerAsParallelCapable();}

	@Override
	public InputStream getParentResource(String name) {
		try {
			return MyModule.getResourceAsStream(name);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	protected Class<?> findClass(String name) throws ClassNotFoundException {
		// 这样，只有一个类(EntryPoint)是AppClassLoader加载的
		if (name.equals("roj.asmx.launcher.EntryPoint")) return EntryPoint.class;
		if (name.equals("roj.asmx.launcher.EntryPointM")) return EntryPointM.class;

		if (classFinder == null) {
			try (InputStream in = getParentResource(name.replace('.', '/').concat(".class"))) {
				if (in != null) {
					byte[] b = in.readAllBytes();
					return defineClass(name, b, 0, b.length);
				}
			} catch (IOException ignored) {}
			throw new ClassNotFoundException(name);
		}
		return classFinder.apply(name);
	}

	// 说实话，我感觉我的用法不对头
	private static MethodHandle newModule, implAddReads, implAddExports, newModuleLayer;
	private static VarHandle getMap;

	@SuppressWarnings("unchecked")
	public static Module defineSubModule(ModuleDescriptor descriptor, ModuleLayer parentLayer, ClassLoader cl, URI uri) throws Throwable {
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

	@SuppressWarnings("unchecked")
	public static void main(String[] args) throws Throwable {
		if (args.length == 0) args = new String[] {"-t", "roj.plugin.PanTweaker", "roj.plugin.Panger"};

		Field f = Unsafe.class.getDeclaredField("theUnsafe");
		f.setAccessible(true);
		Unsafe uu = (Unsafe) f.get(null);

		Field implLookup = MethodHandles.Lookup.class.getDeclaredField("IMPL_LOOKUP");
		var ImplLookup = (MethodHandles.Lookup) uu.getObject(uu.staticFieldBase(implLookup), uu.staticFieldOffset(implLookup));

		Module myModule = EntryPointM.class.getModule();

		EntryPointM classLoader = new EntryPointM();

		ModuleDescriptor desc = ModuleDescriptor.newOpenModule("roj.core").packages(myModule.getPackages()).build();

		newModule = ImplLookup.findConstructor(Module.class, MethodType.methodType(void.class, ModuleLayer.class, ClassLoader.class, ModuleDescriptor.class, URI.class));
		newModuleLayer = ImplLookup.findConstructor(ModuleLayer.class, MethodType.methodType(void.class, Configuration.class, List.class, Function.class));
		Module delegatingModule = (Module) newModule.invoke(ModuleLayer.boot(), classLoader, desc, URI.create("roj://core"));

		implAddReads = ImplLookup.findVirtual(Module.class, "implAddReads", MethodType.methodType(void.class, Module.class, boolean.class));
		implAddExports = ImplLookup.findVirtual(Module.class, "implAddExports", MethodType.methodType(void.class, String.class, Module.class));
		getMap = ImplLookup.findVarHandle(ModuleLayer.class, "nameToModule", Map.class);
		((Map<String, Module>) getMap.get(myModule.getLayer())).put("roj.core", delegatingModule);

		for (String package_ : myModule.getPackages()) {
			implAddExports.invoke(myModule, package_, delegatingModule);
		}

		implAddReads.invoke(delegatingModule, myModule, true);

		for (ModuleDescriptor.Requires require : myModule.getDescriptor().requires()) {
			doAddReads(myModule.getLayer(), delegatingModule, require.name());
		}

		/*ImplLookup.findVarHandle(Module.class, "reads", Set.class).set(delegatingModule, new AbstractSet<Object>() {
			@Override public Iterator<Object> iterator() {return Collections.emptyIterator();}
			@Override public int size() {return 1;}
			@Override public boolean contains(Object o) {return true;}
		});*/

		Class.forName("roj.asmx.launcher.Bootstrap", true, classLoader)
			 .getMethod("boot", String[].class)
			 .invoke(null, (Object) args);

		mainInvoker.run();
	}
}