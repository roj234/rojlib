package roj.test;

import roj.archive.zip.ZEntry;
import roj.archive.zip.ZipFile;
import roj.collect.PerfectHashMap;
import roj.io.IOUtil;
import roj.test.internal.Test;
import roj.util.ByteList;

import java.io.File;

/**
 * @author Roj234
 * @since 2024/5/23 0023 0:53
 */
@Test("测试完美哈希表的构建速度")
public class PerfectHashMapTest {
	public static void main(String[] args) throws Exception {
		File aClass = IOUtil.getJar(PerfectHashMap.class);

		PerfectHashMap.Builder<ZEntry> builder = new PerfectHashMap.Builder<>();
		ZipFile zf = new ZipFile(aClass);
		for (ZEntry entry : zf.entries()) {
			builder.put(entry.getName(), entry);
		}
		System.out.println("Create");
		PerfectHashMap<ZEntry> map = builder.build(100);
		System.out.println("Ok");
		for (ZEntry entry : zf.entries()) {
			PerfectHashMap.Entry<ZEntry> entry1 = map.getEntry(entry.getName());
			if (entry1.value != entry) System.out.println("error "+entry.getName());
		}

		ByteList buf = IOUtil.getSharedByteBuf();
		map.encode(buf);
		System.out.println(buf.dump());
	}

}