package roj.compiler.context;

import roj.archive.zip.ZEntry;
import roj.archive.zip.ZipFile;
import roj.asm.Parser;
import roj.asm.tree.IClass;
import roj.collect.MyHashMap;
import roj.util.ByteList;
import roj.util.Helpers;

import java.io.File;
import java.io.IOException;
import java.util.Set;

/**
 * @author Roj234
 * @since 2022/9/16 0016 21:52
 */
public class LibraryZipFile implements Library {
	public final ZipFile zf;
	private final MyHashMap<String, Object> info;
	private String moduleName;

	public LibraryZipFile(File file) throws IOException {
		this.zf = new ZipFile(file, ZipFile.FLAG_BACKWARD_READ);
		this.info = new MyHashMap<>();

		for (ZEntry entry : zf.entries()) {
			String name = entry.getName();
			if (name.endsWith(".class")) info.put(name.substring(0, name.length()-6), entry);
		}
		zf.entries().clear();
	}
	public LibraryZipFile(File file, String moduleName) throws IOException {
		this(file);
		this.moduleName = moduleName;
	}

	@Override
	public String getModule(String className) {return moduleName;}

	@Override
	public Set<String> content() { return info.keySet(); }

	@Override
	public IClass get(CharSequence name) {
		MyHashMap.AbstractEntry<String, Object> entry = info.getEntry(Helpers.cast(name));
		if (entry == null) return null;
		if (entry.getValue() instanceof IClass c) return c;
		synchronized (info) {
			IClass v = null;
			try {
				ByteList data = ByteList.wrap(zf.get((ZEntry) entry.getValue()));
				v = Parser.parseConstants(data);
				entry.setValue(v);
			} catch (IOException e) {
				e.printStackTrace();
			}
			return v;
		}
	}

	@Override
	public void close() throws Exception { zf.close(); }
}