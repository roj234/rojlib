package roj.text.logging;

import roj.text.CharList;
import roj.text.TextWriter;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;

/**
 * @author Roj233
 * @since 2022/6/1 6:27
 */
@FunctionalInterface
public interface LogAppender {
	void append(CharList sb) throws IOException;

	static LogAppender console() {
		// allow System.setOut() to work properly
		//noinspection Convert2MethodRef
		return (sb) -> System.out.print(sb);
	}
	static LogAppender appendTo(Appendable out) {return out::append;}
	static LogAppender to(OutputStream out) { return new Stream(out, null); }
	static LogAppender to(OutputStream out, Charset charset) { return new Stream(out, charset); }

	final class Stream implements LogAppender {
		private final Appendable out;

		public Stream(OutputStream os, Charset charset) {
			out = os instanceof Appendable p ? p : new TextWriter(os, charset);
		}

		@Override
		public void append(CharList sb) throws IOException {
			out.append(sb);
			if (out instanceof TextWriter tw)
				tw.flush();
		}
	}
}