package roj.text.logging;

import roj.collect.MyHashMap;
import roj.text.CharList;
import roj.text.CharWriter;
import roj.text.UTFCoder;
import roj.text.logging.d.LogDestination;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.*;

/**
 * @author Roj233
 * @since 2022/6/1 5:09
 */
final class LogHelper extends PrintWriter {
	static final ThreadLocal<LogHelper> LOCAL = ThreadLocal.withInitial(LogHelper::new);

	private static class ExcWriter extends CharWriter {
		LogHelper o;

		@Override
		public void write(@Nonnull String str) throws IOException {
			super.write(str);
			if (str.equals(System.lineSeparator())) {
				LogHelper o1 = o;
				if (o1.os != null) {
					if (o1.encoder == null) {
						o1.uc.encodeTo(sb, o1.os);
					} else {
						o1.encodeCE(sb);
					}
				}
				o1.dest.newLine(sb);
				sb.setLength(o1.base);
			}
		}

		public void init(LogHelper helper) {
			o = helper;
			sb = helper.uc.charBuf;
		}
	}

	final MyHashMap<String, Object> localMap = new MyHashMap<>();
	final UTFCoder uc = new UTFCoder();
	final Object[] holder = new Object[10];

	private LogDestination dest;
	private OutputStream os;

	private final CharsetEncoder encoder;
	private CharBuffer ic;
	private final ByteBuffer ob;

	private int base;

	public LogHelper() {
		super(new ExcWriter());
		((ExcWriter) out).init(this);
		uc.byteBuf.ensureCapacity(1024);
		ob = uc.byteBuf.nioBuffer();
		Charset cs = Charset.defaultCharset();
		if (cs == StandardCharsets.UTF_8) encoder = null;
		else encoder = cs.newEncoder().onUnmappableCharacter(CodingErrorAction.REPLACE).onMalformedInput(CodingErrorAction.REPORT);
	}

	void doLog(LogContext ctx, Level level, CharSequence msg, Throwable ex, Object[] data, int length) {
		CharList tmp = uc.charBuf;
		tmp.clear();
		ctx.fillIn(tmp, level);

		int len = tmp.length();
		try {
			dest = ctx.destination();
			os = dest.getAndLock();
			if (msg != null) {
				if (length > 0) placePlaceHolder(tmp, msg, data, length, ctx.placeholderMissing());
				else tmp.append(msg);
				tmp.append(System.lineSeparator());
				if (os != null) {
					if (encoder == null) {
						uc.encodeTo(tmp, os);
					} else {
						encodeCE(tmp);
					}
				}
				dest.newLine(tmp);
				tmp.setLength(len);
			}

			if (ex != null) {
				this.base = len;
				ex.printStackTrace(this);
			}

			dest.unlock();
			dest = null;
		} catch (Exception e1) {
			dest.unlock();

			this.base = len;
			try {
				uc.encodeTo("LogDestination " + dest.toString() + " has thrown an exception during logging\n", Logger.StdErr);
				e1.printStackTrace(Logger.StdErr);
			} catch (IOException ignored) {
			} finally {
				try {
					os.close();
				} catch (IOException ignored) {}
			}
		} finally {
			os = null;
		}
	}

	void encodeCE(CharList tmp) throws IOException {
		CharBuffer ic = this.ic;
		if (ic == null || ic.array() != tmp.list) {
			ic = tmp.toCharBuffer();
		} else {
			ic.limit(tmp.length()).position(0);
		}

		ByteBuffer ob = this.ob;
		do {
			ob.clear();
			CoderResult cr = encoder.encode(ic, ob, true);
			if (cr.isError()) break;
			os.write(ob.array(), 0, ob.position());
		} while (ic.hasRemaining());
	}

	static void placePlaceHolder(CharList tmp, CharSequence msg, Object[] data, int length, CharSequence missing) {
		int j = 0;
		int prev = 0;
		for (int i = 0; i < msg.length(); ) {
			char c = msg.charAt(i++);
			if (c == '{' && i < msg.length() && msg.charAt(i++) == '}') {
				// put in bulk
				tmp.append(msg, prev, i - 2);
				prev = i;

				if (j < length) {
					tmp.append(data[j++]);
				} else {
					tmp.append(missing);
				}
			}
		}
		tmp.append(msg, prev, msg.length());
	}
}
