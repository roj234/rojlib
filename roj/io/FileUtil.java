package roj.io;

import roj.RequireUpgrade;
import roj.concurrent.Waitable;
import roj.io.down.DownloadTask;
import roj.net.http.IHttpClient;
import roj.net.http.SyncHttpClient;
import roj.text.CharList;
import roj.text.TextUtil;
import roj.util.ByteList;
import roj.util.Helpers;

import java.io.*;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
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
public final class FileUtil {
	public static final MessageDigest MD5, SHA1;
	private static final int WHEN_USE_FILE_CACHE = 1048576;

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

	public static int timeout = 10000;

	public static void copyFile(File source, File target) throws IOException {
		try (FileChannel src = FileChannel.open(source.toPath(), StandardOpenOption.READ)) {
			try (FileChannel dst = FileChannel.open(target.toPath(), StandardOpenOption.WRITE, StandardOpenOption.CREATE)) {
				dst.truncate(source.length()).position(0);
				src.transferTo(0, source.length(), dst);
			}
		}
	}

	// readFully也许太占用内存了？
	public static void copyStream(InputStream in, OutputStream out) throws IOException {
		ByteList b = IOUtil.getSharedByteBuf();
		b.ensureCapacity(1024);
		byte[] data = b.list;

		while (true) {
			int len = in.read(data);
			if (len < 0) break;
			out.write(data, 0, len);
		}
	}

	public static List<File> findAllFiles(File file) {
		return findAllFiles(file, new ArrayList<>(), Helpers.alwaysTrue());
	}
	public static List<File> findAllFiles(File file, Predicate<File> predicate) {
		return findAllFiles(file, new ArrayList<>(), predicate);
	}
	public static List<File> findAllFiles(File file, List<File> files, Predicate<File> predicate) {
		File[] files1 = file.listFiles();
		if (files1 != null) {
			for (File file1 : files1) {
				if (file1.isDirectory()) {
					findAllFiles(file1, files, predicate);
				} else {
					if (predicate.test(file1)) {
						files.add(file1);
					}
				}
			}
		}
		return files;
	}

	public static boolean deletePath(File file) {
		File[] files = file.listFiles();
		boolean result = true;
		if (files != null) {
			for (File file1 : files) {
				if (file1.isDirectory()) {
					result &= deletePath(file1);
					result &= file1.delete();
				}
				result &= deleteFile(file1);
			}
		} else {
			result = false;
		}
		return result;
	}
	public static boolean deleteFile(File file) {
		return !file.exists() || file.delete();
	}

	@RequireUpgrade
	public static ByteList downloadFileToMemory(String url) throws IOException {
		IHttpClient con = process302(new URL(url));
		SyncHttpClient sbr = IHttpClient.syncWait(con, 15000, 1000);
		try {
			sbr.waitFor();
		} catch (InterruptedException e) {
			sbr.disconnect();
			throw new ClosedByInterruptException();
		}
		return sbr.getResult();
	}

	public static void allocSparseFile(File file, long length) throws IOException {
		if (file.length() != length) {
			if (!file.isFile() || file.length() < length) {
				file.delete();
				FileChannel fc = FileChannel.open(file.toPath(), StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW, StandardOpenOption.SPARSE).position(length - 1);
				fc.write(ByteBuffer.wrap(new byte[1]));
				fc.close();
			} else if (length < Integer.MAX_VALUE) {
				RandomAccessFile raf = new RandomAccessFile(file, "rw");
				raf.setLength(length); // alloc
				raf.close();
			}
		} else if (length == 0) file.createNewFile();
	}

	public static IHttpClient process302(URL url) throws IOException {
		DownloadTask task = new DownloadTask(url, null, null, null);
		task.operation = DownloadTask.REDIRECT_ONLY;
		DownloadTask.QUERY.pushTask(task);
		task.waitFor();
		return task.getClient();
	}

	public static boolean checkTotalWritePermission(File file) {
		try (RandomAccessFile f = new RandomAccessFile(file, "rw")) {
			long l = f.length();
			f.setLength(l + 1);
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
				File file = new File(path);
				while ((file = file.getParentFile()) != null) {
					if (file.isDirectory() && file.list().length == 0) {
						if (!file.delete()) System.err.println("无法删除目录 " + file);
						oneRemoved = true;
						i++;
					}
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

	public static String extensionName(String text) {
		text = text.substring(Math.max(text.lastIndexOf('/'), text.lastIndexOf('\\'))+1);
		return text.substring(text.lastIndexOf('.')+1);
	}

	public static String noExtName(String text) {
		text = text.substring(Math.max(text.lastIndexOf('/'), text.lastIndexOf('\\'))+1);
		return text.substring(0, text.lastIndexOf('.'));
	}

	public static String fileName(String text) {
		int i = text.lastIndexOf('/');
		i = Math.max(i, text.lastIndexOf('\\'));
		return text.substring(i+1);
	}

	public static String safePath(String path) {
		CharList sb = IOUtil.getSharedCharBuf().append(path);
		sb.trim()
		  .replace('\\', '/')
		  .replaceInReplaceResult("//", "/");

		if (sb.startsWith("../")) sb.delete(0, 3);

		return sb.replaceInReplaceResult("/../", "/")
				 .replaceInReplaceResult("//", "/")
				 .toString(sb.charAt(0) == '/' ? 1 : 0, sb.length());
	}
}