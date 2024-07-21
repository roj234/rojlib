package roj;

import roj.collect.MyHashMap;
import roj.util.OS;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Map;

/**
 * @author Roj233
 * @since 2021/10/15 12:57
 */
public final class RojLib {
	public static final Map<String, Object> DATA = new MyHashMap<>();
	/**
	 * 用于某些类加载顺序强相关的依赖注入
	 * @author Roj234
	 * @since 2024/7/22 0022 6:49
	 */
	public static Object inject(String s) {return DATA.get(s);}

	public static final boolean IS_DEV = new File("D:\\mc\\FMD-1.5.2").isDirectory();

	public static final int WIN32 = 0, ANSI_READBACK = 1, BSDIFF = 2, SHARED_MEMORY = 3, FAST_LZMA = 4;
	public static boolean hasNative(int bit) {return (bits&(1L << bit)) != 0;}
	private static final long bits;

	static {
		long t = 0;

		block:
		try {
			File devFile = new File("D:\\mc\\FMD-1.5.2\\projects\\implib\\libcpp\\bin\\libcpp.dll");
			if (devFile.isFile()) System.load(devFile.getAbsolutePath());
			else if (!loadLibrary()) break block;
			t = init();
		} catch (Throwable e) {
			e.printStackTrace();
		}

		bits = t;
	}

	private static boolean loadLibrary() throws Exception {
		String lib = System.getProperty("os.arch").contains("64") ? "libcpp" : "libcpp32";
		String appendix = OS.CURRENT == OS.WINDOWS ? ".dll" : ".so";
		InputStream in = RojLib.class.getClassLoader().getResourceAsStream(lib+appendix);
		if (in == null) {
			System.err.println("RojLib Warning: 您的平台没有可用的二进制，部分功能将不可用.");
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

	private static native long init();
}