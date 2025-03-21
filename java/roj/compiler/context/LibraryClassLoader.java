package roj.compiler.context;

import roj.asm.ClassNode;
import roj.asm.Parser;
import roj.io.IOUtil;

import java.io.IOException;

/**
 * @author Roj234
 * @since 2024/7/25 0025 6:24
 */
public class LibraryClassLoader implements Library {
	private final ClassLoader cl;
	public LibraryClassLoader(ClassLoader loader) {this.cl = loader;}

	@Override
	public ClassNode get(CharSequence name) {
		try (var in = cl == null ? ClassLoader.getSystemResourceAsStream(name+".class") : cl.getResourceAsStream(name+".class")) {
			if (in != null) return Parser.parseConstants(IOUtil.read(in));
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}
}