package roj.io;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import roj.collect.SimpleList;
import roj.compiler.plugins.annotations.Attach;
import roj.concurrent.FastThreadLocal;
import roj.config.data.CInt;
import roj.config.data.CLong;
import roj.crypt.Base64;
import roj.reflect.ReflectionUtils;
import roj.text.*;
import roj.util.ByteList;
import roj.util.Helpers;
import roj.util.NativeMemory;

import java.io.*;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * @author Roj234
 * @since 2021/5/29 22:1
 */
public final class IOUtil {
	public static final FastThreadLocal<IOUtil> SharedBuf = FastThreadLocal.withInitial(IOUtil::new);

	// region ThreadLocal part
	public final CharList charBuf = new CharList(1024);
	public final ByteList byteBuf = new ByteList(1024);

	private final ByteList.Slice slice = new ByteList.Slice();
	@Deprecated
	public final ByteList.Slice slice1 = new ByteList.Slice();

	public final StringBuilder numberHelper = new StringBuilder(32);

	public static byte[] encodeUTF8(CharSequence str) {return getSharedByteBuf().putUTFData(str).toByteArray();}
	public static String decodeUTF8(byte[] bytes) {
		var TL = SharedBuf.get();
		var sb = TL.charBuf; sb.clear();
		FastCharset.UTF8().decodeFixedIn(TL.slice.setR(bytes, 0, bytes.length), bytes.length, sb);
		return sb.toString();
	}

	public static String encodeBase64(byte[] bytes) {
		var TL = SharedBuf.get();
		var sb = TL.charBuf; sb.clear();
		return Base64.encode(TL.slice.setR(bytes,0, bytes.length), sb).toString();
	}
	public static ByteList decodeBase64(CharSequence str) {
		var buf = getSharedByteBuf();
		Base64.decode(str, 0, str.length(), buf, Base64.B64_CHAR_REV);
		return buf;
	}

	public static String encodeHex(byte[] bytes) {return TextUtil.bytes2hex(bytes, getSharedCharBuf()).toString();}
	public static byte[] decodeHex(CharSequence str) {return TextUtil.hex2bytes(str, getSharedByteBuf()).toByteArray();}

	@Deprecated public ByteList wrap(byte[] b) { return slice.setR(b,0,b.length); }
	@Deprecated public ByteList wrap(byte[] b, int off, int len) { return slice.setR(b,off,len); }
	// endregion

	public static ByteList getSharedByteBuf() {
		ByteList o = SharedBuf.get().byteBuf; o.clear();
		return o;
	}

	public static CharList getSharedCharBuf() {
		CharList o = SharedBuf.get().charBuf; o.clear();
		return o;
	}

	//region InputStream/TextReader
	public static byte[] getResourceIL(String path) throws IOException { return getResource(path, IOUtil.class); }
	public static byte[] getResource(String path) throws IOException { return getResource(path, ReflectionUtils.getCallerClass(2)); }
	public static byte[] getResource(String path, Class<?> caller) throws IOException {
		var in = caller.getClassLoader().getResourceAsStream(path);
		if (in == null) throw new FileNotFoundException(path+" is not in jar "+caller.getName());
		return new ByteList(Math.max(in.available(), 4096)).readStreamFully(in).toByteArrayAndFree();
	}
	public static String getTextResourceIL(String path) throws IOException { return getTextResource(path, IOUtil.class); }
	public static String getTextResource(String path) throws IOException { return getTextResource(path, ReflectionUtils.getCallerClass(2)); }
	public static String getTextResource(String path, Class<?> caller) throws IOException {
		var in = caller.getClassLoader().getResourceAsStream(path);
		if (in == null) throw new FileNotFoundException(path+" is not in jar "+caller.getName());
		try (var r = TextReader.from(in, StandardCharsets.UTF_8)) {
			return new CharList(Math.max(in.available()/3, 4096)).readFully(r).toStringAndFree();
		}
	}

	public static byte[] read(File file) throws IOException {
		long len = file.length();
		if (len > Integer.MAX_VALUE) throw new IOException("file > 2GB");
		byte[] data = new byte[(int) len];
		try (FileInputStream in = new FileInputStream(file)) {
			readFully(in, data);
			return data;
		}
	}
	public static byte[] read(InputStream in) throws IOException { return getSharedByteBuf().readStreamFully(in).toByteArray(); }


