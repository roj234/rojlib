package roj.mod.fp;

import net.md_5.specialsource.*;
import net.md_5.specialsource.provider.JarProvider;
import roj.archive.zip.ZipFileWriter;
import roj.collect.TrieTreeSet;
import roj.io.IOUtil;
import roj.ui.CLIUtil;
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
 * @since 2021/2/25 6:02
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
				if (ze.getName().endsWith(".class") && !set.strStartsWithThis(ze.getName())) {
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
		if (ze == null) throw new RuntimeException("MCP Config 文件有误");

		double start = System.currentTimeMillis();

		CLIUtil.info("开始启动SpecialSource");
		CLIUtil.warning("由于未知原因, 一定会有补丁出错, 会影响你的开发，如果你觉得不爽请打forge");

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

		CLIUtil.success("SpecialSource映射成功! 用时: " + (last = ((last - start) / 1000d)) + "s");
		CLIUtil.info("文件数: " + i + " 平均速度: " + (i / last) + " 文件/s");
	}
}
