package roj.archive.zip;

import roj.io.IOUtil;
import roj.util.Helpers;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * @author Roj234
 * @since 2021/2/24 14:46
 */
public final class ZipUtil {
	public interface ICallback {
		void onRead(String fileName, InputStream stream) throws IOException;
	}

	public static void unzip(File file, ICallback callback, Predicate<ZipEntry> namePredicate) {
		if (file.length() == 0) return;
		ZipFile zf;
		try {
			zf = new ZipFile(file);
		} catch (IOException e) {
			throw new IllegalArgumentException("文件 " + file.getAbsolutePath() + " 无法正常读取!");
		}
		Enumeration<? extends ZipEntry> en = zf.entries();
		while (en.hasMoreElements()) {
			ZipEntry zn = en.nextElement();
			if (!zn.isDirectory() && namePredicate.test(zn)) {
				try (InputStream is = zf.getInputStream(zn)) {
					callback.onRead(zn.getName(), is);
				} catch (IOException e) {
					throw new IllegalArgumentException("文件 " + file.getAbsolutePath() + " 中的 " + zn.getName() + " 无法正常读取!");
				}
			}
		}
	}

	public static void unzip(File file, ICallback callback) {
		unzip(file, callback, Helpers.alwaysTrue());
	}

	public static void unzip(String fileName, String path) {
		ZipFile zf;
		try {
			zf = new ZipFile(new File(fileName));
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}
		Enumeration<? extends ZipEntry> en = zf.entries();
		while (en.hasMoreElements()) {
			ZipEntry zn = en.nextElement();
			if (!zn.isDirectory()) {
				try (InputStream is = zf.getInputStream(zn)) {
					try (FileOutputStream fos = new FileOutputStream(path + zn.getName())) {
						IOUtil.getSharedByteBuf().readStreamFully(is).writeToStream(fos);
					}
				} catch (IOException e) {
					throw new RuntimeException("Failed to write file data ", e);
				}
			} else {
				File dir = new File(path + zn.getName());
				if (!dir.exists() && !dir.mkdirs()) {
					throw new RuntimeException("Failed to create dir " + dir);
				}
			}
		}
	}
}