	public static String readUTF(File f) throws IOException {
		try (var r = TextReader.from(f, StandardCharsets.UTF_8)) {
			return f.length() > 1048576L ? read1(r) : read(r);
		}
	}
	public static String readUTF(InputStream in) throws IOException {
		try (var r = TextReader.from(in, StandardCharsets.UTF_8)) {
			return in.available() > 1048576L ? read1(r) : read(r);
		}
	}
	public static String readString(File f) throws IOException {
		try (var r = TextReader.auto(f)) {
			return f.length() > 1048576L ? read1(r) : read(r);
		}
	}
	public static String readString(InputStream in) throws IOException {
		try (var r = TextReader.auto(in)) {
			return in.available() > 1048576L ? read1(r) : read(r);
		}
	}

	public static String read(Reader r) throws IOException { return getSharedCharBuf().readFully(r).toString(); }
	private static String read1(Reader r) throws IOException { return new CharList(1048576).readFully(r).toStringAndFree(); }

	@Attach
	public static void readFully(InputStream in, byte[] b) throws IOException {readFully(in, b, 0, b.length);}
	@Attach
	public static void readFully(InputStream in, byte[] b, int off, int len) throws IOException {
		while (len > 0) {
			int r = in.read(b, off, len);
			if (r < 0) throw new EOFException();
			len -= r;
			off += r;
		}
	}

	@Attach("skipAlt")
	public static long skip(InputStream in, long count) throws IOException {
		long start = count;
		for(;;) {
			long i = in.skip(count);
			if (i == 0) break;
			count -= i;
			if (count == 0) break;
		}
		return start-count;
	}
	@Attach
	public static void skipFully(InputStream in, long count) throws IOException {
		if (skip(in, count) < count) throw new EOFException(in+"无法跳过"+count+"个字节");
	}

	public static void copyStream(InputStream in, OutputStream out) throws IOException {
		ByteList b = getSharedByteBuf();
		byte[] data = b.list;

		while (true) {
			int len = in.read(data);
			if (len < 0) break;
			out.write(data, 0, len);
		}
	}
	//endregion

