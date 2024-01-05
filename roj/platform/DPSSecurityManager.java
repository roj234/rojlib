package roj.platform;

import roj.asm.Parser;
import roj.asm.cp.Constant;
import roj.asm.cp.CstClass;
import roj.asm.cp.CstUTF;
import roj.asm.type.Desc;
import roj.asm.type.TypeHelper;
import roj.asm.util.ClassUtil;
import roj.asm.util.Context;
import roj.asmx.MethodHook;
import roj.collect.*;
import roj.net.URIUtil;
import roj.reflect.ClassDefiner;
import roj.reflect.ILSecurityManager;
import roj.reflect.ReflectionUtils;
import roj.text.logging.Logger;
import roj.util.ArrayCache;
import roj.util.ByteList;
import roj.util.Helpers;
import sun.misc.Unsafe;

import java.io.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.security.ProtectionDomain;
import java.util.Arrays;
import java.util.Iterator;

/**
 * @author Roj234
 * @since 2023/8/4 0004 15:36
 */
public class DPSSecurityManager extends MethodHook {
	public static final TrieTreeSet
		GlobalClassBlackList = new TrieTreeSet("roj/platform/DPS"),
		GlobalFileWhiteList = new TrieTreeSet(),
		GlobalReflectWhiteList = new TrieTreeSet();

	static final Logger LOGGER = Logger.getLogger("DPS/Security");

	DPSSecurityManager() {
		hooks.put(new Desc("roj/reflect/ReflectionUtils", "u", "Lsun/misc/Unsafe;"),
			new Desc("roj/platform/DPSSecurityManager", "ReflectionUtils_getUnsafe", "(Ljava/lang/Class;)Lsun/misc/Unsafe;", 2));
	}

	static final class SecureClassDefineIL extends ILSecurityManager {
		@Override
		public ByteList checkDefineClass(String name, ByteList buf) {
			int i = 3;
			Class<?> caller;
			do {
				caller = ReflectionUtils.getCallerClass(i++);
			} while (caller == ClassDefiner.class);

			PluginDescriptor pd = DefaultPluginSystem.PM.getPluginDescriptor(caller);
			return preDefineHook(name, pd, buf);
		}

		// 0: TraceUtil
		// 1: SecureClassDefineIL
		// 2: roj.reflect.XX
		// 3: real caller
		public boolean checkAccess(Field f) { return filterField(f, false, ReflectionUtils.getCallerClass(3)) != null; }
		public boolean checkInvoke(Method m) { return filterMethod(m, false, ReflectionUtils.getCallerClass(3)) != null; }
		public boolean checkConstruct(Constructor<?> c) { return filterConstructor(c, false, ReflectionUtils.getCallerClass(3)) != null; }

		public void checkAccess(String owner, String name, String desc) {
			Desc d = ClassUtil.getInstance().sharedDC;
			d.owner = owner;
			d.name = name;
			d.param = desc;

			Class<?> caller = ReflectionUtils.getCallerClass(3);
			Object v = ban(d, caller);
			if (v == IntMap.UNDEFINED) throw new SecurityException(d+" is blocked");
		}

		public void filterMethods(MyHashSet<Method> methods) {
			Class<?> caller = ReflectionUtils.getCallerClass(3);
			for (Iterator<Method> itr = methods.iterator(); itr.hasNext(); ) {
				if (filterMethod(itr.next(), false, caller) == null) itr.remove();
			}
		}
		public void filterFields(SimpleList<Field> fields) {
			Class<?> caller = ReflectionUtils.getCallerClass(3);
			for (Iterator<Field> itr = fields.iterator(); itr.hasNext(); ) {
				if (filterField(itr.next(), false, caller) == null) itr.remove();
			}
		}
	}

	// region 反射保护
	@RealDesc(value = "java/lang/Class.forName(Ljava/lang/String;)Ljava/lang/Class;", callFrom = true)
	public static Class<?> hook_static_forName(String name, Class<?> caller) throws Exception { return checkClass(name, true, caller.getClassLoader(), caller); }
	@RealDesc(value = "java/lang/Class.forName(Ljava/lang/String;ZLjava/lang/ClassLoader;)Ljava/lang/Class;", callFrom = true)
	public static Class<?> hook_static_forName(String name, boolean init, ClassLoader loader, Class<?> caller) throws Exception { return checkClass(name, init, loader, caller); }

