package roj.text;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.Writer;

/**
 * @author Roj233
 * @since 2022/6/1 5:51
 */
public class CharWriter extends Writer {
	public CharList sb;

	@Override
	public void flush() {}

	@Override
	public void close() {}

	public void write(int c) throws IOException {
		sb.append((char) c);
	}

	@Override
	public void write(@Nonnull char[] buf, int off, int len) throws IOException {
		sb.append(buf, off, len);
	}

	public void write(@Nonnull String str) throws IOException {
		sb.append(str);
	}

	public void write(@Nonnull String str, int off, int len) throws IOException {
		sb.append(str, off, len);
	}

	public Writer append(CharSequence csq) throws IOException {
		sb.append(csq, 0, csq.length());
		return this;
	}

	public Writer append(CharSequence csq, int start, int end) throws IOException {
		sb.append(csq, start, end);
		return this;
	}
}
