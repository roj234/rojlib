package roj.asmx.launcher;

import org.jetbrains.annotations.Nullable;
import roj.ReferenceByGeneratedClass;
import roj.archive.zip.ZEntry;
import roj.archive.zip.ZipFile;
import roj.archive.zip.ZipFileWriter;
import roj.asm.ClassNode;
import roj.asm.Parser;
import roj.asm.insn.CodeWriter;
import roj.asm.type.Type;
import roj.asm.util.Context;
import roj.asmx.AnnotationRepo;
import roj.asmx.ITransformer;
import roj.collect.SimpleList;
import roj.collect.TrieTreeSet;
import roj.compiler.plugins.asm.ASM;
import roj.config.data.CInt;
import roj.crypt.CRC32s;
import roj.crypt.jar.JarVerifier;
import roj.io.FastFailException;
import roj.io.IOUtil;
import roj.io.source.FileSource;
import roj.reflect.Bypass;
import roj.reflect.Debug;
import roj.reflect.ReflectionUtils;
import roj.text.CharList;
import roj.text.Escape;
import roj.text.logging.Level;
import roj.text.logging.Logger;
import roj.util.ByteList;
import roj.util.Helpers;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.CodeSigner;
import java.security.CodeSource;
import java.security.GeneralSecurityException;
import java.util.*;
import java.util.function.Function;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import static roj.asm.Opcodes.*;

/**
 * @author Roj233
 * @since 2020/11/9 23:10
 */
public class Bootstrap implements Function<String, Class<?>> {
	private static final ThreadLocal<CInt> IS_TRANSFORMING = ThreadLocal.withInitial(() -> new CInt(-1));
	private static final EntryPoint ENTRY_POINT;
	static {
		try {
			ENTRY_POINT = (EntryPoint) Bootstrap.class.getClassLoader();
		} catch (ClassCastException e) {
			throw new IllegalStateException("不能在非TLauncher环境中引用ClassWrapper", e);
		}
	}

	public static final Bootstrap instance = new Bootstrap();
	static {EntryPoint.classFinder = instance;EntryPoint.resourceFinder = instance::getResource;}

	private static final Logger LOGGER = Logger.getLogger(/*Transforming Class Loader*/"TCL");

	@ReferenceByGeneratedClass
	static final List<ITweaker> tweakers = new SimpleList<>();

