package roj.io;

import roj.collect.SimpleList;
import roj.concurrent.FastThreadLocal;
import roj.concurrent.Waitable;
import roj.math.MutableLong;
import roj.net.http.HttpRequest;
import roj.text.CharList;
import roj.text.TextReader;
import roj.text.TextUtil;
import roj.text.UTFCoder;
import roj.util.ByteList;
import roj.util.Helpers;

import java.io.*;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.UnsupportedCharsetException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;

/**
 * @author Roj234
 * @since 2021/5/29 22:1
 */
public final class IOUtil {
	public static final FastThreadLocal<UTFCoder> SharedCoder = FastThreadLocal.withInitial(UTFCoder::new);

	public static ByteList ddLayeredByteBuf() {
		ByteList bb = new ByteList();
		bb.ensureCapacity(1024);
		return bb;
	}
	public static CharList ddLayeredCharBuf() {
		CharList sb = new CharList();
		sb.ensureCapacity(1024);
		return sb;
	}

	// 上面给API用，下面给实际代码用

	public static ByteList getSharedByteBuf() {
		ByteList o = SharedCoder.get().byteBuf;
		o.clear();
		o.ensureCapacity(1024);
		return o;
	}

	public static CharList getSharedCharBuf() {
		CharList o = SharedCoder.get().charBuf;
		o.clear();
		o.ensureCapacity(1024);
		return o;
	}

	public static String readResUTF(String path) throws IOException { return readResUTF(IOUtil.class, path); }
	public static String readResUTF(Class<?> jar, String path) throws IOException {
		ClassLoader cl = jar.getClassLoader();
		InputStream in = cl == null ? ClassLoader.getSystemResourceAsStream(path) : cl.getResourceAsStream(path);
		if (in == null) return null;
		return readAs(in, "UTF-8");
	}

	public static byte[] readRes(String path) throws IOException { return read1(IOUtil.class, path, getSharedByteBuf()).toByteArray(); }
	public static byte[] readRes(Class<?> jar, String path) throws IOException { return read1(jar, path, getSharedByteBuf()).toByteArray(); }
	private static ByteList read1(Class<?> jar, String path, ByteList list) throws IOException {
		InputStream in = jar.getClassLoader().getResourceAsStream(path);
		if (in == null) throw new FileNotFoundException(path + " is not in jar " + jar.getName());
		return list.readStreamFully(in);
	}

	public static byte[] read(File file) throws IOException {
		try(FileInputStream in = new FileInputStream(file)) {
			return read(in);
		}
	}
	public static byte[] read(InputStream in) throws IOException { return getSharedByteBuf().readStreamFully(in).toByteArray(); }

	public static String read(Reader in) throws IOException {
		return getSharedCharBuf().readFully(in).toString();
	}

	public static String readAs(InputStream in, String encoding) throws UnsupportedCharsetException, IOException {
		try (Reader r = new TextReader(in, encoding)) {
			return getSharedCharBuf().readFully(r).toString();
		}
	}

	public static String readUTF(File f) throws IOException {
		try(FileInputStream in = new FileInputStream(f)) {
			return readUTF(in);
		}
	}
	public static String readUTF(InputStream in) throws IOException {
		UTFCoder x = SharedCoder.get();
		x.decodeFrom(in);
		return x.charBuf.toString();
	}

	public static String readString(File file) throws IOException {
		try (TextReader sr = TextReader.auto(file)) {
			return new CharList().readFully(sr).toStringAndFree();
		}
	}

	public static String readString(InputStream in) throws IOException {
		try (TextReader sr = TextReader.auto(in)) {
			return new CharList().readFully(sr).toStringAndFree();
		}
	}

	public static void write(CharSequence cs, File out) throws IOException {
		try (FileOutputStream fos = new FileOutputStream(out)) {
			SharedCoder.get().encodeTo(cs, fos);
		}
	}

	public static void write(CharSequence cs, OutputStream out) throws IOException {
		SharedCoder.get().encodeTo(cs, out);
	}

