package roj.misc;

import roj.collect.SimpleList;
import roj.collect.ToIntMap;
import roj.io.FileUtil;
import roj.io.IOUtil;
import roj.text.LineReader;
import roj.text.UTFCoder;

import java.io.File;
import java.io.FileInputStream;
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

		UTFCoder x = IOUtil.SharedCoder.get();
		x.keep = false;

		System.out.println("正在计算...");
		ToIntMap<String> map = new ToIntMap<>();
		for (File file : FileUtil.findAllFiles(f, file -> file.getName().endsWith(".java"))) {
			x.byteBuf.clear();
			x.byteBuf.readStreamFully(new FileInputStream(file));
			// 是否计算空行
			int size = new LineReader(x.decodeR(), true).size();
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