	/**
	 * 获取这个类所属的jar
	 */
	public static File getJar(Class<?> clazz) {
		URL location = clazz.getProtectionDomain().getCodeSource().getLocation();
		if (location == null) return null;
		String loc = location.getPath();
		if (loc.startsWith("file:")) loc = loc.substring(5);
		int i = loc.lastIndexOf('!');
		loc = loc.substring(loc.startsWith("/")?1:0, i<0?loc.length():i);
		try {
			loc = Escape.decodeURI(loc);
			i = loc.lastIndexOf('#');
			loc = loc.substring(0, i<0?loc.length():i);
			return new File(loc).getCanonicalFile();
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	public static void copyFile(File source, File target) throws IOException {Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);}

	@Attach("listAll")
	public static List<File> findAllFiles(File file) {return findAllFiles(file, SimpleList.hugeCapacity(0), Helpers.alwaysTrue());}
	@Attach("listAll")
	public static List<File> findAllFiles(File file, Predicate<File> predicate) {return findAllFiles(file, SimpleList.hugeCapacity(0), predicate);}
	@Attach("listAll")
	public static List<File> findAllFiles(File file, List<File> files, Predicate<File> predicate) {
		try {
			if (!file.exists()) return Collections.emptyList();
			Files.walkFileTree(file.toPath(), new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
					File t = file.toFile();
					if (predicate.test(t)) files.add(t);
					return FileVisitResult.CONTINUE;
				}
			});
		} catch (IOException e) {
			e.printStackTrace();
		}
		return files;
	}

	@Attach
	public static boolean deletePath(File file) {
		try {
			Files.walkFileTree(file.toPath(), new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
					Files.delete(file);
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
					Files.delete(dir);
					if (exc != null) throw exc;
					return FileVisitResult.CONTINUE;
				}
			});
		} catch (IOException e) {
			return false;
		}
		return true;
	}

	@Attach
	public static int removeEmptyPaths(File file) {
		CInt tmp = new CInt();
		try {
			Files.walkFileTree(file.toPath(), new SimpleFileVisitor<Path>() {
				int size;
				@Override
				public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {size++;return FileVisitResult.CONTINUE;}
				@Override
				public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
					if (--size > 0 && dir.toFile().delete()) tmp.value++;
					return FileVisitResult.CONTINUE;
				}
			});
		} catch (IOException ignored) {}
		return tmp.value;
	}

	@Attach
	public static void createSparseFile(File file, long length) throws IOException {
		// noinspection all
		if (file.length() != length) {
			// noinspection all
			if (!file.isFile() || (file.length() < length && file.delete())) {
				FileChannel fc = FileChannel.open(file.toPath(), StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW, StandardOpenOption.SPARSE)
											.position(length-1);
				fc.write(ByteBuffer.wrap(new byte[1]));
				fc.close();
			} else if (length < Integer.MAX_VALUE) {
				RandomAccessFile raf = new RandomAccessFile(file, "rw");
				raf.setLength(length); // alloc
				raf.close();
			}
		} else if (length == 0) file.createNewFile();
	}

	public static boolean isReallyWritable(File file) {
		try (var f = new RandomAccessFile(file, "rw")) {
			long l = f.length();
			f.seek(l);
			f.writeByte(1);
			f.setLength(l);
		} catch (IOException e) {
			return false;
		}
		return true;
	}

	public static long transferFileSelf(FileChannel cf, long from, long to, long len) throws IOException {
		if (from == to || len == 0) return len;

		long pos = cf.position();
		try {
			if (from > to ? to + len <= from : from + len <= to) { // 区间不交叉
				return cf.transferTo(from, len, cf.position(to));
			}

			if (len <= 1048576) {
				ByteBuffer direct = ByteBuffer.allocateDirect((int) len);
				try {
					direct.position(0).limit((int) len);
					cf.read(direct, from);
					direct.position(0);
					return cf.position(to).write(direct);
				} finally {
					NativeMemory.freeDirectBuffer(direct);
				}
			} else {
				File tmpPath = new File(System.getProperty("java.io.tmpdir"));
				File tmpFile;
				do {
					tmpFile = new File(tmpPath, "FUT~"+(float)Math.random()+".tmp");
				} while (tmpFile.exists());

				try (FileChannel ct = FileChannel.open(tmpFile.toPath(),
					StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, StandardOpenOption.READ, StandardOpenOption.DELETE_ON_CLOSE)) {
					cf.transferTo(from, len, ct.position(0));
					return ct.transferTo(0, len, cf.position(to));
				}
			}
		} finally {
			// 还原位置
			cf.position(pos+len);
		}
	}

	public static boolean writeFileEvenMoreSafe(File baseFolder, String baseName, Consumer<File> consumer) throws IOException {
		var realFile = new File(baseFolder, baseName);
		var tmpFile = new File(baseFolder, baseName+"."+System.nanoTime()+".tmp");
		var deletePend = new File(baseFolder, baseName+".delete_pend");

		if (!realFile.isFile()) tmpFile = realFile;

		try {
			consumer.accept(tmpFile);
		} catch (Throwable e) {
			if (!tmpFile.delete()) {
				tmpFile.deleteOnExit();
			}
			throw e;
		}

		if (tmpFile == realFile) return true;

		deletePend.delete();
		return realFile.renameTo(deletePend) && tmpFile.renameTo(realFile) && deletePend.delete();
	}

	public static CharList mavenPath(CharSequence name) {
		int i = TextUtil.gIndexOf(name, ':');
		CharList cl = new CharList().append(name).replace('.', '/', 0, i);
		List<String> parts = TextUtil.split(new ArrayList<>(4), cl, ':');

		String ext = "jar";
		final String s = parts.get(parts.size() - 1);
		int extPos = s.lastIndexOf('@');
		if (extPos != -1) {
			ext = s.substring(extPos + 1);
			parts.set(parts.size() - 1, s.substring(0, extPos));
		}

		cl.clear();
		cl.append(parts.get(0)).append('/') // d
		  .append(parts.get(1)).append('/') // n
		  .append(parts.get(2)).append('/') // v
		  .append(parts.get(1)).append('-').append(parts.get(2)); // n-v

		if (parts.size() > 3) {
			cl.append('-').append(parts.get(3));
		}

		return cl.append('.').append(ext);
	}

	public static File deriveOutput(File src, String postfix) {
		var path = src.getName();
		int i = path.lastIndexOf('.');
		if (i < 0) return new File(src.getAbsolutePath()+postfix);
		return new File(src.getParentFile(), path.substring(0, i)+postfix+path.substring(i));
	}

	public static String extensionName(String path) {
		path = path.substring(Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'))+1);
		int i = path.lastIndexOf('.');
		return i < 0 ? "" : path.substring(i+1).toLowerCase(Locale.ROOT);
	}

	public static String fileName(String pat) {
		pat = pat.substring(Math.max(pat.lastIndexOf('/'), pat.lastIndexOf('\\'))+1);
		int i = pat.lastIndexOf('.');
		return i < 0 ? pat : pat.substring(0, i);
	}

	public static String pathToName(String text) {
		int i = text.lastIndexOf('/');
		i = Math.max(i, text.lastIndexOf('\\'));
		return text.substring(i+1);
	}

	/**
	 * 路径过滤函数，处理不可信的用户输入路径.
	 */
	public static String safePath(String path) {return safePath(path, true);}
	@NotNull
	public static String safePath(String path, boolean silent) throws InvalidPathException {
		CharList sb = getSharedCharBuf();

		int end = 0;
		int i = 0;
		while (i < path.length()) {
			var c = path.charAt(i);
			// 分号
			if (c < 32 || c == File.pathSeparatorChar) {
				if (!silent) throw new InvalidPathException(path, "illegal char", i);
				break;
			}
			i++;
			end = i;
		}

		sb.append(path, 0, end).trim()
		  .replace(File.separatorChar, '/') // 在linux上，\是合法的文件名组成部分，而不是路径分隔符，而windows上二者皆是
		  .replaceInReplaceResult("//", "/");

		var paths = TextUtil.split(sb, '/');
		if (sb.endsWith("/")) paths.add("");
		for (int j = 0; j < paths.size();) {
			var s = paths.get(j);
			if (s.equals(".")) {
				paths.remove(j);
			} else if (s.equals("..")) {
				paths.remove(j);
				if (j > 0) paths.remove(--j);
				else if (!silent) throw new InvalidPathException(path, "illegal directory", j);
			} else {
				j++;
			}
		}
		if (!paths.isEmpty() && paths.get(0).isEmpty())
			paths.remove(0);
		return TextUtil.join(paths, "/");
	}
	/**
	 * 这是另外一个实现
	 * @param base 允许访问的目录
	 * @param relative 不可信的用户输入
	 * @return 如果输入合法，那么返回非空
	 */
	@Nullable
	public static File safePath2(String base, String relative) {
		File file = new File(base, relative);
		try {
			if (file.getCanonicalPath().startsWith(base)) return file;
		} catch (IOException ignored) {}
		return null;
	}
	public static File relativePath(File relativeBase, String myPath) {
		File pathFile = new File(myPath);
		if (pathFile.isAbsolute()) {
			return pathFile;
		} else {
			return new File(relativeBase, myPath);
		}
	}

	public static long movePath(File from, File to, boolean move) throws IOException {
		if (!from.isDirectory()) {
			if (move) return from.renameTo(to) ? 1 : 0;
			copyFile(from, to);
			return 1;
		}
		if (from.equals(to)) return 1;

		CLong state = new CLong();
		int len = from.getAbsolutePath().length()+1;
		to.mkdirs();
		Files.walkFileTree(from.toPath(), new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
				String relpath = dir.toString();
				if (relpath.length() > len) {
					relpath = relpath.substring(len);
					if (!new File(to, relpath).mkdir()) {
						state.value |= Long.MIN_VALUE;
						return FileVisitResult.TERMINATE;
					} else {
						state.value += 1L << 20;
					}
				}
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				File from = file.toFile();
				File target = new File(to, file.toString().substring(len));
				if (move) {
					if (from.renameTo(target)) {
						state.value++;
					} else {
						state.value |= Long.MIN_VALUE;
						return FileVisitResult.TERMINATE;
					}
				} else {
					copyFile(from, target);
					state.value++;
				}
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
				if (move) dir.toFile().delete();
				return FileVisitResult.CONTINUE;
			}
		});
		return state.value;
	}

	@Attach
	public static void closeSilently(@Nullable AutoCloseable c) {
		if (c != null) try {
			c.close();
		} catch (Throwable e) {
			new UnsupportedOperationException(c.getClass()+"在关闭时抛出了异常！", e).printStackTrace();
		}
	}

	public static void ioWait(AutoCloseable closeable, Object waiter) throws IOException {
		synchronized (waiter) {
			try {
				waiter.wait();
			} catch (InterruptedException e) {
				var ex2 = new ClosedByInterruptException();
				try {
					closeable.close();
				} catch (Throwable ex) {
					ex2.addSuppressed(ex);
				}
				throw ex2;
			}
		}
	}
}