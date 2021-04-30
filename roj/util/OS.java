package roj.util;

/**
 * This file is a part of MI <br>
 * (L) Copyleft 2020-20XX 版权没有, 仿冒不究,如有雷同,纯属活该
 * <p>
 * @author Roj234
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
        if (System.getProperty("os.name").toLowerCase().startsWith("windows"))
            return WINDOWS;
        if (System.getProperty("os.name").toLowerCase().startsWith("mac"))
            return MAC_OS;
        switch (System.getProperty("os.name")) {
            case "Solaris":
            case "SunOS":
            case "MPC/iX":
            case "HP-UX":
            case "OS/390":
            case "FreeBSD":
            case "Irix":
            case "Digital":
            case "AIX":
            case "NetWare":
                return UNIX;
            case "OSF1":
            case "OpenVMS":
                return JVM;
            default:
                return UNKNOWN;
        }
    }
}
