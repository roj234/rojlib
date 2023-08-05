package roj.util;

/**
 * @author Roj234
 * @since 2023/12/26 9:09
 */
public class VMUtil {
	private static Boolean isRoot;

	public static synchronized Boolean isRoot() {
		if (isRoot == null) {
			if (OS.CURRENT == OS.WINDOWS) {
				try {
					int i = new ProcessBuilder("REG","QUERY","HKU\\S-1-5-19").start().waitFor();
					isRoot = i == 0;
				} catch (Exception e) {
					isRoot = false;
				}
			}
		}
		return isRoot;
	}

	public static long usableMemory() { Runtime r = Runtime.getRuntime(); return r.maxMemory() - r.totalMemory() + r.freeMemory(); }
}
