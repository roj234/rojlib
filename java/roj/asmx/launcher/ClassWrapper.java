package roj.asmx.launcher;

import org.jetbrains.annotations.Nullable;
import roj.archive.zip.ZEntry;
import roj.archive.zip.ZipFile;
import roj.archive.zip.ZipFileWriter;
import roj.asm.Parser;
import roj.asm.util.Context;
import roj.asmx.AnnotationRepo;
import roj.asmx.ITransformer;
import roj.collect.TrieTreeSet;
import roj.crypt.CRC32s;
import roj.crypt.jar.JarVerifier;
import roj.io.IOUtil;
import roj.io.source.FileSource;
import roj.text.Escape;
import roj.text.logging.Level;
import roj.util.ByteList;
import roj.util.Helpers;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.CodeSigner;
import java.security.CodeSource;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import static roj.asmx.launcher.Bootstrap.LOGGER;

/**
 * @author Roj233
 * @since 2020/11/9 23:10
 */
public class ClassWrapper implements Function<String, Class<?>> {
	private static final EntryPoint ENTRY_POINT;
	static {
		try {
			ENTRY_POINT = (EntryPoint) ClassWrapper.class.getClassLoader();
		} catch (ClassCastException e) {
			throw new IllegalStateException("不能在非TLauncher环境中引用ClassWrapper");
		}
	}
	public static final ClassWrapper instance = new ClassWrapper();
	final Function<String, InputStream> resourceLoader() {return this::getResource;}

	private final List<ZipFile> archives = new ArrayList<>();
	private final List<CodeSource> locations = new ArrayList<>();
	private final List<JarVerifier> verifiers = new ArrayList<>();

	private INameTransformer nameTransformer;
	private final List<ITransformer> transformers = new ArrayList<>();

	private final TrieTreeSet transformExcept = new TrieTreeSet();
	public ClassWrapper() {
		transformExcept.addAll(Arrays.asList("roj.asm.", "roj.asmx.", "roj.reflect."));

		try {
			// preload asm classes
			new Context("_classwrapper_", IOUtil.getResource("roj/asmx/launcher/ClassWrapper.class")).getData().parsed();
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
				if (in == null) in = ClassLoader.getSystemResourceAsStream(name);
				if (in == null) return ENTRY_POINT.PARENT.loadClass(newName);

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
		} catch (Throwable e) {
			Helpers.athrow(new ClassNotFoundException(newName, e));
			return null;
		} finally {
			buf._free();
		}
	}

	private int reentrant = -1;
	public final void transform(String name, String transformedName, ByteList list) {
		Context ctx = new Context(name, list);
		boolean changed = false;
		List<ITransformer> ts = transformers;
		int prev = reentrant;
		for (int i = 0; i < ts.size(); i++) {
			reentrant = i;
			try {
				boolean changed1 = ts.get(i).transform(transformedName, ctx);
				if (changed1 && prev == i) LOGGER.warn("类转换器'{}'可能造成了循环调用", ts.get(i).getClass().getName());
				changed |= changed1;
			} catch (Throwable e) {
				LOGGER.fatal("转换类'{}'时发生异常", e, name);
				try {
					ctx.getData().dump();
				} catch (Throwable e1) {
					LOGGER.fatal("保存'{}'的内容用于调试时发生异常", e1, name);
				}
				Helpers.athrow(e);
			}
		}
		reentrant = prev;
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

		var in = ENTRY_POINT.PARENT.getResourceAsStream(name);
		if (in == null) in = ClassLoader.getSystemResourceAsStream(name);
		return in;
	}

	public List<ITransformer> getTransformers() {return transformers;}
	public void addTransformerExclusion(String toExclude) {transformExcept.add(toExclude);}

	public void enableFastZip(URL url) throws IOException {
		ZipFile zf = new ZipFile(new File(Escape.decodeURI(IOUtil.getSharedCharBuf(), IOUtil.getSharedByteBuf(), url.getPath().substring(1)).toString()));
		JarVerifier jv = JarVerifier.create(zf);
		if (archives.isEmpty()) zf.getStream(zf.entries().iterator().next()).close(); // INIT
		archives.add(zf);
		if (jv != null) {
			try {
				jv.ensureManifestValid();
			} catch (GeneralSecurityException e) {
				Helpers.athrow(e);
			}
			locations.add(jv.getCodeSource());
		} else {
			locations.add(new CodeSource(url, (CodeSigner[]) null));
		}
		verifiers.add(jv);
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
			if (archives.isEmpty()) LOGGER.warn("无法读取注解：没有本地ZIP源");

			var repo = new AnnotationRepo();
			var buf = IOUtil.getSharedByteBuf();
			for (ZipFile archive : archives) {
				ZEntry entry = archive.getEntry("META-INF/annotations.repo");
				if (entry != null) {
					buf.clear();
					try {
						if (repo.deserialize(archive.get(entry, buf)))
							continue;
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