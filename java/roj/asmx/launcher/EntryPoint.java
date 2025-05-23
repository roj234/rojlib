package roj.asmx.launcher;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.CodeSource;
import java.security.SecureClassLoader;
import java.util.Enumeration;
import java.util.function.Function;

/**
 * @author Roj234
 * @since 2023/8/4 15:41
 */
public class EntryPoint extends SecureClassLoader {
	public final ClassLoader PARENT = EntryPoint.class.getClassLoader();

	EntryPoint() {super(null);}
	static {ClassLoader.registerAsParallelCapable();}

	public final Class<?> defineClassA(String name, byte[] b, int off, int len, CodeSource cs) throws ClassFormatError {return defineClass(name, b, off, len, cs);}
	public final Class<?> defineClassB(String name, byte[] b, int off, int len) throws ClassFormatError {return defineClass(name, b, off, len, EntryPoint.class.getProtectionDomain());}

	@Override
	public Package definePackage(String name, String specTitle, String specVersion, String specVendor, String implTitle, String implVersion, String implVendor, URL sealBase) {
		return super.definePackage(name, specTitle, specVersion, specVendor, implTitle, implVersion, implVendor, sealBase); }
	@Override public Package getPackage(String name) {return super.getPackage(name);}
	final public Class<?> findLoadedClass1(String name) {return super.findLoadedClass(name);}

	protected Class<?> findClass(String name) throws ClassNotFoundException {
		if (classFinder == null) {
			// 这样，只有一个类(EntryPoint)是AppClassLoader加载的
			if (name.equals("roj.asmx.launcher.EntryPoint")) return EntryPoint.class;

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

	@Override public URL getResource(String name) {return PARENT.getResource(name);}
	@Override public Enumeration<URL> getResources(String name) throws IOException {return PARENT.getResources(name);}
	@Override public final InputStream getResourceAsStream(String name) {return resourceFinder != null ? resourceFinder.apply(name) : getParentResource(name);}
	public InputStream getParentResource(String name) {return PARENT.getResourceAsStream(name);}

	// 这种设计是因为EntryPoint引用的任何类，都会由AppClassLoader加载
	// 如果引用具体类型，就会造成两个不同加载器的类无法转换
	// 类似的设计可以看VMInternal如何获取ReflectCompat的实例
	// public是因为不同加载器不能访问非public字段
	// (看原文)我现在可能理解了，因为这个东西设计复杂
	public static Function<String, Class<?>> classFinder;
	public static Function<String, InputStream> resourceFinder;
	public static Runnable mainInvoker;

	public static void main(String[] args) throws Exception, Throwable {
		if (args.length == 0) args = new String[] {"-t", "roj.plugin.PanTweaker", "roj.plugin.Panger"};

		Class.forName("roj.asmx.launcher.Bootstrap", true, new EntryPoint())
			 .getMethod("boot", String[].class)
			 .invoke(null, (Object) args);

		mainInvoker.run();
	}
}