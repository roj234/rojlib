package roj.compiler.context;

import roj.asm.tree.ConstantData;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Set;

/**
 * @author Roj234
 * @since 2022/9/16 0016 21:51
 */
public interface Library {
	default int fileHashCode() {return 0;}
	default Set<String> content() {return Collections.emptySet();}

	ConstantData get(CharSequence name);
	default String getModule(String className) { return null; }

	default InputStream getResource(CharSequence name) throws IOException {return null;}
	default void close() throws Exception {}
}