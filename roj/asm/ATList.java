package roj.asm;

import roj.collect.MyHashMap;
import roj.io.IOUtil;
import roj.text.LineReader;
import roj.text.TextUtil;
import roj.util.Helpers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * @author Roj234
 * @since 2021/5/29 16:43
 */
public class ATList {
	private static final Map<String, Collection<String>> map = new MyHashMap<>();

	public static void parse(Class<?> provider, String name) {
		try {
			parse(IOUtil.readUTF(provider, name));
		} catch (IOException e) {
			throw new RuntimeException("Failed to parse AT file: " + name + " not found");
		}
	}

	private static void parse(String cfg) {
		LineReader options = new LineReader(cfg);
		List<String> lst = new ArrayList<>(4);
		for (String conf : options) {
			if (conf.length() == 0) continue;
			if (conf.startsWith("public-f ")) conf = conf.substring(9);
			lst.clear();
			TextUtil.split(lst, conf, ' ');
			if (lst.size() < 2) {
				System.err.println("Unknown entry " + conf);
				continue;
			}
			ATList.add(lst.get(0).replace('/', '.'), lst.get(1));
		}
	}

	public static void add(String className, String fieldName) {
		map.computeIfAbsent(className, Helpers.fnMyHashSet()).add(fieldName);
	}

	public static Map<String, Collection<String>> getMapping() {
		return map;
	}
}