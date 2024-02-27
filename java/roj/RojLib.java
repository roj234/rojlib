package roj;

import roj.collect.MyHashMap;
import roj.compiler.plugins.asm.ASM;
import roj.util.OS;

import java.io.File;
import java.io.FileOutputStream;
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
		String lib = OS.ARCH == 64 ? "libcpp" : "libcpp32";
		String ext = OS.CURRENT.libext();
		String hash = ASM.inject("libcpp_hash", "e6d0a146");// TODO dynamically inject version of libcpp

		var in = RojLib.class.getClassLoader().getResourceAsStream(lib+ext);
		if (in == null) {
			System.err.println("RojLib Warning: 您的平台没有可用的二进制，部分功能将不可用.");
			return false;
		}

		File tmp = new File(System.getProperty("java.io.tmpdir"), lib+hash+ext);
		if (!tmp.isFile()) {
			byte[] buf = new byte[4096];
			try (var out = new FileOutputStream(tmp)) {
				do {
					int r = in.read(buf);
					if (r <= 0) break;
					out.write(buf, 0, r);
				} while (true);
				in.close();
			}
		}

		System.load(tmp.getAbsolutePath());
		return true;
	}

	private static native long init();
}