	public static void readFully(InputStream in, byte[] b) throws IOException { readFully(in, b, 0, b.length); }
	public static void readFully(InputStream in, byte[] b, int off, int len) throws IOException {
		while (len > 0) {
			int r = in.read(b, off, len);
			if (r < 0) throw new EOFException();
			len -= r;
			off += r;
		}
	}

	public static final MessageDigest MD5, SHA1;
	static {
		MessageDigest MD, SH;
		try {
			MD = MessageDigest.getInstance("md5");
			SH = MessageDigest.getInstance("sha1");
		} catch (NoSuchAlgorithmException e) {
			System.err.println("MD5/SHA1 Algorithm not found!");
			System.exit(-2);
			MD = null;
			SH = null;
		}
		MD5 = MD;
		SHA1 = SH;
	}

	public static void copyFile(File source, File target) throws IOException {
		Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
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

	public static List<File> findAllFiles(File file) {
		return findAllFiles(file, SimpleList.withCapacityType(0,2), Helpers.alwaysTrue());
	}
	public static List<File> findAllFiles(File file, Predicate<File> predicate) {
		return findAllFiles(file, SimpleList.withCapacityType(0,2), predicate);
	}
	public static List<File> findAllFiles(File file, List<File> files, Predicate<File> predicate) {
		try {
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
	public static boolean deleteFile(File file) {
		try {
			return Files.deleteIfExists(file.toPath());
		} catch (IOException e) {
			return false;
		}
	}

	public static void allocSparseFile(File file, long length) throws IOException {
		// noinspection all
		if (file.length() != length) {
			// noinspection all
			if (!file.isFile() || file.length() < length) {
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

	public static boolean checkTotalWritePermission(File file) {
		try (RandomAccessFile f = new RandomAccessFile(file, "rw")) {
			long l = f.length();
			f.seek(l);
			f.writeByte(1);
			f.setLength(l);
		} catch (IOException e) {
			return false;
		}
		return true;
	}

	public static int removeEmptyPaths(Collection<String> files) {
		boolean oneRemoved = true;
		int i = 0;
		while (oneRemoved) {
			oneRemoved = false;
			for (String path : files) {
				File dir = new File(path);
				while ((dir = dir.getParentFile()) != null) {
					if (!dir.delete()) break;

					oneRemoved = true;
					i++;
				}
			}
		}
		return i;
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
					NIOUtil.clean(direct);
				}
			} else {
				File tmpPath = new File(System.getProperty("java.io.tmpdir"));
				File tmpFile;
				do {
					tmpFile = new File(tmpPath, "FUT~" + (float) Math.random() + ".tmp");
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

	public static String extensionName(String path) {
		path = path.substring(Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'))+1);
		return path.substring(path.lastIndexOf('.')+1);
	}

	public static String noExtName(String path) {
		path = path.substring(Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'))+1);
		int i = path.lastIndexOf('.');
		return i < 0 ? path : path.substring(0, i);
	}

	public static String fileName(String text) {
		int i = text.lastIndexOf('/');
		i = Math.max(i, text.lastIndexOf('\\'));
		return text.substring(i+1);
	}

	public static String safePath(String path) {
		CharList sb = getSharedCharBuf().append(path);
		sb.trim()
		  .replace('\\', '/')
		  .replaceInReplaceResult("//", "/");

		if (sb.startsWith("../")) sb.delete(0, 3);

		return sb.replaceInReplaceResult("/../", "/")
				 .replaceInReplaceResult("//", "/")
				 .toString(sb.charAt(0) == '/' ? 1 : 0, sb.length());
	}

	// todo 支持HTTP2.0后移走
	public static int timeout = 10000;

	public static ByteList downloadFileToMemory(String url) throws IOException {
		return HttpRequest.nts().url(new URL(url)).execute().bytes();
	}

	public static final class ImmediateFuture implements Waitable {
		@Override
		public void waitFor() {}

		@Override
		public boolean isDone() {
			return true;
		}

		@Override
		public void cancel() {}
	}

	public static long movePath(File from, File to, boolean move) throws IOException {
		if (!from.isDirectory()) {
			if (move) return from.renameTo(to) ? 1 : 0;
			copyFile(from, to);
			return 1;
		}
		if (from.equals(to)) return 1;

		MutableLong state = new MutableLong();
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
}