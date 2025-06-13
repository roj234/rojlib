package roj.text.logging;

import roj.collect.HashSet;
import roj.collect.Hasher;
import roj.compiler.plugins.asm.ASM;
import roj.config.serial.ToJson;
import roj.text.CharList;
import roj.text.logging.c.LogComponent;

import java.io.PrintStream;
import java.util.List;

/**
 * @author Roj233
 * @since 2022/6/1 5:09
 */
final class LogWriterJson extends LogWriter {
	static final ThreadLocal<LogWriterJson> LOCAL = ThreadLocal.withInitial(LogWriterJson::new);

	final void log(LogContext ctx, Level level, CharSequence msg, Throwable ex, Object[] args, int argc) {
		ToJson ser = new ToJson();
		ser.valueMap();

		ser.key("context");
		ser.valueMap();

		ser.key("thread");
		ser.value(tmpCtx.get("THREAD").toString());
		ser.key("level");
		ser.value(level.name());
		ser.key("logger");
		ser.value(ctx.name());

		CharList sb = this.sb;
		MyMap m = tmpCtx;
		m.components = ctx.getComponents();

		List<LogComponent> components = ctx.getComponents();
		for (int i = 0; i < components.size(); i++) {
			ser.key(Integer.toString(i));

			sb.clear();
			components.get(i).accept(m, sb);
			ser.valString(sb);
		}

		ser.pop();

		ser.key("message");
		if (argc > 0) {
			sb.clear();
			replaceArg(sb, msg.toString(), args, argc);
			ser.valString(sb);
		}
		else ser.valString(msg);

		if (ex != null) {
			ser.key("exception");
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

	private static void writeException(Throwable ex, HashSet<Throwable> dejavu, ToJson ser) {
		if (!dejavu.add(ex)) {
			ser.value("circular reference");
			return;
		}

		ser.valueMap();

		ser.key("type");
		ser.value(ex.getClass().getSimpleName());

		if (ex.getMessage() != null) {
			ser.key("message");
			ser.value(ex.getMessage());
		}

		var trace = LogHelper.INSTANCE.getStackTrace(ex);
		if (trace.length > 0) {
			ser.key("trace");
			ser.valueList();

			for (var el : trace) {
				ser.valueMap();
				ser.key("class");
				ser.value(el.getClassName());
				ser.key("method");
				ser.value(el.getMethodName());
				if (el.getLineNumber() != -1) {
					ser.key("line");
					ser.value(el.getLineNumber());
				}
				if (el.getFileName() != null) {
					ser.key("file");
					ser.value(el.getFileName());
				}
				if (ASM.TARGET_JAVA_VERSION > 8) {
					if (el.getModuleName() != null) {
						ser.key("module");
						ser.value(el.getMethodName());
					}
				}
				ser.pop();
			}
		}

		List<Throwable> suppressed = LogHelper.INSTANCE.getSuppressed(ex);
		if (suppressed != null) {
			ser.key("suppressed");
			ser.valueList();
			for (Throwable th : suppressed) {
				writeException(th, dejavu, ser);
			}
			ser.pop();
		}

		Throwable cause = ex.getCause();
		if (cause != null) {
			ser.key("cause");
			writeException(cause, dejavu, ser);
		}

		ser.pop();
	}
}