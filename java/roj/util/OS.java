package roj.util;

import roj.reflect.ReflectionUtils;

import java.util.Locale;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public enum OS {
	WINDOWS, UNIX, JVM, OSX, UNKNOWN;
	public static final OS CURRENT = getOS();
	public static final int ARCH = getArch();

	private static int getArch() {
		return switch (ReflectionUtils.u.addressSize()) {
			case 4 -> 32;
			case 8 -> 64;
			default -> 0;
		};
	}

	private static OS getOS() {
		String property = System.getProperty("os.name").toLowerCase(Locale.ROOT);
		if (property.startsWith("windows")) return WINDOWS;
		if (property.startsWith("mac")) return OSX;
		return switch (property) {
			case "solaris", "sunos", "mpc/ix", "hp-ux", "os/390", "freebsd", "irix", "digital", "aix", "netware" -> UNIX;
			case "osf1", "openvms" -> JVM;
			default -> UNKNOWN;
		};
	}

	public String libext() {
		return switch (this) {
			case WINDOWS -> ".dll";
			case OSX -> ".dynlib";
			case UNIX -> ".so";
			default -> throw new IllegalArgumentException("Unsupported Operation System");
		};
	}
}
