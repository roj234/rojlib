package roj.io;

import org.jetbrains.annotations.NotNull;
import roj.util.ArrayUtil;

import java.io.OutputStream;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public final class NullOutputStream extends OutputStream {
	public static final OutputStream INSTANCE = new NullOutputStream();

	public long writtenBytes;

	@Override public void write(int b) {writtenBytes++;}
	@Override public void write(@NotNull byte[] b, int off, int len) {
		ArrayUtil.checkRange(b, off, len);
		writtenBytes += len;
	}
}