package roj.io;

import org.jetbrains.annotations.Nullable;
import roj.collect.SimpleList;
import roj.concurrent.FastThreadLocal;
import roj.config.data.CLong;
import roj.crypt.Base64;
import roj.text.CharList;
import roj.text.TextReader;
import roj.text.TextUtil;
import roj.text.UTF8MB4;
import roj.util.ByteList;
import roj.util.Helpers;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;

/**
 * @author Roj234
 * @since 2021/5/29 22:1
 */
public final class IOUtil {
	public static final FastThreadLocal<IOUtil> SharedCoder = FastThreadLocal.withInitial(IOUtil::new);

	// region ThreadLocal part
	private final CharList charBuf = new CharList(1024);
	public final ByteList byteBuf = new ByteList();
	{byteBuf.ensureCapacity(1024);}

	private final ByteList.Slice shell = new ByteList.Slice();
	public final ByteList.Slice shellB = new ByteList.Slice();

	public final StringBuilder numberHelper = new StringBuilder(32);

	public byte[] encode(CharSequence str) {
		ByteList b = byteBuf; b.clear();
		return b.putUTFData(str).toByteArray();
	}
	public String decode(byte[] b) {
		CharList c = charBuf; c.clear();
		UTF8MB4.CODER.decodeFixedIn(shell.setR(b,0,b.length), b.length, c);
		return c.toString();
	}

	public String encodeHex(byte[] b) { return shell.setR(b,0,b.length).hex(); }
	public String encodeBase64(byte[] b) { return shell.setR(b,0,b.length).base64(); }

	public byte[] decodeHex(CharSequence c) {
		ByteList b = byteBuf; b.clear();
		return TextUtil.hex2bytes(c, b).toByteArray();
	}

	public ByteList decodeBase64(CharSequence c) { return decodeBase64(c, Base64.B64_CHAR_REV); }
	public ByteList decodeBase64(CharSequence c, byte[] chars) {
		ByteList b = byteBuf; b.clear();
		Base64.decode(c, 0, c.length(), b, chars);
		return b;
	}

	public ByteList wrap(byte[] b) { return shell.setR(b,0,b.length); }
	public ByteList wrap(byte[] b, int off, int len) { return shell.setR(b,off,len); }
	// endregion

	public static ByteList getSharedByteBuf() {
		ByteList o = SharedCoder.get().byteBuf; o.clear();
		return o;
	}

	public static CharList getSharedCharBuf() {
		CharList o = SharedCoder.get().charBuf; o.clear();
		return o;
	}

	public static byte[] getResource(String path) throws IOException { return getResource(IOUtil.class, path); }
	public static byte[] getResource(Class<?> caller, String path) throws IOException {
		InputStream in = caller.getClassLoader().getResourceAsStream(path);
		if (in == null) throw new FileNotFoundException(path+" is not in jar "+caller.getName());
		return read(in);
	}
	public static String getTextResource(String path) throws IOException { return getTextResource(IOUtil.class, path); }
	public static String getTextResource(Class<?> caller, String path) throws IOException {
		ClassLoader cl = caller.getClassLoader();
		InputStream in = cl == null ? ClassLoader.getSystemResourceAsStream(path) : cl.getResourceAsStream(path);
		if (in == null) throw new FileNotFoundException(path+" is not in jar "+caller.getName());
		return readUTF(in);
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
		try (Reader r = TextReader.from(f, StandardCharsets.UTF_8)) {
			return f.length() > 1048576L ? read1(r) : read(r);
		}
	}
	public static String readUTF(InputStream in) throws IOException {
		try (Reader r = TextReader.from(in, StandardCharsets.UTF_8)) {
			return in.available() > 1048576L ? read1(r) : read(r);
		}
	}
	public static String readString(File f) throws IOException {
		try (TextReader r = TextReader.auto(f)) {
			return f.length() > 1048576L ? read1(r) : read(r);
		}
	}
	public static String readString(InputStream in) throws IOException {
		try (Reader r = TextReader.auto(in)) {
			return in.available() > 1048576L ? read1(r) : read(r);
		}
	}

	public static String read(Reader r) throws IOException { return getSharedCharBuf().readFully(r).toString(); }
	private static String read1(Reader r) throws IOException { return new CharList(1048576).readFully(r).toStringAndFree(); }

	public static void readFully(InputStream in, byte[] b) throws IOException { readFully(in, b, 0, b.length); }
	public static void readFully(InputStream in, byte[] b, int off, int len) throws IOException {
		while (len > 0) {
			int r = in.read(b, off, len);
			if (r < 0) throw new EOFException();
			len -= r;
			off += r;
		}
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

	public static void allocSparseFile(File file, long length) throws IOException {
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
		int i = path.lastIndexOf('.');
		return i < 0 ? "" : path.substring(i+1);
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
	 * no URLDecode
	 * no \ TO /
	 * @param path
	 * @return
	 */
	public static String safePath(String path) {
		CharList sb = getSharedCharBuf();

		int altStream = path.lastIndexOf(':');
		if (altStream > 1) path = path.substring(0, altStream);

		return sb.append(path).trim()
				 .replaceInReplaceResult("../", "/")
				 .replaceInReplaceResult("//", "/")
				 .toString(sb.length() > 0 && sb.charAt(0) == '/' ? 1 : 0, sb.length());
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

	public static void closeSilently(@Nullable AutoCloseable c) {
		if (c != null) try {
			c.close();
		} catch (Throwable e) {
			e.printStackTrace();
		}
	}

	public static void ioWait(AutoCloseable closeable, Object waiter) throws IOException {
		synchronized (waiter) {
			try {
				waiter.wait();
			} catch (InterruptedException e) {
				ClosedByInterruptException ex2 = new ClosedByInterruptException();
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