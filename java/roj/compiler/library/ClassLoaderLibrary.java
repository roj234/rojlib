package roj.compiler.library;

import roj.asm.ClassNode;
import roj.io.IOUtil;

import java.io.IOException;

/**
 * @author Roj234
 * @since 2024/7/25 6:24
 */
public class ClassLoaderLibrary implements Library {
	private final ClassLoader cl;
	public ClassLoaderLibrary(ClassLoader loader) {this.cl = loader;}

	@Override
	public ClassNode get(CharSequence name) {
		try (var in = cl == null ? ClassLoader.getSystemResourceAsStream(name+".class") : cl.getResourceAsStream(name+".class")) {
			if (in != null) return ClassNode.parseSkeleton(IOUtil.read(in));
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}
}