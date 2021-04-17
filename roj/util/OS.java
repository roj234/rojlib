package roj.util;

import java.util.Locale;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public enum OS {
	WINDOWS, UNIX, JVM, MAC_OS, UNKNOWN;
	public static final OS CURRENT = getOS();
	public static final String ARCH = getArch();

	private static String getArch() {
		switch (System.getProperty("os.arch", "")) {
			case "amd64":
			case "x86_64":
				return "64";
			case "x86":
			case "i386":
				return "32";
			default:
				return "UNKNOWN";
		}
	}

	private static OS getOS() {
		String property = System.getProperty("os.name").toLowerCase(Locale.ROOT);
		if (property.startsWith("windows")) return WINDOWS;
		if (property.startsWith("mac")) return MAC_OS;
		switch (property) {
			case "solaris":
			case "sunos":
			case "mpc/ix":
			case "hp-ux":
			case "os/390":
			case "freebsd":
			case "irix":
			case "digital":
			case "aix":
			case "netware":
				return UNIX;
			case "osf1":
			case "openvms":
				return JVM;
			default:
				return UNKNOWN;
		}
	}
}