	private static final Method[] EMPTY_METHODS = new Method[0];
	private static final Field[] EMPTY_FIELDS = new Field[0];
	private static final Constructor<?>[] EMPTY_CONSTRUCTORS = new Constructor<?>[0];

	public static Method hook_callFrom_getMethod(Class<?> c, String name, Class<?>[] param, Class<?> caller) throws Exception { return filterMethod(c.getMethod(name, param), true, caller); }
	public static Method hook_callFrom_getDeclaredMethod(Class<?> c, String name, Class<?>[] param, Class<?> caller) throws Exception { return filterMethod(c.getDeclaredMethod(name, param), true, caller); }
	public static Method[] hook_callFrom_getMethods(Class<?> c, Class<?> caller) { return filterMethods(c.getMethods(), caller); }
	public static Method[] hook_callFrom_getDeclaredMethods(Class<?> c, Class<?> caller) { return filterMethods(c.getDeclaredMethods(), caller); }
	private static Method[] filterMethods(Method[] a, Class<?> caller) {
		SimpleList<Method> list = SimpleList.asModifiableList(a);
		for (int i = list.size()-1; i >= 0; i--) {
			Method f = filterMethod(list.get(i), false, caller);
			if (f == null) {
				list.remove(i);
				a = EMPTY_METHODS;
			} else list.set(i, f);
		}
		return list.toArray(a);
	}
	private static Method filterMethod(Method m, boolean _throw, Class<?> caller) {
		Desc d = ClassUtil.getInstance().sharedDC;
		d.owner = m.getDeclaringClass().getName().replace('.', '/');
		d.name = m.getName();
		d.param = TypeHelper.class2asm(m.getParameterTypes(), m.getReturnType());

		Object v = ban(d, caller);
		if (v == null) return m;
		if (v == IntMap.UNDEFINED) {
			if (_throw) Helpers.athrow(new NoSuchMethodException(m.getDeclaringClass().getName()+'.'+m.getName()+argumentTypesToString(m.getParameterTypes())));
			return null;
		}
		return (Method) v;
	}

	private static final Method INVOKE, NEWINSTANCE;
	static {
		try {
			INVOKE = Method.class.getDeclaredMethod("invoke", Object.class, Object[].class);
			NEWINSTANCE = Constructor.class.getDeclaredMethod("newInstance", Object[].class);
		} catch (NoSuchMethodException e) {
			throw new RuntimeException(e);
		}
	}

	public static Object hook_callFrom_invoke(Method m, Object inst, Object[] value, Class<?> caller) throws Exception {
		MyHashSet<Object[]> arr = null;
		while (m.equals(INVOKE)) {
			m = (Method) inst;
			inst = value[0];

			// 防止可能的DoS漏洞
			if (arr == null) arr = new MyHashSet<>(Hasher.identity());
			if (!arr.add(value)) throw new StackOverflowError();

			value = (Object[]) value[1];
		}

		if (m.equals(NEWINSTANCE)) return hook_callFrom_newInstance((Constructor<?>) inst, value, caller);

		return checkInvoke(m, inst, value, caller);
	}

	public static Field hook_callFrom_getField(Class<?> c, String name, Class<?> caller) throws Exception { return filterField(c.getField(name), true, caller); }
	public static Field hook_callFrom_getDeclaredField(Class<?> c, String name, Class<?> caller) throws Exception { return filterField(c.getDeclaredField(name), true, caller); }
	public static Field[] hook_callFrom_getFields(Class<?> c, Class<?> caller) { return filterFields(c.getFields(), caller); }
	public static Field[] hook_callFrom_getDeclaredFields(Class<?> c, Class<?> caller) { return filterFields(c.getDeclaredFields(), caller); }
	private static Field[] filterFields(Field[] a, Class<?> caller) {
		SimpleList<Field> list = SimpleList.asModifiableList(a);
		for (int i = list.size()-1; i >= 0; i--) {
			Field f = filterField(list.get(i), false, caller);
			if (f == null) {
				list.remove(i);
				a = EMPTY_FIELDS;
			} else list.set(i, f);
		}
		return list.toArray(a);
	}
	private static Field filterField(Field f, boolean _throw, Class<?> caller) {
		Desc d = ClassUtil.getInstance().sharedDC;
		d.owner = f.getDeclaringClass().getName().replace('.', '/');
		d.name = f.getName();
		d.param = TypeHelper.class2asm(f.getType());

		Object v = ban(d, caller);
		if (v == null) return f;
		if (v == IntMap.UNDEFINED) {
			if (_throw) Helpers.athrow(new NoSuchFieldException(f.getName()));
			return null;
		}
		return (Field) v;
	}

