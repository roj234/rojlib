package roj.asmx.launcher.boot;

import roj.util.function.ExceptionalFunction;

import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.URL;
import java.security.ProtectionDomain;
import java.security.SecureClassLoader;
import java.util.Enumeration;
import java.util.function.Function;

/**
 * @author Roj234
 * @since 2023/8/4 15:41
 */
public class Main extends SecureClassLoader {
	public static final ClassLoader PARENT = Main.class.getClassLoader();

	public final ProtectionDomain protectionDomain;
	Main() {
		super(null);
		var mypd = Main.class.getProtectionDomain();
		protectionDomain = new ProtectionDomain(mypd.getCodeSource(), mypd.getPermissions(), this, mypd.getPrincipals());
	}

	public final Class<?> defineClassA(String name, byte[] b, int off, int len, ProtectionDomain pd) throws ClassFormatError {return defineClass(name, b, off, len, pd);}

	@Override
	public Package definePackage(String name, String specTitle, String specVersion, String specVendor, String implTitle, String implVersion, String implVendor, URL sealBase) {
		return super.definePackage(name, specTitle, specVersion, specVendor, implTitle, implVersion, implVendor, sealBase); }
	@Override public Package getPackage(String name) {return super.getPackage(name);}
	final public Class<?> findLoadedClass1(String name) {return super.findLoadedClass(name);}

	//@Override protected Object getClassLoadingLock(String className) {return className;}

	protected Class<?> findClass(String name) throws ClassNotFoundException {
		if (classFinder == null) {
			// 这样，只有一个类(Main)是AppClassLoader加载的
			if (name.startsWith("roj.asmx.launcher.boot.")) return PARENT.loadClass(name);

			try (InputStream in = getInitialResource(name.replace('.', '/').concat(".class"))) {
				if (in != null) {
					byte[] b = in.readAllBytes();
					return defineClass(name, b, 0, b.length, protectionDomain);
				}
			} catch (IOException ignored) {}
			throw new ClassNotFoundException(name);
		}
		return classFinder.apply(name);
	}

	@Override public URL getResource(String name) {return PARENT.getResource(name);}
	@Override public Enumeration<URL> getResources(String name) throws IOException {return PARENT.getResources(name);}
	@Override public final InputStream getResourceAsStream(String name) {return resourceFinder != null ? resourceFinder.apply(name) : getInitialResource(name);}
	public InputStream getInitialResource(String name) {return PARENT.getResourceAsStream(name);}

	// 这种设计是因为Main引用的任何类，都会由AppClassLoader加载
	// 如果引用具体类型，就会造成两个不同加载器的类无法转换
	// 类似的设计可以看Reflection如何加载实例
	// public是因为不同加载器不能访问非public字段
	// (看原文)我现在可能理解了，因为这个东西设计复杂
	public static ExceptionalFunction<String, Class<?>, ClassNotFoundException> classFinder;
	public static Function<String, InputStream> resourceFinder;
	public static Runnable main;

	public static void main(String[] args) throws Throwable {
		Class<?> loader = new Main().findClass("roj.asmx.launcher.Loader");
		MethodHandles.lookup().findStatic(loader, "init", MethodType.methodType(void.class, String[].class)).invokeExact(args);
		main.run();
	}
}