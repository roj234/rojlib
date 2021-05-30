package roj.mod;

import net.md_5.specialsource.*;
import net.md_5.specialsource.provider.JarProvider;
import roj.collect.TrieTreeSet;
import roj.io.ZipUtil;
import roj.ui.CmdUtil;
import roj.util.ByteList;

import java.io.*;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author solo6975
 * @since 2021/2/25 6:02
 */
class Helper1 {
    static void remap116_SC(File serverDest, File mcServer, File mcpConfigFile, TrieTreeSet set) throws IOException, NoSuchFieldException,
            IllegalAccessException {

        File tmpFile = new File(System.getProperty("java.io.tmpdir"), System.currentTimeMillis() + ".tmp");
        int i = 0;
        ByteList bl = new ByteList();
        ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(tmpFile));
        try(ZipFile zf = new ZipFile(mcServer)) {
            Enumeration<? extends ZipEntry> es = zf.entries();
            while (es.hasMoreElements()) {
                ZipEntry ze = es.nextElement();
                if (!ze.isDirectory() && ze.getName().endsWith(".class") && !set.startsWith(ze.getName())) {
                    ZipEntry ze1 = new ZipEntry(ze);
                    ze1.setCompressedSize(-1);
                    zos.putNextEntry(ze1);
                    bl.readStreamArrayFully(zf.getInputStream(ze)).writeToStream(zos);
                    bl.clear();
                    zos.closeEntry();
                    i++;
                }
            }
        } finally {
            ZipUtil.close(zos);
        }

        CmdUtil.info("开始启动SpecialSource");
        CmdUtil.warning("由于未知原因, 一定会有补丁出错, 会影响你的开发，如果你觉得不爽请打forge");

        ZipFile zipFile = new ZipFile(mcpConfigFile);
        ZipEntry ze = zipFile.getEntry("config/joined.tsrg");
        if (ze == null)
            throw new RuntimeException("MCP Config 文件有误");

        double start = System.currentTimeMillis();

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
        if(!tmpFile.delete()) {
            tmpFile.deleteOnExit();
        }

        double last = System.currentTimeMillis();

        CmdUtil.success("SpecialSource映射成功! 用时: " + (last = ((last - start) / 1000d)) + "s");
        CmdUtil.info("文件数: " + i + " 平均速度: " + (i / last) + " 文件/s");
    }
}
