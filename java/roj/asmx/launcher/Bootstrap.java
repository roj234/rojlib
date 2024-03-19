package roj.asmx.launcher;

import roj.ReferenceByGeneratedClass;
import roj.asm.Parser;
import roj.asm.tree.ConstantData;
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
	public static final ClassWrapper classLoader = new ClassWrapper();
	static { EntryPoint.actualLoader = classLoader; }

	public static final Logger LOGGER = Logger.getLogger("Launcher");
	public static final Map<String, Object> blackboard = new MyHashMap<>();

	@ReferenceByGeneratedClass
	public static final List<ITweaker> tweakers = new SimpleList<>();

	public static String[] initArgs;
	// 这里和Bootstrap$Loader是同一个包了
	static SimpleList<String> args;
	static String[] getArg() {
		var s = args.toArray(new String[args.size()]);
		args = null;
		return s;
	}

	public static void boot(String[] args) {
		// 甚至都不用传参
		EntryPoint entryPoint = (EntryPoint) Bootstrap.class.getClassLoader();

		// necessary (加载Logger相关类)
		LOGGER.info("ImpLib TLauncher 2.1");

		{
			URL[] urls = GetOtherJars();
			if (urls != null && urls.length > 0) for (URL url : urls) {
				if (!url.getProtocol().equals("file")) {
					LOGGER.warn("非文件的classpath {}", url);
				} else {
					try {
						classLoader.enableFastZip(url);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			} else {
				URL myJar = EntryPoint.class.getProtectionDomain().getCodeSource().getLocation();
				if (myJar != null) {
					try {
						classLoader.enableFastZip(myJar);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
		}

		Set<String> tweakerNames = new LinkedHashSet<>();

		boolean debug = false;
		LOGGER.setLevel(Level.INFO);
		int i;
		for (i = 0; i < args.length-1;) {
			String arg = args[i];
			if (arg.charAt(0) != '-') break;
			if (arg.equals("--")) { i++; break; }
			switch (arg) {
				case "-debug": debug = true; break;
				case "-t", "--tweaker":
					arg = args[++i];
					if (!tweakerNames.add(arg)) LOGGER.warn("转换器 '{}' 已存在", arg);
				break;
				default:
					LOGGER.error("无法识别的参数 {}", arg);
				break;
			}
			i++;
		}

		if (i == args.length) {
			LOGGER.fatal("未指定运行目标");
			return;
		}

		String target = args[i++];

		initArgs = new String[args.length-i];
		System.arraycopy(args, i, initArgs, 0, initArgs.length);
		if (tweakerNames.isEmpty()) {
			tweakerNames.add("roj/asmx/launcher/DefaultTweaker");
		}
		Bootstrap.args = new SimpleList<>();
		Bootstrap.args.addAll(initArgs);

		ConstantData L = new ConstantData();
		L.name("roj/asmx/launcher/Bootstrap$Loader");
		L.interfaces().add("java/lang/Runnable");
		L.npConstructor();

		CodeWriter c = L.newMethod(ACC_PUBLIC|ACC_STATIC, "<clinit>", "()V");
		c.visitSize(3, 2);
		c.field(GETSTATIC, "roj/asmx/launcher/Bootstrap", "args", "Lroj/collect/SimpleList;");
		c.one(ASTORE_0);

		for (String name : tweakerNames) {
			classLoader.addTransformerExclusion(name.substring(0, name.lastIndexOf('.')+1));
			if (debug) {
				c.field(GETSTATIC, "roj/asmx/launcher/Bootstrap", "LOGGER", "Lroj/text/logging/Logger;");
				c.ldc("Tweaker => '"+name+"'");
				c.invokeV("roj/text/logging/Logger", "debug", "(Ljava/lang/String;)V");
			}
			c.newObject(name.replace('.', '/'));
			c.one(ASTORE_1);

			c.one(ALOAD_1);
			c.one(ALOAD_0);
			c.field(GETSTATIC, "roj/asmx/launcher/Bootstrap", "classLoader", "Lroj/asmx/launcher/ClassWrapper;");
			c.invokeItf("roj/asmx/launcher/ITweaker", "init", "(Ljava/util/List;Lroj/asmx/launcher/ClassWrapper;)V");

			c.field(GETSTATIC, "roj/asmx/launcher/Bootstrap", "tweakers", "Ljava/util/List;");
			c.one(ALOAD_1);
			c.invokeItf("java/util/List", "add", "(Ljava/lang/Object;)Z");
			c.one(POP);
		}

		c.newObject(L.name);
		c.field(PUTSTATIC, "roj/asmx/launcher/EntryPoint", "loaderInst", "Ljava/lang/Runnable;");
		c.one(RETURN);
		c.finish();

		c = L.newMethod(ACC_PUBLIC, "run", "()V");
		c.visitSize(2, 1);
		if (debug) {
			c.field(GETSTATIC, "roj/asmx/launcher/Bootstrap", "LOGGER", "Lroj/text/logging/Logger;");
			c.ldc("Main => '"+target+"'");
			c.invokeV("roj/text/logging/Logger", "debug", "(Ljava/lang/String;)V");
		}
		c.invokeS("roj/asmx/launcher/Bootstrap", "getArg", "()[Ljava/lang/String;");
		c.invokeS(target.replace('.', '/'), "main", "([Ljava/lang/String;)V");
		c.one(RETURN);
		c.finish();

		ByteList list = Parser.toByteArrayShared(L);
		Class<?> loaderClass = entryPoint.defineClassB(L.name.replace('/', '.'), list.list, 0, list.wIndex());
		ReflectionUtils.ensureClassInitialized(loaderClass);
	}

	private static URL[] GetOtherJars() {
		ClassLoader loader = EntryPoint.class.getClassLoader();
		if (loader instanceof URLClassLoader) return ((URLClassLoader) loader).getURLs();
		if (loader.getClass().getName().endsWith("AppClassLoader")) {
			for (Field field : ReflectionUtils.getFields(loader.getClass())) {
				if (field.getType().getName().endsWith("URLClassPath")) {
					for (Field field1 : field.getType().getDeclaredFields()) {
						if (field1.getName().equals("path")) {
							H fn = DirectAccessor.builder(H.class).weak().access(loader.getClass(), field.getName(), "getUCP", null).access(field.getType(), field1.getName(), "getPath", null).build();
							ArrayList<URL> path = fn.getPath(fn.getUCP(loader));
							return path.toArray(new URL[path.size()]);
						}
					}
				}
			}
		}
		LOGGER.warn("并非直接从从文件加载: {}", loader.getClass().getName());
		return null;
	}
	private interface H {
		Object getUCP(Object o);
		ArrayList<URL> getPath(Object o);
	}
}