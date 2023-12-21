package roj.text.logging;

import roj.asm.type.TypeHelper;
import roj.collect.MyHashMap;
import roj.text.CharList;
import roj.text.CharWriter;
import roj.text.logging.d.LogDestination;
import roj.util.DynByteBuf;

import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.List;

/**
 * @author Roj233
 * @since 2022/6/1 5:09
 */
final class LogHelper extends PrintWriter {
	static final ThreadLocal<LogHelper> LOCAL = ThreadLocal.withInitial(LogHelper::new);

	private static final class ExcWriter extends CharWriter {
		private LogHelper o;
		public final void flush() throws IOException { o.myOut.append(sb); sb.setLength(o.prefix); }
		final void init(LogHelper h) { o = h; sb = h.sb; }
	}

	static final class MyMap extends MyHashMap<String, Object> {
		List<?> components;

		@Override
		public Object getOrDefault(Object key, Object def) {
			String s = key.toString();
			if (s.charAt(0) >= '0' && s.charAt(0) <= '9') return components.get(Integer.parseInt(s));
			else return super.getOrDefault(s, def);
		}
	}

	final MyMap tmpCtx = new MyMap();
	final Object[] holder = new Object[4];

	int prefix;
	Appendable myOut;

	final CharList sb = new CharList(256);

	public LogHelper() {
		super(new ExcWriter(), true);
		((ExcWriter) out).init(this);
	}

	final void doLog(LogContext ctx, Level level, CharSequence msg, Throwable ex, Object[] args, int argc) {
		MyMap m = tmpCtx;
		m.put("THREAD", Thread.currentThread().getName());
		m.put("LEVEL", level.name());
		m.put("NAME", ctx.name());
		m.components = ctx.getComponents();

		CharList sb = this.sb; sb.clear();
		ctx.getPrefix().replace(m, sb);

		int pref = sb.length();

		LogDestination dst = ctx.destination();
		try {
			Appendable ao = dst.getAndLock();
			if (msg != null) {
				if (argc > 0) replaceArg(sb, msg.toString(), args, argc);
				else sb.append(msg);
				sb.append(System.lineSeparator());
				ao.append(sb);
				sb.setLength(pref);
			}

			if (ex != null) {
				prefix = pref;
				myOut = ao;
				ex.printStackTrace(this);
			}

			dst.unlockAndFlush();
		} catch (Exception e) {
			try { dst.unlockAndFlush(); } catch (Exception ignored) {}

			try {
				PrintStream prevErr = System.err;
				prevErr.println("LogDestination [" + dst.getClass().getName() + "] has thrown an exception during logging");
				e.printStackTrace(prevErr);
			} catch (Exception ignored) {}
		} finally {
			myOut = null;
		}
	}

	private static void replaceArg(CharList sb, String msg, Object[] args, int argc) {
		int j = 0;
		int prev = 0;
		for (int i = 0; i < msg.length(); ) {
			char c = msg.charAt(i++);
			if (c == '{' && i < msg.length() && msg.charAt(i++) == '}') {
				sb.append(msg, prev, i-2);
				prev = i;

				if (j < argc) toString(sb, args[j++]);
				else sb.append("{}");
			}
		}
		sb.append(msg, prev, msg.length());
	}

	private static void toString(CharList sb, Object arg) {
		if (arg.getClass().getComponentType() != null) {
			switch (TypeHelper.parseField(TypeHelper.class2asm(arg.getClass())).getActualClass()) {
				case "[I": sb.append(Arrays.toString((int[]) arg)); return;
				case "[J": sb.append(Arrays.toString((long[]) arg)); return;
				case "[F": sb.append(Arrays.toString((float[]) arg)); return;
				case "[D": sb.append(Arrays.toString((double[]) arg)); return;
				case "[B": sb.append(Arrays.toString((byte[]) arg)); return;
				case "[Z": sb.append(Arrays.toString((boolean[]) arg)); return;
				case "[C": sb.append(Arrays.toString((char[]) arg)); return;
				case "[S": sb.append(Arrays.toString((short[]) arg)); return;
				default: sb.append(Arrays.deepToString((Object[]) arg)); return;
			}
		}

		if (arg instanceof DynByteBuf) {
			sb.append(((DynByteBuf) arg).dump());
			return;
		}

		sb.append(arg);
	}
}