	public static Constructor<?> hook_callFrom_getConstructor(Class<?> c, Class<?>[] param, Class<?> caller) throws Exception { return filterConstructor(c.getConstructor(param), true, caller); }
	public static Constructor<?> hook_callFrom_getDeclaredConstructor(Class<?> c, Class<?>[] param, Class<?> caller) throws Exception { return filterConstructor(c.getDeclaredConstructor(param), true, caller); }
	public static Constructor<?>[] hook_callFrom_getConstructors(Class<?> c, Class<?> caller) { return filterConstructors(c.getConstructors(), caller); }
	public static Constructor<?>[] hook_callFrom_getDeclaredConstructors(Class<?> c, Class<?> caller) { return filterConstructors(c.getDeclaredConstructors(), caller); }
	private static Constructor<?>[] filterConstructors(Constructor<?>[] a, Class<?> caller) {
		SimpleList<Constructor<?>> list = SimpleList.asModifiableList(a);
		for (int i = list.size()-1; i >= 0; i--) {
			Constructor<?> f = filterConstructor(list.get(i), false, caller);
			if (f == null) {
				list.remove(i);
				a = EMPTY_CONSTRUCTORS;
			} else list.set(i, f);
		}
		return list.toArray(a);
	}
	private static Constructor<?> filterConstructor(Constructor<?> c, boolean _throw, Class<?> caller) {
		Desc d = ClassUtil.getInstance().sharedDC;
		d.owner = c.getDeclaringClass().getName().replace('.', '/');
		d.name = "<init>";
		d.param = TypeHelper.class2asm(c.getParameterTypes(), void.class);

		Object v = ban(d, caller);
		if (v == null) return c;
		if (v == IntMap.UNDEFINED) {
			if (_throw) Helpers.athrow(new NoSuchMethodException(c.getDeclaringClass().getName()+".<init>"+argumentTypesToString(c.getParameterTypes())));
			return null;
		}
		return (Constructor<?>) v;
	}

	public static Method hook_callFrom_getEnclosingMethod(Class<?> c, Class<?> caller) {
		Method m = c.getEnclosingMethod();
		return m == null ? null : filterMethod(m, false, caller); }
	public static Constructor<?> hook_callFrom_getEnclosingConstructor(Class<?> c, Class<?> caller) {
		Constructor<?> c1 = c.getEnclosingConstructor();
		return c1 == null ? null : filterConstructor(c1, false, caller); }

	public static Object hook_callFrom_newInstance(Class<?> c, Class<?> caller) throws Exception {
		Desc d = ClassUtil.getInstance().sharedDC;
		d.owner = c.getDeclaringClass().getName().replace('.', '/');
		d.name = "<init>";
		d.param = "()V";

		Object v = ban(d, caller);
		if (v == null) return c.newInstance();
		if (v == IntMap.UNDEFINED) throw new InstantiationException(c.getName());
		return ((Constructor<?>) v).newInstance(ArrayCache.OBJECTS);
	}

	public static Class<?> hook_defineClass(ClassLoader cl, byte[] b, int off, int len) { return hook_defineClass(cl, null, b, off, len, null); }
	public static Class<?> hook_defineClass(ClassLoader cl, String name, byte[] b, int off, int len) { return hook_defineClass(cl, name, b, off, len, null); }
	public static Class<?> hook_defineClass(ClassLoader cl, String name, byte[] b, int off, int len, ProtectionDomain pd) { return hook_defineClass(cl, name, b, off, len, pd, cl.getClass()); }

	private static String argumentTypesToString(Class<?>[] types) {
		StringBuilder buf = new StringBuilder().append('(');
		for (int i = 0; i < types.length; i++) {
			if (i > 0) buf.append(", ");

			Class<?> c = types[i];
			buf.append((c == null) ? "null" : c.getName());
		}
		return buf.append(')').toString();
	}
	// endregion
	// region 文件访问保护 (不全) (这些函数可以放心的使用caller，因为DPSSelfProtect已经禁止调用这些函数)
	/* -- Attribute accessors -- */

