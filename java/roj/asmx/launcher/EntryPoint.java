package roj.asmx.launcher;

import roj.text.logging.Level;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.security.CodeSigner;
import java.security.CodeSource;
import java.security.SecureClassLoader;
import java.util.Enumeration;
import java.util.function.Function;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import static roj.asmx.launcher.Bootstrap.LOGGER;

/**
 * @author Roj234
 * @since 2023/8/4 0004 15:41
 */
public final class EntryPoint extends SecureClassLoader {
	public static final boolean EXPERIMENTAL_FAST_ZIP = true;
	public final ClassLoader PARENT = getClass().getClassLoader();

	public EntryPoint() { super(null); }

	public final Class<?> defineClassA(String name, byte[] b, int off, int len, CodeSource cs) throws ClassFormatError { return defineClass(name, b, off, len, cs); }
	public final Class<?> defineClassB(String name, byte[] b, int off, int len) throws ClassFormatError { return defineClass(name, b, off, len, EntryPoint.class.getProtectionDomain()); }

	@Deprecated
	public final CodeSource getCodeSource(URL url, String name, URLConnection conn) throws IOException {
		int dot = name.lastIndexOf('.');
		CodeSigner[] signers = null;

		if (dot > -1) {
			String pkgName = name.substring(0, dot);
			Package pkg = getPackage(pkgName);

			if (conn instanceof JarURLConnection) {
				JarURLConnection juc = (JarURLConnection) conn;
				Manifest man = juc.getManifest();
				signers = juc.getJarEntry().getCodeSigners();

				if (pkg == null) {
					if (man != null) {
						//definePackage(pkgName, man, url);
					} else {
						definePackage(pkgName, null, null, null, null, null, null, null);
					}
				} else if (pkg.isSealed() && !pkg.isSealed(juc.getJarFileURL())) {
					LOGGER.log(Level.WARN,  "{} is trying to seal sealed path {}", null, juc.getJarFile().getName(), pkgName);
				} else if (man != null && isSealed(pkgName, man)) {
					LOGGER.log(Level.WARN,  "{} has a security seal for path {}, but that path is defined and not secure", null, juc.getJarFile().getName(), pkgName);
				}
			} else {
				if (pkg == null) {
					definePackage(pkgName, null, null, null, null, null, null, null);
				} else if (pkg.isSealed()) {
					LOGGER.log(Level.WARN,  "{} is defining elements for sealed path {}", null, conn.getURL(), pkgName);
				}
			}
		}

		return new CodeSource(null, signers);
	}
	private static boolean isSealed(String name, Manifest man) {
		Attributes attr = man.getAttributes(name.replace('.', '/').concat("/"));
		String sealed = null;
		if (attr != null) {
			sealed = attr.getValue(Attributes.Name.SEALED);
		}
		if (sealed == null) {
			if ((attr = man.getMainAttributes()) != null) {
				sealed = attr.getValue(Attributes.Name.SEALED);
			}
		}
		return "true".equalsIgnoreCase(sealed);
	}

	protected final Class<?> findClass(String name) throws ClassNotFoundException {
		if (actualLoader == null) {
			// 设置唯一一个由EntryPoint加载的点(下面boot)好处是只有一个类是AppClassLoader加载的
			// (但凡JVM给我一个全局的getLoadedClass都不至于写成这鬼样子)
			if (name.equals("roj.asmx.launcher.EntryPoint")) return getClass();

			try (InputStream in = PARENT.getResourceAsStream(name.replace('.', '/').concat(".class"))) {
				if (in != null) {
					byte[] b = in.readAllBytes();
					return defineClass(name, b, 0, b.length);
				}
			} catch (IOException ignored) {}
			throw new ClassNotFoundException(name);
		}
		return actualLoader.apply(name);
	}

	@Override
	public URL getResource(String name) { return PARENT.getResource(name); }
	@Override
	public Enumeration<URL> getResources(String name) throws IOException { return PARENT.getResources(name); }
	@Override
	public InputStream getResourceAsStream(String name) { return PARENT.getResourceAsStream(name); }

	// 这种设计类似SharedSecrets, 不过是因为
	// EntryPoint加载的任何类，除非要求，否则都会由AppClassLoader加载
	// 为了防止不同加载器的类冲突，只能用这种类似callback的写法
	// public是因为即使是同一个包，不同加载器，不能访问package-private或protected字段
	// 在这里又无法使用DirectAccessor
	// 另外，它支持Java8-17 (不是很理解forge为什么用JS)
	public static Function<String, Class<?>> actualLoader;
	public static Runnable loaderInst;

	public static void main(String[] args) throws Exception {
		if (args.length == 0) args = new String[] {"roj.platform.DefaultPluginSystem"};

		Method m = Class.forName("roj.asmx.launcher.Bootstrap", true, new EntryPoint()).getMethod("boot", String[].class);
		m.invoke(null, (Object) args);

		// 这也算回调
		// 更多离谱的回调方式请看roj.reflect.Java9Compat
		loaderInst.run();
	}
}