package roj.misc;

import roj.collect.SimpleList;
import roj.collect.ToIntMap;
import roj.io.IOUtil;
import roj.text.CharList;
import roj.text.TextReader;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * @author Roj234
 * @since 2022/10/7 0007 22:32
 */
public class WhatYouHaveDone {
	public static void main(String[] args) throws IOException {
		File f = new File("D:\\mc\\FMD-1.5.2\\projects\\implib\\java");
		int total = 0;

		CharList sb = IOUtil.getSharedCharBuf();

		System.out.println("正在计算...");
		ToIntMap<String> map = new ToIntMap<>();
		for (File file : IOUtil.findAllFiles(f, file -> file.getName().endsWith(".java"))) {
			int size = 0;
			try (TextReader in = TextReader.auto(file)) {
				while (in.readLine(sb)) {
					if (sb.length() > 0) size++;
					sb.clear();
				}
			} catch (IOException e) {
				System.out.println(file);
				e.printStackTrace();
			}

			total += size;
			map.putInt(file.getName(), size);
		}
		List<ToIntMap.Entry<String>> list = new SimpleList<>(map.selfEntrySet());
		list.sort((o1, o2) -> Integer.compare(o2.v, o1.v));
		System.out.println("你已经写了 " + total + " 行代码呢~");
		System.out.println("=========TOP 10============");
		for (int i = 0; i < Math.min(list.size(), 10); i++) {
			System.out.println((i+1) + ". " + list.get(i).k + ": " + list.get(i).v);
		}
	}
}
