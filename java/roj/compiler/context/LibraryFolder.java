package roj.compiler.context;

import roj.asm.ClassNode;
import roj.io.IOUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * @author Roj234
 * @since 2022/9/16 21:52
 */
public class LibraryFolder implements Library {
	private final File path;
	public LibraryFolder(File file) {this.path = file;}

	@Override public ClassNode get(CharSequence name) {
		try {
			var file = new File(path, name.toString().concat(".class"));
			if (file.isFile()) return ClassNode.parseSkeleton(IOUtil.read(file));
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	@Override
	public InputStream getResource(CharSequence name) throws IOException {return new FileInputStream(new File(path, name.toString()));}
}