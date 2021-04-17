package roj.lavac.util;

import roj.archive.zip.ZEntry;
import roj.archive.zip.ZipArchive;
import roj.asm.Parser;
import roj.asm.tree.IClass;
import roj.collect.MyHashMap;
import roj.collect.MyHashSet;
import roj.io.IOUtil;
import roj.util.ByteList;
import roj.util.Helpers;

import java.io.File;
import java.io.IOException;
import java.util.Set;

import static roj.collect.IntMap.UNDEFINED;

/**
 * @author Roj234
 * @since 2022/9/16 0016 21:52
 */
public class LibraryZipFile implements Library {
	public final ZipArchive zf;
	private final MyHashMap<String, IClass> info;

	public LibraryZipFile(File file) throws IOException {
		this.zf = new ZipArchive(file, ZipArchive.FLAG_BACKWARD_READ);

		MyHashSet<ZEntry> set = new MyHashSet<>(zf.getEntries().size());
		for (ZEntry entry : zf.getEntries().values()) {
			if (entry.getName().endsWith(".class")) set.add(entry);
		}
		zf.getEntries().clear();
		for (ZEntry entry : set) {
			zf.getEntries().put(entry.getName().substring(0, entry.getName().length() - 6), entry);
		}

		info = new MyHashMap<>();
	}

	@Override
	public Set<String> content() {
		return zf.getEntries().keySet();
	}

	@Override
	public boolean has(CharSequence name) {
		return zf.getEntries().containsKey(name);
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
	public void close() throws Exception {
		zf.close();
	}

	public IClass apply(CharSequence s) {
		ZEntry file = zf.getEntries().get(s);
		if (file == null) return null;
		try {
			ByteList data = zf.get(file, IOUtil.getSharedByteBuf());
			return Parser.parseConstants(data);
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}
}
