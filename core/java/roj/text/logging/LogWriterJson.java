package roj.text.logging;

import roj.collect.HashSet;
import roj.collect.Hasher;
import roj.compiler.plugins.asm.ASM;
import roj.config.JsonSerializer;
import roj.text.CharList;
import roj.text.Formatter;

import java.io.PrintStream;
import java.util.List;

/**
 * @author Roj233
 * @since 2022/6/1 5:09
 */
final class LogWriterJson extends LogWriter {
	static final ThreadLocal<LogWriterJson> LOCAL = ThreadLocal.withInitial(LogWriterJson::new);

	final void log(LogContext ctx, Level level, CharSequence msg, Throwable ex, Object[] args, int argc) {
		JsonSerializer ser = new JsonSerializer();
		ser.emitMap();

		ser.emitKey("context");
		ser.emitMap();

		ser.emitKey("thread");
		ser.emit(tmpCtx.get("THREAD").toString());
		ser.emitKey("level");
		ser.emit(level.name());
		ser.emitKey("logger");
		ser.emit(ctx.name());

		CharList sb = this.sb;
		MyMap m = tmpCtx;
		m.components = ctx.getComponents();

		List<Formatter> components = ctx.getComponents();
		for (int i = 0; i < components.size(); i++) {
			ser.emitKey(Integer.toString(i));

			sb.clear();
			ser.valString(components.get(i).format(m, sb));
		}

		ser.pop();

		ser.emitKey("message");
		if (argc > 0) {
			sb.clear();
			replaceArg(sb, msg.toString(), args, argc);
			ser.valString(sb);
		}
		else ser.valString(msg);

		if (ex != null) {
			ser.emitKey("exception");
			writeException(ex, new HashSet<>(Hasher.identity()), ser);
		}

		CharList json = ser.getValue();
		var dst = ctx.destination();
		try {
			dst.getAndLock().append(json);
			dst.unlockAndFlush();
		} catch (Exception e) {
			try { dst.unlockAndFlush(); } catch (Throwable ignored) {}
			try {
				PrintStream prevErr = System.err;
				prevErr.println("LogDestination ["+dst.getClass().getName()+"] has thrown an exception during logging");
				e.printStackTrace(prevErr);
			} catch (Throwable ignored) {}
		} finally {
			json._free();
		}
	}

	private static void writeException(Throwable ex, HashSet<Throwable> dejavu, JsonSerializer ser) {
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
				if (ASM.TARGET_JAVA_VERSION > 8) {
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