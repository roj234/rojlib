package roj.mod;

import LZMA.LzmaInputStream;
import roj.util.ByteList;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author solo6975
 * @since 2020/8/29 8:54
 */
final class Helper0 {
    static InputStream forgeInit(File forgeFile) throws IOException {
        JarFile jarFile = new JarFile(forgeFile);
        Enumeration<JarEntry> enumeration = jarFile.entries();
        JarEntry deobfData = null;
        while (enumeration.hasMoreElements()) {
            JarEntry entry = enumeration.nextElement();
            if (!entry.isDirectory() && entry.getName().startsWith("deobfuscation_data-") && entry.getName().endsWith(".lzma")) {
                deobfData = entry;
                break;
            }
        }
        try (InputStream is = new BufferedInputStream(new LzmaInputStream(jarFile.getInputStream(deobfData)))) {
            return new ByteList().readStreamFully(is).asInputStream();
        }
    }

}
