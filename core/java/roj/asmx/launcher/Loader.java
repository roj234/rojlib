package roj.asmx.launcher;

import org.jetbrains.annotations.Nullable;
import roj.archive.zip.ZEntry;
import roj.archive.zip.ZipFile;
import roj.archive.zip.ZipFileWriter;
import roj.asm.AsmCache;
import roj.asm.ClassNode;
import roj.asm.ClassView;
import roj.asm.insn.CodeWriter;
import roj.asm.type.Type;
import roj.asmx.AnnotationRepo;
import roj.asmx.Context;
import roj.asmx.Transformer;
import roj.ci.annotation.ReferenceByGeneratedClass;
import roj.collect.ArrayList;
import roj.collect.TrieTreeSet;
import roj.compiler.plugins.asm.ASM;
import roj.config.node.IntValue;
import roj.crypt.CRC32;
import roj.crypt.jar.JarVerifier;
import roj.io.IOUtil;
import roj.io.source.FileSource;
import roj.reflect.Bypass;
import roj.reflect.Debug;
import roj.reflect.Reflection;
import roj.text.CharList;
import roj.text.URICoder;
import roj.text.logging.Level;
import roj.text.logging.LogDestination;
import roj.text.logging.Logger;
import roj.util.ByteList;
import roj.util.FastFailException;
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.function.Function;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import static roj.asm.Opcodes.*;

/**
 * @author Roj233
 * @since 2020/11/9 23:10
 */
public class Loader implements Function<String, Class<?>> {
	private static final ThreadLocal<IntValue> IS_TRANSFORMING = ThreadLocal.withInitial(() -> new IntValue(-1));
	private static final Main MAIN;
	static {
		try {
			MAIN = (Main) Loader.class.getClassLoader();
		} catch (ClassCastException e) {
			throw new IllegalStateException("只能在launcher启动的应用中引用Loader", e);
		}
	}

	public static final Loader instance = new Loader();
	static {Main.classFinder = instance;Main.resourceFinder = instance::getResource;}

	private static final Logger LOGGER = Logger.getLogger("Ignis");

	// 这里和Loader$Init是相同类加载器的相同包了
	static ArrayList<String> args;
	@ReferenceByGeneratedClass
	static String[] args() {
		var s = args.toArray(new String[args.size()]);
		args = null;
		return s;
	}

