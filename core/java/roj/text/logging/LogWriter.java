package roj.text.logging;

import roj.asm.type.Type;
import roj.asm.type.TypeHelper;
import roj.collect.HashSet;
import roj.collect.Hasher;
import roj.config.JsonSerializer;
import roj.config.TextEmitter;
import roj.text.CharList;
import roj.text.Formatter;
import roj.text.TextUtil;
import roj.util.ArrayCache;
import roj.util.DynByteBuf;
import roj.util.Helpers;
import roj.util.JVM;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Roj233
 * @since 2022/6/1 5:09
 */
class LogWriter extends PrintWriter {
	static final ThreadLocal<LogWriter> LOCAL = ThreadLocal.withInitial(LogWriter::new);

	void printError(Throwable e, Appendable target, String prefix) {
		packet.clear();
		packet.append(prefix);
		prefixLen = packet.length();
		errOut = target;

		try {
			e.printStackTrace(this);
		} finally {
			errOut = null;
		}
	}

	public void println(Object x) {
		try {
			errOut.append(packet, 0, prefixLen);

			String s = String.valueOf(x);
			if (s.startsWith("\tat ")) simplifyPackage(s, errOut);
			else errOut.append(s).append('\n');

		} catch (Exception e) {
			Helpers.athrow(e);
		}
	}
	private static final int PACKAGE_NAME_LENGTH = 2;
	private static void simplifyPackage(String name, Appendable sb) throws IOException {
		sb.append("\tat ");
		int end = name.lastIndexOf('.', name.indexOf('('));
		int i = 4;

		if (JVM.VERSION > 8) {
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

	final HashMap<String, Object> variables = new HashMap<>();
	final Object[] sharedArguments = new Object[4];

	final CharList line = new CharList(256);
	int prefixLen;

	final CharList packet = new CharList(ArrayCache.getIOCharBuffer());
	Appendable errOut;

	public LogWriter() {
		super(JVM.VERSION >= 11 ? nullWriter() : new Writer() {
			public void write(char[] cbuf, int off, int len){}
			public void flush(){}
			public void close(){}
		}, true);
		variables.put("thread", Thread.currentThread().getName());
	}

	CharList serialize(LogContext ctx, Formatter format, Level level, CharSequence msg, Throwable ex, Object[] args, int argc) {
		var m = variables;
		m.put("level", level);
		m.put("logger", ctx.name());

		var sb = packet; sb.clear();
		format.format(m, sb);
		int pfxLen = sb.length();

		if (msg != null) {
			int i = 0;
			int argIndex = 0;

			while (true) {
				line.clear();
				i = TextUtil.gAppendToNextCRLF(msg, i, line, -1);

				argIndex = replaceArg(packet, line, args, argc, argIndex);
				packet.append('\n');

				if (i < 0) break;
				packet.append(packet, 0, pfxLen);
			}
		}

		if (ex != null) {
			errOut = sb;
			prefixLen = pfxLen;
			ex.printStackTrace(this);
		}

		return sb;
	}
	private static int replaceArg(CharList out, CharSequence in, Object[] args, int argc, int argIndex) {
		int prev = 0;
		for (int i = 0; i < in.length(); ) {
			char c = in.charAt(i++);
			if (c == '{' && i < in.length() && in.charAt(i++) == '}') {
				out.append(in, prev, i-2);
				prev = i;

				if (argIndex < argc) toString(out, args[argIndex++]);
				else out.append("{}");
			}
		}
		out.append(in, prev, in.length());
		return argIndex;
	}
	private static void toString(CharList sb, Object arg) {
		if (arg == null) {
			sb.append("null");
			return;
		}

		if (arg.getClass().getComponentType() != null) {
			switch (Type.getType(TypeHelper.class2asm(arg.getClass())).getActualClass()) {
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

	CharList serializeJson(LogContext ctx, Level level, CharSequence msg, Throwable ex, Object[] args, int argc) {
		packet.clear();
		var json = new JsonSerializer().to(packet);
		json.emitMap();

		json.emitKey("context");
		json.emitMap();

		CharList sb = this.line;
		var m = variables;
		m.put("level", level);
		m.put("logger", ctx.name());

		for (Map.Entry<String, Object> entry : m.entrySet()) {
			json.emitKey(entry.getKey());
			Object value = entry.getValue();

			if (value instanceof Formatter f) {
				sb.clear();
				json.valString(f.format(m, sb));
			} else {
				json.emit(String.valueOf(value));
			}
		}

		json.pop();

		json.emitKey("message");
		if (argc > 0) {
			sb.clear();
			replaceArg(sb, msg, args, argc, 0);
			json.valString(sb);
		}
		else json.valString(msg);

		if (ex != null) {
			json.emitKey("exception");
			writeException(ex, new HashSet<>(Hasher.identity()), json);
		}

		return json.getValue();
	}

	private static void writeException(Throwable ex, HashSet<Throwable> dejavu, TextEmitter ser) {
		if (!dejavu.add(ex)) {
			ser.emit("circular reference");
			return;
		}

		ser.emitMap();

		ser.emitKey("type");
		ser.emit(ex.getClass().getSimpleName());

		if (ex.getMessage() != null) {
			ser.emitKey("message");
			ser.emit(ex.getMessage());
		}

		var trace = LogHelper.INSTANCE.getStackTrace(ex);
		if (trace.length > 0) {
			ser.emitKey("trace");
			ser.emitList();

			for (var el : trace) {
				ser.emitMap();
				ser.emitKey("class");
				ser.emit(el.getClassName());
				ser.emitKey("method");
				ser.emit(el.getMethodName());
				if (el.getLineNumber() != -1) {
					ser.emitKey("line");
					ser.emit(el.getLineNumber());
				}
				if (el.getFileName() != null) {
					ser.emitKey("file");
					ser.emit(el.getFileName());
				}
				if (JVM.VERSION > 8) {
					if (el.getModuleName() != null) {
						ser.emitKey("module");
						ser.emit(el.getMethodName());
					}
				}
				ser.pop();
			}
		}

		List<Throwable> suppressed = LogHelper.INSTANCE.getSuppressed(ex);
		if (suppressed != null) {
			ser.emitKey("suppressed");
			ser.emitList();
			for (Throwable th : suppressed) {
				writeException(th, dejavu, ser);
			}
			ser.pop();
		}

		Throwable cause = ex.getCause();
		if (cause != null) {
			ser.emitKey("cause");
			writeException(cause, dejavu, ser);
		}

		ser.pop();
	}
}