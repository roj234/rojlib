package roj.compiler.context;

import roj.asm.Parser;
import roj.asm.tree.IClass;
import roj.collect.MyHashMap;
import roj.io.IOUtil;
import roj.text.CharList;

import java.io.InputStream;

/**
 * @author Roj234
 * @since 2022/9/17 0017 18:15
 */
public class LibraryRuntime implements Library {
	public static final Library INSTANCE = new LibraryRuntime();

	private LibraryRuntime() {}

	private final MyHashMap<CharSequence, IClass> info = new MyHashMap<>();

	@Override
	public IClass get(CharSequence name) {
		MyHashMap.AbstractEntry<CharSequence, IClass> entry = info.getEntry(name);
		if (entry != null) return entry.getValue();
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
			String cn = new CharList().append(s).append(".class").toStringAndFree();
			InputStream in = LibraryRuntime.class.getClassLoader().getResourceAsStream(cn);
			if (in == null) return null;
			return Parser.parseConstants(IOUtil.getSharedByteBuf().readStreamFully(in).toByteArray());
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}
}