	public static boolean hook_callFrom_canRead(File f, Class<?> caller) { return checkFileAccess(f, caller) && f.canRead(); }
	public static boolean hook_callFrom_canWrite(File f, Class<?> caller) { return checkFileAccess(f, caller) && f.canWrite(); }
	public static boolean hook_callFrom_exists(File f, Class<?> caller) { return checkFileAccess(f, caller) && f.exists(); }
	public static boolean hook_callFrom_isDirectory(File f, Class<?> caller) { return checkFileAccess(f, caller) && f.isDirectory(); }
	public static boolean hook_callFrom_isFile(File f, Class<?> caller) { return checkFileAccess(f, caller) && f.isFile(); }
	public static boolean hook_callFrom_isHidden(File f, Class<?> caller) { return checkFileAccess(f, caller) && f.isHidden(); }
	public static long hook_callFrom_lastModified(File f, Class<?> caller) { return checkFileAccess(f, caller) ? f.lastModified() : 0; }
	public static long hook_callFrom_length(File f, Class<?> caller) { return checkFileAccess(f, caller) ? f.length() : 0; }

	/* -- File operations -- */
	public static boolean hook_callFrom_createNewFile(File f, Class<?> caller) throws IOException { return checkFileAccess(f, caller) && f.createNewFile(); }

	public static boolean hook_callFrom_delete(File f, Class<?> caller) { return checkFileAccess(f, caller) && f.delete(); }
	public static void hook_callFrom_deleteOnExit(File f, Class<?> caller) { if (checkFileAccess(f, caller)) f.deleteOnExit(); }

	public static String[] hook_callFrom_list(File f, Class<?> caller) { return checkFileAccess(f, caller) ? f.list() : null; }
	public static String[] hook_callFrom_list(File f, FilenameFilter filter, Class<?> caller) { return checkFileAccess(f, caller) ? f.list(filter) : null; }

	public static File[] hook_callFrom_listFiles(File f, Class<?> caller) { return checkFileAccess(f, caller) ? f.listFiles() : null; }
	public static File[] hook_callFrom_listFiles(File f, FilenameFilter filter, Class<?> caller) { return checkFileAccess(f, caller) ? f.listFiles(filter) : null; }
	public static File[] hook_callFrom_listFiles(File f, FileFilter filter, Class<?> caller) { return checkFileAccess(f, caller) ? f.listFiles(filter) : null; }

	public static boolean hook_callFrom_mkdir(File f, Class<?> caller) { return checkFileAccess(f, caller) && f.mkdir(); }
	public static boolean hook_callFrom_mkdirs(File f, Class<?> caller) {return checkFileAccess(f, caller) && f.mkdirs(); }

	public static boolean hook_callFrom_renameTo(File f, File dest, Class<?> caller) { return checkFileAccess(f, caller) && checkFileAccess(dest, caller) && f.renameTo(dest); }

	public static boolean hook_callFrom_setLastModified(File f, long time, Class<?> caller) { return checkFileAccess(f, caller) && f.setLastModified(time); }
	public static boolean hook_callFrom_setReadOnly(File f, Class<?> caller) { return checkFileAccess(f, caller) && f.setReadOnly(); }
	public static boolean hook_callFrom_setWritable(File f, boolean writable, boolean ownerOnly, Class<?> caller) { return checkFileAccess(f, caller) && f.setWritable(writable, ownerOnly); }
	public static boolean hook_callFrom_setWritable(File f, boolean writable, Class<?> caller) { return hook_callFrom_setWritable(f, writable, true, caller); }
	public static boolean hook_callFrom_setReadable(File f, boolean readable, boolean ownerOnly, Class<?> caller) { return checkFileAccess(f, caller) && f.setReadable(readable, ownerOnly); }
	public static boolean hook_callFrom_setReadable(File f, boolean readable, Class<?> caller) { return hook_callFrom_setReadable(f, readable, true, caller); }
	public static boolean hook_callFrom_setExecutable(File f, boolean executable, boolean ownerOnly, Class<?> caller) { return checkFileAccess(f, caller) && f.setExecutable(executable, ownerOnly); }
	public static boolean hook_callFrom_setExecutable(File f, boolean executable, Class<?> caller) { return hook_callFrom_setExecutable(f, executable, true, caller); }
	public static boolean hook_callFrom_canExecute(File f, Class<?> caller) { return checkFileAccess(f, caller) && f.canExecute(); }

