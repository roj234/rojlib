package roj.text;

import roj.collect.MyHashMap;
import roj.collect.SimpleList;

import java.nio.charset.Charset;
import java.nio.charset.spi.CharsetProvider;
import java.util.Iterator;

/**
 * @author Roj234
 * @since 2023/4/18 0018 23:55
 */
final class MyCharsetProvider extends CharsetProvider {
	private static final MyHashMap<String, Charset> lookup = new MyHashMap<>();
	private static final SimpleList<Charset> registry = new SimpleList<>();

	static {
		register(UTF8MB4.INSTANCE);
	}

	private static void register(Charset cs) {
		registry.add(cs);
		lookup.put(cs.name(), cs);
		for (String alias : cs.aliases()) lookup.put(alias, cs);
	}

	@Override
	public Iterator<Charset> charsets() {
		return registry.iterator();
	}

	@Override
	public Charset charsetForName(String charsetName) {
		return lookup.get(charsetName);
	}
}