	public static void init(String[] args) {
		// 甚至都不用传参
		// 需要注意的是，必须是这个类，别的类会崩，因为加载顺序问题，我也不清楚怎么回事，哈哈哈
		var entryPoint = (Main) CharList.Slice.class.getClassLoader();

		boolean isSecureJar = Main.class.getProtectionDomain().getCodeSource().getCertificates() != null;
		if (ASM.TARGET_JAVA_VERSION >= 17 || GetOtherJars() && !isSecureJar) {
			URL myJar = Main.class.getProtectionDomain().getCodeSource().getLocation();
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

		// 初始化Logger
		LOGGER.setLevel(Level.INFO);
		LogDestination destination = Logger.getRootContext().destination();
		Logger.getRootContext().destination(IOUtil::getSharedCharBuf);
		LOGGER.info("");
		Logger.getRootContext().destination(destination);

		String tweakerName = "roj/asmx/launcher/Tweaker";
		String mainClass = null;
		int argOffset = 0;

		if (args.length == 0) {
			Manifest manifest = instance.getManifest();
			if (manifest != null) {
				Attributes mainAttributes = manifest.getMainAttributes();
				mainClass = mainAttributes.getValue("Ignis-Main");
				if (mainClass != null) {
					String tweakerClass = mainAttributes.getValue("Ignis-Tweaker");
					if (tweakerClass != null) tweakerName = tweakerClass;
				}
			}
		} else {
			String p = args[0];
			if (args.length >= 3 && (p.equals("-t") || p.equals("--tweaker"))) {
				tweakerName = args[1];
				mainClass = args[2];
				argOffset = 3;
			} else {
				mainClass = p;
				argOffset = 1;
			}
		}

		if (mainClass == null) {
			LOGGER.fatal("Usage: EntryPoint [-t <tweaker>] <main> [args...]");
			return;
		}

		var initArgs = new String[args.length-argOffset];
		System.arraycopy(args, argOffset, initArgs, 0, initArgs.length);
		Loader.args = ArrayList.asModifiableList(initArgs);

		ClassNode init = new ClassNode();
		init.name("roj/asmx/launcher/Loader$Init");
		init.interfaces().add("java/lang/Runnable");
		init.npConstructor();

		CodeWriter c = init.newMethod(ACC_PUBLIC|ACC_STATIC, "<clinit>", "()V");
		c.visitSize(3, 0);

		instance.addTransformerExclusion(tweakerName.substring(0, tweakerName.lastIndexOf('.')+1));
		c.newObject(tweakerName.replace('.', '/'));
		c.field(GETSTATIC, "roj/asmx/launcher/Loader", "args", "Lroj/collect/ArrayList;");
		c.field(GETSTATIC, "roj/asmx/launcher/Loader", "instance", "Lroj/asmx/launcher/Loader;");
		c.invokeV("roj/asmx/launcher/Tweaker", "init", "(Ljava/util/List;Lroj/asmx/launcher/Loader;)V");

		c.newObject(init.name());
		c.field(PUTSTATIC, "roj/asmx/launcher/Main", "main", "Ljava/lang/Runnable;");
		c.insn(RETURN);
		c.finish();

		c = init.newMethod(ACC_PUBLIC, "run", "()V");
		c.visitSize(1, 1);
		c.invokeS("roj/asmx/launcher/Loader", "args", "()[Ljava/lang/String;");
		c.invokeS(mainClass.replace('.', '/'), "main", "([Ljava/lang/String;)V");
		c.insn(RETURN);
		c.finish();

		ByteList buf = AsmCache.toByteArrayShared(init);
		Class<?> loaderClass = entryPoint.defineClassA(init.name().replace('/', '.'), buf.list, 0, buf.wIndex(), Loader.class.getProtectionDomain().getCodeSource());
		Reflection.ensureClassInitialized(loaderClass);
	}

	private static boolean GetOtherJars() {
		H fn = null;
		var builder = Bypass.builder(H.class).weak().i_access("sun.net.www.protocol.jar.JarFileFactory", "fileCache", Type.klass("java/util/HashMap"), "getCache", null, true);
		Object ucp;
		URL[] urls;
		var loader = Main.class.getClassLoader();
		findURL: {
			if (loader instanceof URLClassLoader ucl) {
				// 这个异常实际上不可能发生
				try {
					fn = builder.access(URLClassLoader.class, "ucp", "getUCP", null)
							   .delegate_o(Reflection.getField(URLClassLoader.class, "ucp").getType(), "closeLoaders").build();
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
		java.util.ArrayList<URL> getPath(Object o);
		List<IOException> closeLoaders(Object o);
		HashMap<String, JarFile> getCache();
	}

	private final List<ZipFile> archives = new java.util.ArrayList<>();
	private final List<CodeSource> locations = new java.util.ArrayList<>();
	private final List<JarVerifier> verifiers = new java.util.ArrayList<>();

	private INameTransformer nameTransformer;
	private final List<Transformer> transformers = new java.util.ArrayList<>();

	private final TrieTreeSet transformExcept = new TrieTreeSet();
	private final TrieTreeSet loadExcept = new TrieTreeSet();
	public Loader() {
		transformExcept.addAll(Arrays.asList("roj.asm.", "roj.asmx.", "roj.reflect."));
		loadExcept.addAll(Arrays.asList("java.", "javax."));

		try {
			// preload asm classes
			new Context("_classwrapper_", IOUtil.getResourceIL("roj/asmx/launcher/Loader.class")).getData().parsed();
		} catch (Exception e) {
			throw new IllegalStateException("预加载转换器相关类时出现异常", e);
		}
	}

	public void registerTransformer(Transformer tr) {
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
	private void addPackage(URL sealBase, String name, JarVerifier jv) {
		int dot = name.lastIndexOf('/');
		if (dot > -1) {
			Manifest man = jv.getManifest();
			String pkgName = name.substring(0, dot).replace('/', '.');
			Attributes pkgAttrs = man.getAttributes(name.substring(0, dot+1));

			// 优先使用包专属属性，若无则使用主属性
			Attributes attrs = pkgAttrs != null ? pkgAttrs : man.getMainAttributes();

			// 提取包属性
			String specTitle = getAttribute(attrs, Attributes.Name.SPECIFICATION_TITLE);
			String specVersion = getAttribute(attrs, Attributes.Name.SPECIFICATION_VERSION);
			String specVendor = getAttribute(attrs, Attributes.Name.SPECIFICATION_VENDOR);
			String implTitle = getAttribute(attrs, Attributes.Name.IMPLEMENTATION_TITLE);
			String implVersion = getAttribute(attrs, Attributes.Name.IMPLEMENTATION_VERSION);
			String implVendor = getAttribute(attrs, Attributes.Name.IMPLEMENTATION_VENDOR);
			String sealed = getAttribute(attrs, Attributes.Name.SEALED);

			// 获取或定义包
			Package pkg = MAIN.getPackage(pkgName);
			if (pkg == null) {
				// 包未定义，创建新包
				MAIN.definePackage(
						pkgName,
						specTitle,
						specVersion,
						specVendor,
						implTitle,
						implVersion,
						implVendor,
						"true".equalsIgnoreCase(sealed) ? sealBase : null
				);
			} else if (pkg.isSealed() && !pkg.isSealed(sealBase)) {
				// 密封性冲突警告
				LOGGER.log(Level.WARN, "URL {} 加载了其他文件的封闭包 {} 的类 {}", null, sealBase, pkgName, name);
			}
		}
	}
	private String getAttribute(Attributes attrs, Attributes.Name name) {
		return attrs != null ? attrs.getValue(name) : null;
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
				var clazz = MAIN.findLoadedClass1(oldName);
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

				var in = MAIN.PARENT.getResourceAsStream(name);
				if (in == null || loadExcept.strStartsWithThis(newName)) return MAIN.PARENT.loadClass(newName);

				buf.readStreamFully(in);

				URL url = MAIN.PARENT.getResource(name);
				if (url != null) {
					//格式 file:/PATH/...!xxx
					String path = "file:".concat(url.getPath().substring(6));
					int i = path.indexOf('!');
					if (i > 0) path = path.substring(0, i);
					cs = new CodeSource(new URL(path), (CodeSigner[]) null);
				} else {
					cs = Main.class.getProtectionDomain().getCodeSource();
				}
			} catch (IOException e) {
				LOGGER.log(Level.ERROR, "读取类'{}'时发生异常", e, name);
				throw e;
			}

			if (!cs.getLocation().getHost().equals("cached") && !transformExcept.strStartsWithThis(newName))
				transform(name, newName.replace('.', '/'), buf);
			return MAIN.defineClassA(newName, buf.list, 0, buf.wIndex(), cs);
		} catch (ClassNotFoundException e) {
			Helpers.athrow(e);
		}  catch (Throwable e) {
			LOGGER.debug("类 {} 加载失败", e, newName);
			throw new FastFailException("关键错误：转换器故障 "+newName, e);
		} finally {
			buf.release();
		}
		return null;
	}

	public final void transform(String name, String transformedName, ByteList list) {
		Context ctx = new Context(name, list);
		boolean changed = false;

		List<Transformer> ts = transformers;
		var reentrant = IS_TRANSFORMING.get();
		if (reentrant.value >= 1) {
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
					Debug.dump("transform_failed", ctx.getClassBytes());
				} catch (Throwable e1) {
					LOGGER.fatal("保存'{}'的内容用于调试时发生异常", e1, name);
				}
				Helpers.athrow(e);
			}
		}
		reentrant.value = -1;
		if (changed) {
			// See ConstantPool#checkCollision
			ByteList b = ctx.getClassBytes();
			if (b != list) {
				list.clear();
				list.put(b);
			}
		}
	}

	public Manifest getManifest() {
		for (int i = 0; i < archives.size(); i++) {
			var zf = archives.get(i);
			var ze = zf.getEntry("META-INF/MANIFEST.MF");
			if (ze != null) {
				var jv = verifiers.get(i);
				if (jv != null) return jv.getManifest();
				try {
					return new Manifest(zf.getStream(ze));
				} catch (IOException ignored) {}
			}
		}

		return null;
	}

	public boolean hasResource(String name) {
		for (int i = 0; i < archives.size(); i++) {
			var zf = archives.get(i);
			if (zf.getEntry(name) != null) return true;
		}

		var in = MAIN.getResource(name);
		if (in == null) in = ClassLoader.getSystemResource(name);
		return in != null;
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

		var in = MAIN.getParentResource(name);
		if (in == null) in = ClassLoader.getSystemResourceAsStream(name);
		return in;
	}

	public List<Transformer> getTransformers() {return transformers;}
	public void addTransformerExclusion(String toExclude) {transformExcept.add(toExclude);}

	public void enableFastZip(URL url, boolean skipCodeSource) throws IOException {
		ZipFile zf = new ZipFile(new File(URICoder.decodeURI(url.getPath().substring(1))));
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

			int fastHash = CRC32.initial;
			for (ZEntry entry : archive.entries()) {
				buf.clear();
				buf.putUTF(entry.getName()).putLong(entry.getModificationTime()).putInt(entry.getCrc32());

				fastHash = CRC32.update(fastHash, buf.list, 0, buf.wIndex());
			}
			fastHash = CRC32.finish(fastHash);

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
								String name = ClassView.parse(buf, false).name;

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
			for (ZipFile archive : archives) {
				repo.loadCacheOrAdd(archive);
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