	static String[] initArgs;
	// 这里和Bootstrap$Loader是同一个包了
	static SimpleList<String> args;
	static String[] getArg() {
		var s = args.toArray(new String[args.size()]);
		args = null;
		return s;
	}
	public static void boot(String[] args) {
		// 甚至都不用传参
		// 需要注意的是，必须是这个类，别的类会崩，因为加载顺序问题，我也不清楚怎么回事，哈哈哈
		var entryPoint = (EntryPoint) CharList.Slice.class.getClassLoader();

		boolean isSecureJar = EntryPoint.class.getProtectionDomain().getCodeSource().getCertificates() != null;
		if (ASM.TARGET_JAVA_VERSION >= 17 || GetOtherJars() && !isSecureJar) {
			URL myJar = EntryPoint.class.getProtectionDomain().getCodeSource().getLocation();
			if (myJar != null && myJar.getProtocol().equals("file") && myJar.getPath().indexOf('!') < 0) {
				try {
					instance.enableFastZip(myJar, false);
				} catch (Exception e) {
					e.printStackTrace();
				}
			} else {
				LOGGER.warn("未能开启高性能ZIP，如果你是Java17或更高版本，那就无所谓了");
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

		ClassNode L = new ClassNode();
		L.name("roj/asmx/launcher/Bootstrap$Loader");
		L.interfaces().add("java/lang/Runnable");
		L.npConstructor();

		CodeWriter c = L.newMethod(ACC_PUBLIC|ACC_STATIC, "<clinit>", "()V");
		c.visitSize(3, 2);
		c.field(GETSTATIC, "roj/asmx/launcher/Bootstrap", "args", "Lroj/collect/SimpleList;");
		c.insn(ASTORE_0);

		for (String name : tweakerNames) {
			instance.addTransformerExclusion(name.substring(0, name.lastIndexOf('.')+1));
			if (debug) {
				c.field(GETSTATIC, "roj/asmx/launcher/Bootstrap", "LOGGER", "Lroj/text/logging/Logger;");
				c.ldc("Tweaker => '"+name+"'");
				c.invokeV("roj/text/logging/Logger", "debug", "(Ljava/lang/String;)V");
			}
			c.newObject(name.replace('.', '/'));
			c.insn(ASTORE_1);

			c.insn(ALOAD_1);
			c.insn(ALOAD_0);
			c.field(GETSTATIC, "roj/asmx/launcher/Bootstrap", "instance", "Lroj/asmx/launcher/Bootstrap;");
			c.invokeItf("roj/asmx/launcher/ITweaker", "init", "(Ljava/util/List;Lroj/asmx/launcher/Bootstrap;)V");

			c.field(GETSTATIC, "roj/asmx/launcher/Bootstrap", "tweakers", "Ljava/util/List;");
			c.insn(ALOAD_1);
			c.invokeItf("java/util/List", "add", "(Ljava/lang/Object;)Z");
			c.insn(POP);
		}

		c.newObject(L.name());
		c.field(PUTSTATIC, "roj/asmx/launcher/EntryPoint", "mainInvoker", "Ljava/lang/Runnable;");
		c.insn(RETURN);
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
		c.insn(RETURN);
		c.finish();

		ByteList list = Parser.toByteArrayShared(L);
		Class<?> loaderClass = entryPoint.defineClassA(L.name().replace('/', '.'), list.list, 0, list.wIndex(), Bootstrap.class.getProtectionDomain().getCodeSource());
		ReflectionUtils.ensureClassInitialized(loaderClass);
	}

	private static boolean GetOtherJars() {
		H fn = null;
		var builder = Bypass.builder(H.class).weak().i_access("sun.net.www.protocol.jar.JarFileFactory", "fileCache", Type.klass("java/util/HashMap"), "getCache", null, true);
		Object ucp;
		URL[] urls;
		var loader = EntryPoint.class.getClassLoader();
		findURL: {
			if (loader instanceof URLClassLoader ucl) {
				// 这个异常实际上不可能发生
				try {
					fn = builder.access(URLClassLoader.class, "ucp", "getUCP", null)
							   .delegate_o(ReflectionUtils.getField(URLClassLoader.class, "ucp").getType(), "closeLoaders").build();
				} catch (NoSuchFieldException ignored) {}
				ucp = fn.getUCP(loader);
				urls = ucl.getURLs();
				break findURL;
			} else {
				if (loader.getClass().getName().endsWith("AppClassLoader")) {
					Field _ucp = null;
					Class<?> type = loader.getClass();
					loop:
					while (type != Object.class) {
						for (Field field : type.getDeclaredFields()) {
							if (field.getType().getName().endsWith("URLClassPath")) {
								_ucp = field;
								break loop;
							}
						}
						type = type.getSuperclass();
					}
					if (_ucp != null) {
						for (var _path : _ucp.getType().getDeclaredFields()) {
							if (_path.getName().equals("path")) {
								fn = builder.access(loader.getClass(), _ucp.getName(), "getUCP", null)
									.access(_ucp.getType(), _path.getName(), "getPath", null)
									.delegate_o(_ucp.getType(), "closeLoaders").build();

								ucp = fn.getUCP(loader);
								var path = fn.getPath(ucp);
								urls = path.toArray(new URL[path.size()]);
								break findURL;
							}
						}
					}
				}
			}

			LOGGER.warn("并非直接从从文件加载: {}", loader.getClass().getName());
			return true;
		}
		if (urls.length == 0) return true;

		boolean hasError = false;
		for (URL url : urls) {
			if (!url.getProtocol().equals("file")) {
				LOGGER.warn("非文件的classpath {}", url);
				hasError = true;
			} else {
				try {
					instance.enableFastZip(url, true);
				} catch (Exception e) {
					hasError = true;
					e.printStackTrace();
				}
			}
		}

		if (!hasError) {
			fn.closeLoaders(ucp);
			try {
				fn.getCache().clear();
			} catch (Throwable ignored) {}
		}
		return false;
	}
	private interface H {
		Object getUCP(Object o);
		ArrayList<URL> getPath(Object o);
		List<IOException> closeLoaders(Object o);
		HashMap<String, JarFile> getCache();
	}

	private final List<ZipFile> archives = new ArrayList<>();
	private final List<CodeSource> locations = new ArrayList<>();
	private final List<JarVerifier> verifiers = new ArrayList<>();

	private INameTransformer nameTransformer;
	private final List<ITransformer> transformers = new ArrayList<>();

	private final TrieTreeSet transformExcept = new TrieTreeSet();
	private final TrieTreeSet loadExcept = new TrieTreeSet();
	public Bootstrap() {
		transformExcept.addAll(Arrays.asList("roj.asm.", "roj.asmx.", "roj.reflect."));
		loadExcept.addAll(Arrays.asList("java.", "javax."));

		try {
			// preload asm classes
			new Context("_classwrapper_", IOUtil.getResourceIL("roj/asmx/launcher/Bootstrap.class")).getData().parsed();
		} catch (Exception e) {
			throw new IllegalStateException("预加载转换器相关类时出现异常", e);
		}
	}

	public void registerTransformer(ITransformer tr) {
		if (transformers.contains(tr)) throw new IllegalArgumentException("Transformer already exist: "+tr);
		transformers.add(tr);
		if (tr instanceof INameTransformer) {
			if (nameTransformer == null) {
				nameTransformer = (INameTransformer) tr;
				LOGGER.trace("注册类名映射 '{}'", tr.getClass().getName());
			} else {
				LOGGER.debug("类名映射 '{}' 因为已存在 '{}' 而被跳过", tr.getClass().getName(), nameTransformer.getClass().getName());
			}
		} else {
			LOGGER.trace("注册类转换器 '{}'", tr.getClass().getName());
		}
	}

	private void addPackage(URL url, String name, JarVerifier jv)  {
		int dot = name.lastIndexOf('/');
		if (dot > -1) {
			Manifest man = jv.getManifest();
			// TODO specTitle ... 等我理解了manifest中包定义的结构再说 先让它自己来
			//m.getAttributes();
			/*String pkgName = name.substring(0, dot).replace('/', '.');

			Package pkg = ENTRY_POINT.getPackage(pkgName);
			if (pkg == null) {
				ENTRY_POINT.definePackage(pkgName, null, null, null, null, null, null, isSealed(pkgName, man) ? url : null);
			} else if (pkg.isSealed() && !pkg.isSealed(url)) {
				LOGGER.log(Level.WARN,  "{} 加载了其他文件的封闭包 {} 的类 {}", null, url, pkgName, name);
			}*/
		}
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

	@Override
	public Class<?> apply(String name) {
		String newName;
		if (nameTransformer == null) {
			newName = name;
		} else {
			newName = nameTransformer.mapName(name);
			var oldName = nameTransformer.unmapName(name);
			if (!oldName.equals(name)) {
				var clazz = ENTRY_POINT.findLoadedClass1(oldName);
				if (clazz != null) return clazz;
			}
		}

		var buf = new ByteList();
		buf.ensureCapacity(4096);

		CodeSource cs;
		try {
			name/*File Name*/ = newName.replace('.', '/').concat(".class");

			block:
			try {
				for (int i = 0; i < archives.size(); i++) {
					var za = archives.get(i);
					var in = za.getStream(name);
					if (in != null) {
						var jv = verifiers.get(i);
						if (jv != null) {
							in = jv.wrapInput(name, in);
							addPackage(locations.get(i).getLocation(), name, jv);
						}
						buf.readStreamFully(in);
						cs = locations.get(i);
						break block;
					}
				}

				var in = ENTRY_POINT.PARENT.getResourceAsStream(name);
				if (in == null || loadExcept.strStartsWithThis(newName)) return ENTRY_POINT.PARENT.loadClass(newName);

				buf.readStreamFully(in);

				URL url = ENTRY_POINT.PARENT.getResource(name);
				if (url != null) {
					//格式 file:/PATH/...!xxx
					String path = "file:".concat(url.getPath().substring(6));
					int i = path.indexOf('!');
					if (i > 0) path = path.substring(0, i);
					cs = new CodeSource(new URL(path), (CodeSigner[]) null);
				} else {
					cs = EntryPoint.class.getProtectionDomain().getCodeSource();
				}
			} catch (IOException e) {
				LOGGER.log(Level.ERROR, "读取类'{}'时发生异常", e, name);
				throw e;
			}

			if (!cs.getLocation().getHost().equals("cached") && !transformExcept.strStartsWithThis(newName))
				transform(name, newName.replace('.', '/'), buf);
			return ENTRY_POINT.defineClassA(newName, buf.list, 0, buf.wIndex(), cs);
		} catch (ClassNotFoundException e) {
			Helpers.athrow(e);
		}  catch (Throwable e) {
			LOGGER.debug("类 {} 加载失败", e, newName);
			throw new FastFailException("关键错误：转换器故障 "+newName, e);
		} finally {
			buf._free();
		}
		return null;
	}

	public final void transform(String name, String transformedName, ByteList list) {
		Context ctx = new Context(name, list);
		boolean changed = false;

		List<ITransformer> ts = transformers;
		var reentrant = IS_TRANSFORMING.get();
		if (reentrant.value >= 0) {
			LOGGER.warn("类转换器'{}'正在引用'{}'", ts.get(reentrant.value).getClass().getName(), transformedName);
			return;
		}

		for (int i = 0; i < ts.size(); i++) {
			reentrant.value = i;
			try {
				changed |= ts.get(i).transform(transformedName, ctx);
			} catch (Throwable e) {
				reentrant.value = -1;

				LOGGER.fatal("转换类'{}'时发生异常({}/{})", e, name, i);
				try {
					Debug.dump("transform_failed", ctx.get());
				} catch (Throwable e1) {
					LOGGER.fatal("保存'{}'的内容用于调试时发生异常", e1, name);
				}
				Helpers.athrow(e);
			}
		}
		reentrant.value = -1;
		if (changed) {
			// See ConstantPool#checkCollision
			ByteList b = ctx.get();
			if (b != list) {
				list.clear();
				list.put(b);
			}
		}
	}

	@Nullable
	public InputStream getResource(String name) {
		for (int i = 0; i < archives.size(); i++) {
			var zf = archives.get(i);
			InputStream in = null;
			try {
				in = zf.getStream(name);
			} catch (IOException ignored) {}
			if (in != null) {
				var jv = verifiers.get(i);
				if (jv != null) in = jv.wrapInput(name, in);
				return in;
			}
		}

		var in = ENTRY_POINT.getParentResource(name);
		if (in == null) in = ClassLoader.getSystemResourceAsStream(name);
		return in;
	}

	public List<ITransformer> getTransformers() {return transformers;}
	public void addTransformerExclusion(String toExclude) {transformExcept.add(toExclude);}

	public void enableFastZip(URL url, boolean skipCodeSource) throws IOException {
		ZipFile zf = new ZipFile(new File(Escape.decodeURI(url.getPath().substring(1))));
		JarVerifier jv = JarVerifier.create(zf);
		if (archives.isEmpty()) IOUtil.read(zf.getStream(zf.entries().iterator().next())); // INIT
		archives.add(zf);
		int slot = verifiers.size();
		verifiers.add(null);
		locations.add(new CodeSource(url, (CodeSigner[]) null));
		if (jv != null) {
			if (skipCodeSource) locations.set(slot, jv.getCodeSource());
			try {
				jv.ensureManifestValid(false);
			} catch (GeneralSecurityException e) {
				Helpers.athrow(e);
			}
			verifiers.set(slot, jv);
		}
	}
	public void enableTransformerCache() throws IOException {
		ByteList buf = new ByteList();
		buf.ensureCapacity(4096);

		for (int i = 0; i < archives.size(); i++) {
			ZipFile archive = archives.get(i);

			int fastHash = CRC32s.INIT_CRC;
			for (ZEntry entry : archive.entries()) {
				buf.clear();
				buf.putUTF(entry.getName()).putLong(entry.getModificationTime()).putInt(entry.getCrc32());

				fastHash = CRC32s.update(fastHash, buf.list, 0, buf.wIndex());
			}
			fastHash = CRC32s.retVal(fastHash);

			File out = new File(".cached/"+IOUtil.fileName(((FileSource) archive.source()).getFile().getName())+"-"+Integer.toHexString(fastHash)+".jar");
			if (out.isFile()) {
				verifiers.set(i, null);
				archives.set(i, new ZipFile(out)).close();
				locations.set(i, new CodeSource(new URL("file://cached/"+out.getName()), (CodeSigner[]) null));
			} else {
				JarVerifier jv = verifiers.get(i);

				try (ZipFileWriter zfw = new ZipFileWriter(out, 9, 0)) {
					for (ZEntry entry : archive.entries()) {
						if (entry.getName().endsWith(".class")) {
							InputStream in = null;
							try {
								in = archive.getStream(entry);
								if (jv != null && jv.isSigned()) in = jv.wrapInput(entry.getName(), in);

								buf.clear();
								buf.readStreamFully(in);
								String name = Parser.parseAccess(buf, false).name;

								buf.rIndex = 0;
								if (name.concat(".class").equals(entry.getName())) {
									String newName = nameTransformer == null ? name : nameTransformer.mapName(name);
									transform(name, newName, buf);

									zfw.beginEntry(new ZEntry(entry.getName()));
									buf.rIndex = 0;
									buf.writeToStream(zfw);
									zfw.closeEntry();
								}
							} catch (Exception e) {
								e.printStackTrace();
							} finally {
								IOUtil.closeSilently(in);
							}
						}
					}
				}
			}
		}
	}

	private AnnotationRepo repo;
	public AnnotationRepo getAnnotations() throws IOException {
		if (this.repo == null) {
			if (archives.isEmpty()) LOGGER.warn("无法读取注解：无法列出文件");

			var repo = new AnnotationRepo();
			var buf = IOUtil.getSharedByteBuf();
			for (ZipFile archive : archives) {
				ZEntry entry = archive.getEntry("META-INF/annotations.repo");
				if (entry != null) {
					buf.clear();
					try {
						if (repo.deserialize(archive.get(entry, buf))) {
							LOGGER.debug("从缓存读取注解: {}", archive.source());
							continue;
						}
					} catch (Exception ignored) {}
				}
				repo.add(archive);
			}

			this.repo = repo;
		}
		return repo;
	}
	public void setAnnotations(AnnotationRepo repo) {
		if (this.repo != null) throw new IllegalStateException("Annotation already set");
		this.repo = repo;
	}
	public long getAnnotationTimestamp() {
		long time = 0;
		for (ZipFile archive : archives) {
			if (archive.source() instanceof FileSource fs) {
				long mod = fs.getFile().lastModified();
				if (time < mod) time = mod;
			} else {
				LOGGER.warn("有些源来自内存: {}, 注解时间戳可能无效.", archive.source());
			}
		}
		return time;
	}
}