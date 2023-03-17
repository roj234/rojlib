package roj.io;

import javax.annotation.Nonnull;
import java.io.OutputStream;

/**
 * Dummy output stream
 *
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public final class DummyOutputStream extends OutputStream {
	public static final DummyOutputStream INSTANCE = new DummyOutputStream();

	public int wrote;

	public DummyOutputStream() {}

	@Override
	public void write(int i) {
		wrote++;
	}

	public void write(@Nonnull byte[] arr, int off, int len) {
		wrote += len;
	}
}
