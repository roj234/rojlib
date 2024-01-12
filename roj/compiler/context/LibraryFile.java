package roj.compiler.context;

import org.jetbrains.annotations.NotNull;
import roj.asm.Parser;
import roj.asm.tree.ConstantData;
import roj.asm.tree.IClass;
import roj.io.IOUtil;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Set;

/**
 * @author Roj234
 * @since 2022/9/16 0016 21:52
 */
public class LibraryFile implements Library {
	public final ConstantData data;

	public LibraryFile(File f) throws IOException { data = Parser.parseConstants(IOUtil.read(f)); }

	@Override
	@NotNull
	public Set<String> content() { return Collections.singleton(data.name); }
	@Override
	public IClass get(CharSequence name) { return name.equals(data.name) ? data : null; }
}