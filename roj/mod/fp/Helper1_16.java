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
package roj.mod.fp;

import net.md_5.specialsource.*;
import net.md_5.specialsource.provider.JarProvider;
import roj.collect.TrieTreeSet;
import roj.io.IOUtil;
import roj.io.ZipFileWriter;
import roj.ui.CmdUtil;
import roj.util.ByteList;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Split-out processor
 *
 * @author Roj234
 * @since  2021/2/25 6:02
 */
class Helper1_16 {
    static void remap116_SC(File serverDest, File mcServer, File mcpConfigFile, TrieTreeSet set) throws IOException, NoSuchFieldException, IllegalAccessException {

        File tmpFile = new File(System.getProperty("java.io.tmpdir"), System.currentTimeMillis() + ".tmp");
        tmpFile.deleteOnExit();

        int i = 0;
        ByteList bl = IOUtil.getSharedByteBuf();

        try (ZipFileWriter zos = new ZipFileWriter(tmpFile, false);
             ZipFile zf = new ZipFile(mcServer)) {
            Enumeration<? extends ZipEntry> es = zf.entries();
            while (es.hasMoreElements()) {
                ZipEntry ze = es.nextElement();
                if (ze.getName().endsWith(".class") && !set.startsWith(ze.getName())) {
                    ze.setExtra(null);
                    zos.beginEntry(ze);
                    bl.readStreamFully(zf.getInputStream(ze)).writeToStream(zos);
                    bl.clear();
                    zos.closeEntry();
                    i++;
                }
            }
        }

        ZipFile zipFile = new ZipFile(mcpConfigFile);
        ZipEntry ze = zipFile.getEntry("config/joined.tsrg");
        if (ze == null)
            throw new RuntimeException("MCP Config ????????????");

        double start = System.currentTimeMillis();

        CmdUtil.info("????????????SpecialSource");
        CmdUtil.warning("??????????????????, ????????????????????????, ???????????????????????????????????????????????????forge");

        JarMapping jarMapping = new JarMapping();
        BufferedReader reader = new BufferedReader(new InputStreamReader(zipFile.getInputStream(ze)));
        jarMapping.loadMappings(reader, null, null, false);

        zipFile.close();

        ProgressMeter.printInterval = 20;
        Field verbose = SpecialSource.class.getDeclaredField("verbose");
        verbose.setAccessible(true);
        verbose.setBoolean(null, true);

        Jar jar = Jar.init(Collections.singletonList(tmpFile));
        jarMapping.setFallbackInheritanceProvider(new JarProvider(jar));

        JarRemapper jarRemapper = new JarRemapper(null, jarMapping, null);
        jarRemapper.remapJar(jar, serverDest);

        jar.close();
        tmpFile.delete();

        double last = System.currentTimeMillis();

        CmdUtil.success("SpecialSource????????????! ??????: " + (last = ((last - start) / 1000d)) + "s");
        CmdUtil.info("?????????: " + i + " ????????????: " + (i / last) + " ??????/s");
    }
}
