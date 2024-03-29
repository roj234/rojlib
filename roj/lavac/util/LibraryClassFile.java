package roj.lavac.util;

import roj.asm.Parser;
import roj.asm.tree.ConstantData;
import roj.asm.tree.IClass;
import roj.io.IOUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.Set;

/**
 * @author Roj234
 * @since 2022/9/16 0016 21:52
 */
public class LibraryClassFile implements Library {
	public final ConstantData data;

	public LibraryClassFile(File f) throws IOException {
		this.data = Parser.parseConstants(IOUtil.getSharedByteBuf().readStreamFully(new FileInputStream(f)));
	}

	@Override
	public Set<String> content() {
		return Collections.singleton(data.name);
	}

	@Override
	public boolean has(CharSequence name) {
		return name.equals(data.name);
	}

	@Override
	public IClass get(CharSequence name) {
		return name.equals(data.name) ? data : null;
	}

	@Override
	public void close() {}
}
