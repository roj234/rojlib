package roj;

import roj.util.OS;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * Native library loader
 *
 * @author Roj233
 * @since 2021/10/15 12:57
 */
public class NativeLibrary {
	public static final boolean IN_DEV = new File("D:\\mc\\FMD-1.5.2\\projects\\implib\\java").isDirectory();
	public static final boolean loaded;

	static {
		boolean t;
		try {
			File devFile = new File("D:\\mc\\FMD-1.5.2\\projects\\implib\\java\\libcpp\\bin\\libcpp.dll");
			if (devFile.isFile()) {
				System.load(devFile.getAbsolutePath());
				t = true;
			} else {
				t = loadLibrary();
			}
			if (t) init();
		} catch (Throwable e) {
			t = false;
			e.printStackTrace();
		}
		loaded = t;
	}

	private static boolean loadLibrary() throws Exception {
		String lib = System.getProperty("os.arch").contains("64") ? "libcpp" : "libcpp32";
		String appendix = OS.CURRENT == OS.WINDOWS ? ".dll" : ".so";
		InputStream in = NativeLibrary.class.getResourceAsStream("/"+lib+appendix);
		if (in == null) {
			System.err.println("Failed to load RojLib native");
			return false;
		}

		File tmp = new File(System.getProperty("java.io.tmpdir"));
		try {
			for (String s : tmp.list()) {
				if (s.startsWith(lib) && s.endsWith(appendix)) {
					System.load(new File(tmp, s).getAbsolutePath());
					return true;
				}
			}
		} catch (UnsatisfiedLinkError ex) {
			if (ex.getMessage().contains("Can't find dependent libraries")) throw ex;
		}
		File tempFile = new File(tmp, lib+"-"+Long.toHexString(Math.abs(System.nanoTime()))+appendix);
		byte[] buf = new byte[4096];
		try (FileOutputStream out = new FileOutputStream(tempFile)) {
			do {
				int read = in.read(buf);
				if (read <= 0) break;
				out.write(buf, 0, read);
			} while (true);
			in.close();
		}
		System.load(tempFile.getAbsolutePath());
		return true;
	}

	private static native void init();
}
