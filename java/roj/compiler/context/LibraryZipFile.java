package roj.compiler.context;

import roj.archive.zip.ZEntry;
import roj.archive.zip.ZipFile;
import roj.asm.Parser;
import roj.asm.tree.ConstantData;
import roj.asm.tree.IClass;
import roj.asm.tree.attr.AttrModule;
import roj.asm.tree.attr.Attribute;
import roj.collect.MyHashMap;
import roj.io.IOUtil;
import roj.util.Helpers;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
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
		//zf.entries().clear();

		IClass module = get("module-info");
		if (module != null) {
			AttrModule module1 = module.parsedAttr(module.cp(), Attribute.Module);
			moduleName = module1.self.name;
		}
	}

	@Override
	public String getModule(String className) {return moduleName;}

	@Override
	public Set<String> content() { return info.keySet(); }

	@Override
	@SuppressWarnings("unchecked")
	public ConstantData get(CharSequence name) {
		var entry = (MyHashMap.AbstractEntry<String, Object>) ((MyHashMap<?,?>)info).getEntry(Helpers.cast(name));
		if (entry == null) return null;
		if (entry.getValue() instanceof ConstantData c) return c;
		synchronized (info) {
			ConstantData v = null;
			try {
				v = Parser.parseConstants(IOUtil.read(zf.getStream((ZEntry) entry.getValue())));
				entry.setValue(v);
			} catch (IOException e) {
				e.printStackTrace();
			}
			return v;
		}
	}

	@Override
	public InputStream getResource(CharSequence name) throws IOException {return zf.getStream(name.toString());}

	@Override
	public void close() throws Exception { zf.close(); }
}