package roj.util;

import roj.io.IOUtil;
import roj.reflect.Unaligned;

/**
 * @author Roj234
 * @since 2023/12/26 9:09
 */
public class VMUtil {
	private static final Class<?> Hooks_Instance;
	private static final long Hooks_Offset;
	static {
		try {
			// might not applicable for Java8
			Hooks_Instance = Class.forName("java.lang.ApplicationShutdownHooks");
			Hooks_Offset = Unaligned.fieldOffset(Hooks_Instance, "hooks");
		} catch (ClassNotFoundException e) {
			throw new RuntimeException(e);
		}
	}
	public static boolean isShutdownInProgress() {return Unaligned.U.getReference(Hooks_Instance, Hooks_Offset) == null;}

	private static Boolean isRoot;
	public static synchronized Boolean isRoot() {
		if (isRoot == null) {
			try {
				if (OS.CURRENT == OS.WINDOWS) {
					int exitCode = new ProcessBuilder("REG","QUERY","HKU\\S-1-5-19").start().waitFor();
					isRoot = exitCode == 0;
				} else {
					var id = new ProcessBuilder("id").start();
					ByteList buf = IOUtil.getSharedByteBuf().readStreamFully(id.getInputStream());
					isRoot = buf.readAscii(buf.readableBytes()).contains("0(root)");
				}
			} catch (Exception e) {
				isRoot = false;
			}
		}
		return isRoot;
	}

	public static long usableMemory() { Runtime r = Runtime.getRuntime(); return r.maxMemory() - r.totalMemory() + r.freeMemory(); }
}