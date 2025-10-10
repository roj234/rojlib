package roj.io;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.OutputStream;

/**
 * @author Roj234
 * @since 2025/10/15 14:48
 */
public abstract class MBOutputStream extends OutputStream {
	@Override public final void write(int b) throws IOException {IOUtil.writeSingleByteHelper(this, b);}
	@Override public abstract void write(@NotNull byte[] b, int off, int len) throws IOException;
}