package roj.text.logging;

import roj.asm.type.Type;
import roj.asm.type.TypeHelper;
import roj.collect.HashMap;
import roj.compiler.plugins.asm.ASM;
import roj.concurrent.TimerTask;
import roj.reflect.Reflection;
import roj.text.CharList;
import roj.text.LineReader;
import roj.util.DynByteBuf;
import roj.util.Helpers;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * @author Roj233
 * @since 2022/6/1 5:09
 */
sealed class LogWriter extends PrintWriter permits LogWriterJson {
	static final ThreadLocal<LogWriter> LOCAL = ThreadLocal.withInitial(LogWriter::new);

	public void println(Object x) {
		try {
			String s = String.valueOf(x);
			if (s.startsWith("\tat ")) simplifyPackage(s, sb);
			else sb.append(s).append('\n');
			myOut.append(sb);
			sb.setLength(prefix);
		} catch (Exception e) {
			Helpers.athrow(e);
		}
	}

	void printError(Throwable e, Appendable myOut, String prefix) {
		sb.clear();
		sb.append(prefix);
		this.prefix = sb.length();
		this.myOut = myOut;
		e.printStackTrace(this);
	}

	private static final int PACKAGE_NAME_LENGTH = 2;
	private static void simplifyPackage(String name, CharList sb) {
		sb.append("\tat ");
		int end = name.lastIndexOf('.', name.indexOf('('));
		int i = 4;

		if (Reflection.JAVA_VERSION > 8) {
			// java.base/
			int module = name.indexOf('/');
			if (module >= 0) {
				sb.append(name, i, module+1);
				i = module+1;
			}
		}

		while (true) {
			int j = name.indexOf('.', i);
			if (j < 0 || j >= end) break;
			sb.append(name, i, i+PACKAGE_NAME_LENGTH).append('.');
			i = j+1;
		}
		sb.append(name, i, name.length()).append('\n');
	}

	static final class MyMap extends HashMap<String, Object> {
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

	final CharList lineTemp = new CharList(256);
	final CharList sb = new CharList(256);

	public LogWriter() {
		super(ASM.TARGET_JAVA_VERSION >= 11 ? nullWriter() : new Writer() {
			public void write(char[] cbuf, int off, int len){}
			public void flush(){}
			public void close(){}
		}, true);
		tmpCtx.put("THREAD", Thread.currentThread().getName());
	}

	private TimerTask task;
	private LogContext prevCtx;
	private Level prevLevel;
	private CharSequence prevTxt;
	private Throwable prevExc;
	private Object[] prevArg;
	private volatile int prevCount;
	private long prevTime;

	private static boolean equals(Object[] a, Object[] a2, int len) {
		if (a==a2) return true;
		if (a2==null) return false;
		if (a2.length != len) return false;

		for (int i=0; i<len; i++) {
			if (!Objects.equals(a[i], a2[i]))
				return false;
		}

		return true;
	}
	private void delayedShowTask() {
		if (prevCount > 0) {
			synchronized (this) {
				if (prevCount > 0) {
					String txt = prevTxt+" (x"+prevCount+" in total)";
					prevCount = 0;
					prevTxt = null;
					Object[] arg = prevArg;
					LOCAL.get().log(prevCtx, prevLevel, txt, prevExc, arg, arg == null ? 0 : arg.length);
				}
			}
		}
	}

	void log(LogContext ctx, Level level, CharSequence msg, Throwable ex, Object[] args, int argc) {
		/*if (ex == prevExc && msg.equals(prevTxt) && equals(args, prevArg, argc)) {
			if (++prevCount % 100 == 0) msg += " (x100)";
			else if (System.currentTimeMillis() - prevTime < 100) {
				if (prevCount == 1) {
					task = Scheduler.getDefaultScheduler().delay(this::delayedShowTask, 100);
				}
				return;
			}
		} else {
			if (task != null) task.cancel();
			delayedShowTask();

			prevCtx = ctx;
			prevLevel = level;
			prevTxt = msg;
			prevExc = ex;
			prevArg = args == holder ? Arrays.copyOf(args, argc) : args;
			prevTime = System.currentTimeMillis();
		}*/

		MyMap m = tmpCtx;
		m.put("LEVEL", level);
		m.put("NAME", ctx.name());
		m.components = ctx.getComponents();

		CharList sb = this.sb; sb.clear();
		ctx.getPrefix().format(m, sb);

		int pref = sb.length();

		CharSequence msg1;
		if (argc > 0) {
			lineTemp.clear();
			replaceArg(lineTemp, msg.toString(), args, argc);
			msg1 = lineTemp;
		} else {
			msg1 = msg;
		}

		LogDestination dst = ctx.destination();
		try {
			Appendable out = dst.getAndLock();
			if (msg1 != null) {
				for (String line : LineReader.create(msg1, false)) {
					out.append(sb.append(line).append('\n'));
					sb.setLength(pref);
				}
			}

			if (ex != null) {
				prefix = pref;
				myOut = out;
				try {
					ex.printStackTrace(this);
				} finally {
					myOut = null;
				}
			}

			dst.unlockAndFlush();
		} catch (Exception e) {
			try { dst.unlockAndFlush(); } catch (Exception ignored) {}

			try {
				PrintStream prevErr = System.err;
				prevErr.println("LogDestination ["+dst.getClass().getName()+"] has thrown an exception during logging");
				e.printStackTrace(prevErr);
			} catch (Exception ignored) {}
		}
	}

	static void replaceArg(CharList sb, String msg, Object[] args, int argc) {
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
		if (arg == null) {
			sb.append("null");
			return;
		}

		if (arg.getClass().getComponentType() != null) {
			switch (Type.fieldDesc(TypeHelper.class2asm(arg.getClass())).getActualClass()) {
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

		if (arg instanceof DynByteBuf buf) {
			sb.append(buf.dump());
			return;
		}

		sb.append(arg);
	}
}