	/* -- Filesystem interface -- */
	@RealDesc(value = "java/io/File.createTempFile(Ljava/lang/String;Ljava/lang/String;Ljava/io/File;)Ljava/io/File;", callFrom = true)
	public static File hook_static_createTempFile(String prefix, String suffix, File directory, Class<?> caller) throws IOException {
		if (directory != null && !checkFileAccess(directory, caller)) throw new SecurityException("没有写入权限");
		return File.createTempFile(prefix, suffix, directory);
	}

	@RealDesc(value = "java/io/File.createTempFile(Ljava/lang/String;Ljava/lang/String;)Ljava/io/File;", callFrom = true)
	public static File hook_static_createTempFile(String prefix, String suffix, Class<?> caller) throws IOException { return hook_static_createTempFile(prefix, suffix, null, caller); }

	/* -- NIO -- */
	public static Path hook_callFrom_getPath(FileSystem fs, String first, String[] more, Class<?> caller) {
		Path path = fs.getPath(first, more);
		if (fs == FileSystems.getDefault() && !checkFileAccess(path.toFile(), caller))
			throw new SecurityException("权限不足");
		return path;
	}
	public static Path hook_callFrom_toPath(File f, Class<?> caller) {
		if (!checkFileAccess(f, caller)) throw new SecurityException("权限不足");
		return f.toPath();
	}

	/* -- URL -- */
	public static InputStream hook_callFrom_openStream(URL url, Class<?> caller) throws IOException {
		if (url.getProtocol().equals("file")) {
			if (!checkFileAccess(new File(URIUtil.decodeURI(url.getFile())), caller)) throw new SecurityException("没有读取权限");
		} else if (url.getProtocol().equals("jar")) {
			String spec = url.getFile();
			int separator = spec.indexOf("!/");
			if (separator == -1) throw new MalformedURLException("no !/ found in url spec:" + spec);

			if (!checkFileAccess(new File(URIUtil.decodeURI(spec.substring(0, separator))), caller))
				throw new SecurityException("没有读取权限");
		}
		return url.openStream();
	}

	/* -- ArgumentCapture -- */
	@RealDesc(value = {
		"java/io/RandomAccessFile.<init>(Ljava/io/File;)V",
		"java/io/FileInputStream.<init>(Ljava/io/File;)V",
		"java/io/FileOutputStream.<init>(Ljava/io/File;)V",
	}, callFrom = true)
	public static File hook_newInstance_f(File file, Class<?> caller) throws IOException {
		if (checkFileAccess(file, caller)) return file;
		throw new IOException("权限不足");
	}
	@RealDesc(value = {
		"java/io/RandomAccessFile.<init>(Ljava/lang/String;)V",
		"java/io/FileInputStream.<init>(Ljava/lang/String;)V",
		"java/io/FileOutputStream.<init>(Ljava/lang/String;)V",
	}, callFrom = true)
	public static String hook_newInstance_s(String file, Class<?> caller) throws IOException {
		if (checkFileAccess(file, caller)) return file;
		throw new IOException("权限不足");
	}
	// endregion
	// region 本地库保护
	@RealDesc(value = "java/lang/System.load(Ljava/lang/String;)V", callFrom = true)
	public static void hook_callFrom_load(String path, Class<?> caller) {
		PluginDescriptor pd = DefaultPluginSystem.PM.getPluginDescriptor(caller);
		if (!pd.loadNative) throw new SecurityException("loadNative权限未为"+pd+"开启");
		System.load(path);
	}
	@RealDesc(value = "java/lang/System.loadLibrary(Ljava/lang/String;)V", callFrom = true)
	public static void hook_callFrom_loadLibrary(String path, Class<?> caller) {
		PluginDescriptor pd = DefaultPluginSystem.PM.getPluginDescriptor(caller);
		if (!pd.loadNative) throw new SecurityException("loadNative权限未为"+pd+"开启");
		System.loadLibrary(path);
	}
	// endregion
	// region Unsafe
	public static Unsafe ReflectionUtils_getUnsafe(Class<?> caller) {
		PluginDescriptor pd = DefaultPluginSystem.PM.getPluginDescriptor(caller);
		if (!pd.accessUnsafe) throw new SecurityException("accessUnsafe权限未为"+pd+"开启");
		return ReflectionUtils.u;
	}
	// endregion
	// region 类定义
	@RealDesc(value = "roj/reflect/ClassDefiner.defineClass(Ljava/lang/ClassLoader;Ljava/lang/String;[BIILjava/security/ProtectDomain;)Ljava/lang/Class;", callFrom = true)
	public static Class<?> hook_defineClass(ClassLoader cl, String name, byte[] b, int off, int len, ProtectionDomain pd, Class<?> caller) {
		ByteList buf = new ByteList(Arrays.copyOfRange(b, off, off+len));

		PluginDescriptor pd1 = DefaultPluginSystem.PM.getPluginDescriptor(caller);
		buf = preDefineHook(name, pd1, buf);
		return ClassDefiner.defineClass(cl, name, buf.list, 0, buf.wIndex(), pd);
	}

