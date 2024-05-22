package roj.compiler.context;

import roj.asm.Parser;
import roj.asm.tree.ConstantData;
import roj.collect.MyHashMap;
import roj.io.IOUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * @author Roj234
 * @since 2022/9/16 0016 21:52
 */
public class LibraryFolder implements Library {
	public LibraryFolder(File file) {this.path = file;}

	private final File path;
	private final MyHashMap<String, ConstantData> info = new MyHashMap<>();

	@Override
	public ConstantData get(CharSequence name) {
		var o = info.get(name);
		if (o != null) return o;
		try {
			String cn = name.toString();
			ConstantData data = Parser.parseConstants(IOUtil.read(new File(path, cn.concat(".class"))));
			info.put(cn, data);
			return data;
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
	}

	@Override
	public InputStream getResource(CharSequence name) throws IOException {return new FileInputStream(new File(path, name.toString()));}
}