package roj.launcher;

import roj.asm.Parser;
import roj.asm.tree.ConstantData;
import roj.asm.util.AccessFlag;
import roj.asm.visitor.CodeWriter;
import roj.collect.MyHashMap;
import roj.collect.SimpleList;
import roj.reflect.DirectAccessor;
import roj.reflect.ReflectionUtils;
import roj.text.logging.Level;
import roj.text.logging.Logger;
import roj.util.ByteList;

import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;

import static roj.asm.Opcodes.*;

/**
 * @author Roj234
 * @since 2023/8/6 0006 0:13
 */
public final class Bootstrap {
	public static final Logger LOGGER = Logger.getLogger("Launcher");
	public static final Map<String, Object> blackboard = new MyHashMap<>();

	public static final ClassWrapper classLoader = new ClassWrapper();
	public static final List<ITweaker> tweakers = new SimpleList<>();

	public static String[] arguments;

	// 这里和Bootstrap$Loader是同一个包了
	static List<String> argList = new SimpleList<>();
	static String[] getArg() {
		arguments = argList.toArray(new String[argList.size()]);
		argList = null;
		return arguments;
	}

	public static void boot(String[] args) {
		// 甚至都不用传参
		EntryPoint entryPoint = (EntryPoint) Bootstrap.class.getClassLoader();
		for (URL url : GetOtherJars()) entryPoint.addURL(url);

		Set<String> tweakerNames = new LinkedHashSet<>();

		int i;
		for (i = 0; i < args.length-1; i++) {
			String arg = args[i];
			if (arg.charAt(0) != '-') break;
			switch (arg) {
				case "-t":
				case "--tweaker":
					arg = args[++i];
					if (!tweakerNames.add(arg)) LOGGER.log(Level.WARN, "Tweaker '{}' 已存在", null, arg);
				break;
				default:
					LOGGER.log(Level.FATAL, "Unknown argument {}", null, arg);
				break;
			}
		}
		if (tweakerNames.isEmpty()) tweakerNames.add("roj.launcher.RojLibTweaker");

		String target = args[i++];

		arguments = new String[args.length-i];
		System.arraycopy(args, i, arguments, 0, arguments.length);

		ConstantData L = new ConstantData();
		L.name("roj/launcher/Bootstrap$Loader");
		L.interfaces().add("java/lang/Runnable");
		L.npConstructor();

		CodeWriter c = L.newMethod(AccessFlag.PUBLIC|AccessFlag.STATIC, "<clinit>", "()V");
		c.visitSize(3, 2);
		c.field(GETSTATIC, "roj/launcher/Bootstrap", "arguments", "[Ljava/lang/String;");
		c.one(ASTORE_0);

		for (String name : tweakerNames) {
			classLoader.addClassLoaderExclusion(name.substring(0, name.lastIndexOf('.')+1));

			c.field(GETSTATIC, "roj/launcher/Bootstrap", "LOGGER", "Lroj/text/logging/Logger;");
			c.ldc("加载Tweaker '"+name+"'");
			c.invokeV("roj/text/logging/Logger", "info", "(Ljava/lang/String;)V");

			c.newObject(name.replace('.', '/'));
			c.one(ASTORE_1);

			c.one(ALOAD_1);
			c.one(ALOAD_0);
			c.field(GETSTATIC, "roj/launcher/Bootstrap", "classLoader", "Lroj/launcher/ClassWrapper;");
			c.invokeItf("roj/launcher/ITweaker", "initialize", "([Ljava/lang/String;Lroj/launcher/ClassWrapper;)[Ljava/lang/String;");

			c.one(ASTORE_0);

			c.one(ALOAD_1);
			c.field(GETSTATIC, "roj/launcher/Bootstrap", "argList", "Ljava/util/List;");
			c.invokeItf("roj/launcher/ITweaker", "addArguments", "(Ljava/util/List;)V");

			c.field(GETSTATIC, "roj/launcher/Bootstrap", "tweakers", "Ljava/util/List;");
			c.one(ALOAD_1);
			c.invokeItf("java/util/List", "add", "(Ljava/lang/Object;)Z");
			c.one(POP);
		}

		c.newObject(L.name);
		c.field(PUTSTATIC, "roj/launcher/EntryPoint", "loaderInst", "Ljava/lang/Runnable;");
		c.one(RETURN);
		c.finish();

		c = L.newMethod(AccessFlag.PUBLIC, "run", "()V");
		c.visitSize(2, 1);
		c.field(GETSTATIC, "roj/launcher/Bootstrap", "LOGGER", "Lroj/text/logging/Logger;");
		c.ldc("启动 '"+target+"'");
		c.invokeV("roj/text/logging/Logger", "info", "(Ljava/lang/String;)V");
		c.invokeS("roj/launcher/Bootstrap", "getArg", "()[Ljava/lang/String;");
		c.invokeS(target.replace('.', '/'), "main", "([Ljava/lang/String;)V");
		c.one(RETURN);
		c.finish();

		ByteList list = Parser.toByteArrayShared(L);
		Class<?> loaderClass = entryPoint.defineClassB(L.name.replace('/', '.'), list.list, 0, list.wIndex());
		ReflectionUtils.u.ensureClassInitialized(loaderClass);

		EntryPoint.actualLoader = classLoader;
	}

	private static URL[] GetOtherJars() {
		ClassLoader loader = EntryPoint.class.getClassLoader();
		if (loader instanceof URLClassLoader) return ((URLClassLoader) loader).getURLs();
		if (loader.getClass().getName().endsWith("AppClassLoader")) {
			for (Field field : ReflectionUtils.getFields(loader.getClass())) {
				if (field.getType().getName().endsWith("URLClassPath")) {
					for (Field field1 : field.getType().getDeclaredFields()) {
						if (field1.getName().equals("path")) {
							H fn = DirectAccessor.builder(H.class).access(loader.getClass(), field.getName(), "getUCP", null).access(field.getType(), field1.getName(), "getPath", null).build();
							ArrayList<URL> path = fn.getPath(fn.getUCP(loader));
							return path.toArray(new URL[path.size()]);
						}
					}
				}
			}
		}
		throw new IllegalArgumentException("不支持的ClassLoader " + loader.getClass().getName());
	}
	private interface H {
		Object getUCP(Object o);
		ArrayList<URL> getPath(Object o);
	}
}
