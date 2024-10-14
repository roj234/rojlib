package roj.compiler.context;

import roj.asm.Parser;
import roj.asm.tree.ConstantData;
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
	private final File path;
	public LibraryFolder(File file) {this.path = file;}

	@Override public ConstantData get(CharSequence name) {
		try {
			var file = new File(path, name.toString().concat(".class"));
			if (file.isFile()) return Parser.parseConstants(IOUtil.read(file));
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	@Override
	public InputStream getResource(CharSequence name) throws IOException {return new FileInputStream(new File(path, name.toString()));}
}