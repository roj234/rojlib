package roj.lavac.util;

import roj.asm.Parser;
import roj.asm.tree.IClass;
import roj.collect.MyHashMap;
import roj.io.IOUtil;
import roj.text.CharList;
import roj.util.Helpers;

import java.io.InputStream;
import java.util.Collections;
import java.util.Set;

import static roj.collect.IntMap.UNDEFINED;

/**
 * @author Roj234
 * @since 2022/9/17 0017 18:15
 */
public class LibraryRuntime implements Library {
	public static final Library INSTANCE = new LibraryRuntime();

	private final MyHashMap<String, IClass> info = new MyHashMap<>();

	@Override
	public Set<String> content() {
		return Collections.emptySet();
	}

	@Override
	public boolean has(CharSequence name) {
		return null != Object.class.getClassLoader().getResource(name.toString().concat(".class"));
	}

	@Override
	public IClass get(CharSequence name) {
		MyHashMap.Entry<String, IClass> entry = info.getEntry(Helpers.cast(name));
		if (entry != null && entry.v != UNDEFINED) return entry.v;
		synchronized (info) {
			IClass v = apply(name);
			info.put(name.toString(), v);
			return v;
		}
	}

	@Override
	public void close() throws Exception {}

	public IClass apply(CharSequence s) {
		try {
			String cn = new CharList().append(s).append(".class").toString();
			InputStream in = LibraryRuntime.class.getClassLoader().getResourceAsStream(cn);
			if (in == null) return null;
			return Parser.parseConstants(IOUtil.getSharedByteBuf().readStreamFully(in).toByteArray());
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}
}
