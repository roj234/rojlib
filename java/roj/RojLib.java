package roj;

import roj.collect.MyHashMap;
import roj.compiler.plugins.asm.ASM;
import roj.plugins.ci.annotation.ReplaceConstant;
import roj.reflect.ReflectionUtils;
import roj.reflect.litasm.Intrinsics;
import roj.reflect.litasm.LibraryLoader;
import roj.util.Helpers;
import roj.util.OS;
import roj.util.TypedKey;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Map;

/**
 * @author Roj233
 * @since 2021/10/15 12:57
 */
@ReplaceConstant
public final class RojLib {
	public static final Map<Object, Object> BLACKBOARD = new MyHashMap<>();
	/**
	 * 用于某些类加载顺序强相关的依赖注入
	 * @author Roj234
	 * @since 2024/7/22 0022 6:49
	 */
	public static Object inject(String s) {return BLACKBOARD.get(s);}
	public static <T> T inject(TypedKey<T> key) {return Helpers.cast(BLACKBOARD.get(key));}

	/**
	 * FastJNI
	 */
	public static void linkLibrary(int bit) {
		if ((bits&(1L<<bit)) == 0 || !Intrinsics.available()) return;
		Intrinsics.linkNative(getLibrary(), ReflectionUtils.getCallerClass(2));
	}
	public static Object getLibrary() {return LibraryLoader.INSTANCE.loadLibraryEx(RojLib.class, LibFile);}

	private static final String LibName = "libcpp";
	private static File LibFile = new File("D:\\mc\\MCMake\\projects\\rojlib-jni\\bin\\libcpp.dll");

	public static final boolean ASM_DEBUG = false;
	public static final boolean IS_DEV;
	public static final int GENERIC = 0, WIN32 = 1, ANSI_READBACK = 2, SHARED_MEMORY = 3, FAST_LZMA = 4, AES_NI = 5, FastJNI = 6;
	public static boolean hasNative(int bit) {return (bits&(1L << bit)) != 0;}

	public static boolean fastJni() {return Intrinsics.available();}
	private static final long bits;

	static {
		IS_DEV = LibFile.isFile();
		long t = 0;
		long mask = ~Long.getLong("roj.nativeDisableBit", 0);

		block:
		if (mask != 0) try {
			if (IS_DEV) System.load(LibFile.getAbsolutePath());
			else if (!loadLibrary()) break block;
			t = init();
		} catch (Throwable e) {
			e.printStackTrace();
		}

		bits = t & mask;
	}

	private static boolean loadLibrary() throws Exception {
		if (OS.ARCH == 64) {
			String ext = OS.CURRENT.libext();
			String hash = ASM.inject("libcpp_hash", "ab62fb3b");

			var in = RojLib.class.getClassLoader().getResourceAsStream(LibName + ext);
			if (in != null) {
				File tmp = new File(System.getProperty("java.io.tmpdir"), LibName + hash + ext);
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
				LibFile = tmp;
				return true;
			}
		}

		System.err.println("RojLib Warning: 您的平台没有可用的二进制，部分功能将不可用.");
		return false;
	}

	private static native long init();
}