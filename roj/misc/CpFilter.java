package roj.misc;

import roj.archive.zip.ZipArchive;
import roj.collect.MyHashSet;
import roj.io.IOUtil;
import roj.text.LineReader;
import roj.util.ByteList;
import roj.util.Helpers;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Vector;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author Roj233
 * @since 2021/7/8 12:35
 */
public class CpFilter {
	public static void registerShutdownHook() {
		Runtime.getRuntime().addShutdownHook(new Thread(CpFilter::genList));
	}

	public static void genList() {
		try {
			File dst = new File("classList.txt");
			// 取并集
			HashSet<String> dt = new HashSet<>();
			if (dst.isFile()) {
				dt.addAll(Files.readAllLines(dst.toPath()));
			}
			Field f = ClassLoader.class.getDeclaredField("classes");
			f.setAccessible(true);
			Vector<Class<?>> vector = Helpers.cast(f.get(CpFilter.class.getClassLoader()));
			for (int i = 0; i < vector.size(); i++) {
				Class<?> classes = vector.get(i);
				dt.add(classes.getName().replace('.', '/'));
			}
			ByteList b = IOUtil.getSharedByteBuf();
			try (FileOutputStream out = new FileOutputStream(dst)) {
				for (String name : dt) {
					b.putUTFData(name).put((byte) '\n');
				}
				b.writeToStream(out);
			}
		} catch (Throwable e) {
			e.printStackTrace();
		}
	}

	public static void main(String[] args) throws Exception {
		if (args.length < 1) {
			System.out.println("CpFilter <jar> [classList]");
			System.out.println("  用途：精简jar");
			return;
		}

		MyHashSet<String> dt = new MyHashSet<>();
		if (args.length > 1) {
			for (String ss : new LineReader(IOUtil.readUTF(new File(args[1])))) {
				dt.add(ss);
			}
		}

		ZipArchive zf = new ZipArchive(new File(args[0]));

		for (String entry : zf.getEntries().keySet()) {
			if (entry.endsWith("/") || (!dt.isEmpty() && !dt.contains(entry.substring(0, entry.length() - 6)))) {
				zf.put(entry, null);
			}
		}
		zf.store();
		System.out.println("OK");
	}
}