	static ByteList preDefineHook(String name, PluginDescriptor pd1, ByteList buf) {
		if (!pd1.dynamicLoadClass) throw new SecurityException("dynamicLoadClass权限未为"+pd1+"开启");
		if (!pd1.skipCheck) {
			// 一个有趣的问题是，如果另一个线程异步修改这个数组？
			// 因为final了，所以就不用clone
			buf = new ByteList(buf.toByteArray());

			name = Parser.parseAccess(buf, false).name;
			DefaultPluginSystem.transform(name, buf);
		}
		return buf;
	}
	// endregion

	private static Object checkInvoke(Method m, Object inst, Object[] value, Class<?> caller) throws Exception {
		return m.invoke(inst, value);
	}
	public static Object hook_callFrom_newInstance(Constructor<?> c, Object[] value, Class<?> caller) throws Exception {
		return c.newInstance(value);
	}

	private static Class<?> checkClass(String name, boolean init, ClassLoader loader, Class<?> caller) throws Exception {
		Desc d = ClassUtil.getInstance().sharedDC;
		d.owner = name.replace('.', '/');
		d.name = "";
		d.param = "";

		Object result = ban(d, caller);
		if (result == IntMap.UNDEFINED) loader = null;

		return Class.forName(name, init, loader);
	}

	static boolean checkFileAccess(File file, Class<?> caller) { return checkFileAccess(file.getPath(), caller); }
	static boolean checkFileAccess(String path, Class<?> caller) {
		PluginDescriptor pd = DefaultPluginSystem.PM.getPluginDescriptor(caller);
		if (pd.skipCheck || GlobalFileWhiteList.strStartsWithThis(path) || pd.extraPath.strStartsWithThis(path)) {
			LOGGER.debug("允许读写 {}", path);
			return true;
		}
		LOGGER.debug("禁止读写 {}", path);
		return false;
	}

	private static Object ban(Desc d, Class<?> caller) {
		if (d.owner.equals("java/lang/reflect/Constructor") || d.owner.equals("java/lang/reflect/Method")) {
			// prevent getting internal fields
			if (d.param.indexOf('(') == -1) return IntMap.UNDEFINED;
		}

		if (d.owner.equals("sun/misc/Unsafe") || d.owner.equals("jdk/internal/misc/Unsafe"))
			return IntMap.UNDEFINED;

		if (d.owner.startsWith("roj/platform/") || d.owner.startsWith("roj/reflect/") || d.owner.startsWith("roj/mapper/"))
			return IntMap.UNDEFINED;

		PluginDescriptor pd = DefaultPluginSystem.PM.getPluginDescriptor(caller);
		if (pd.skipCheck || GlobalReflectWhiteList.contains(d.owner) || pd.reflectiveClass.contains(d.owner)) {
			LOGGER.debug("允许反射 {}", d);
			return null;
		}
		LOGGER.debug("禁止反射 {}", d);
		throw new SecurityException(d+"不在"+pd+"的reflectivePackage中");
	}

	@Override
	public boolean transform(String name, Context ctx) {
		boolean changed = false;
		if (PluginClassLoader.PLUGIN_CONTEXT.get() != null) {
			for (Constant c : ctx.getData().cp.array()) {
				if (c instanceof CstClass) {
					CstUTF ref = ((CstClass) c).name();
					if (GlobalClassBlackList.strStartsWithThis(ref.str())) {
						LOGGER.debug("屏蔽类 {}", ref.str());
						ctx.getData().cp.setUTFValue(ref, "roj/platform/Blacklisted");
						changed = true;
					}
				}
			}
		}
		return changed|super.transform(name, ctx);
	}
}