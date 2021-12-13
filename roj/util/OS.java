/*
 * This file is a part of MI
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2021 Roj234
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package roj.util;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
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
        String property = System.getProperty("os.name").toLowerCase();
        if (property.startsWith("windows"))
            return WINDOWS;
        if (property.startsWith("mac"))
            return MAC_OS;
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
