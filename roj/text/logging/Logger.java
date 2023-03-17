package roj.text.logging;

import roj.reflect.TraceUtil;

import java.io.PrintStream;

/**
 * @author Roj233
 * @since 2022/6/1 5:04
 */
public abstract class Logger {
	static PrintStream StdErr = System.err;
	static LogContext defaultContext = new LogContext();

	public static PrintStream getStdErr() {
		return StdErr;
	}

	public static void setStdErr(PrintStream stdErr) {
		StdErr = stdErr;
	}

	public static void setDefaultContext(LogContext c) {
		Logger.defaultContext = c;
	}

	public static LogContext getDefaultContext() {
		return defaultContext;
	}

	public static Logger getLogger() {
		return getLogger(TraceUtil.getCallerClass1().getSimpleName());
	}

	public static Logger getLogger(String name) {
		return getLogger(new LogContext(defaultContext).name(name));
	}

	public static Logger getLogger(LogContext ctx) {
		return new SimpleLogger(ctx);
	}

	protected LogContext ctx;
	protected Level level;

	public void setLevel(Level level) {
		this.level = level;
	}

	public Level getLevel() {
		return level;
	}

	public LogContext getContext() {
		return ctx;
	}

	public final void catching(Throwable e) {
		log(Level.ERROR, "Catching " + e.getClass().getSimpleName(), e);
	}

	public final void catching(String msg, Throwable e) {
		log(Level.ERROR, msg, e);
	}

	public final void debug(String msg, Object par0) {
		log(Level.DEBUG, msg, null, par0);
	}

	public final void info(String msg) {
		log(Level.INFO, msg, null);
	}

	public final void info(String msg, Object p0) {
		log(Level.INFO, msg, null, p0);
	}

	public final void info(String msg, Object p0, Object p1) {
		log(Level.INFO, msg, null, p0, p1);
	}

	public final void info(String msg, Object p0, Object p1, Object p2) {
		log(Level.INFO, msg, null, p0, p1, p2);
	}

	public final void info(String msg, Object p0, Object p1, Object p2, Object p3) {
		log(Level.INFO, msg, null, p0, p1, p2, p3);
	}

	public final void info(String msg, Object p0, Object p1, Object p2, Object p3, Object p4) {
		log(Level.INFO, msg, null, p0, p1, p2, p3, p4);
	}

	public final void info(String msg, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5) {
		log(Level.INFO, msg, null, p0, p1, p2, p3, p4, p5);
	}

	public void log(Level lv, CharSequence msg, Throwable ex) {
		if (!level.canLog(lv)) return;

		LogHelper h = LogHelper.LOCAL.get();
		h.doLog(ctx, lv, msg, ex, null, 0);
	}

	public void log(Level lv, CharSequence msg, Throwable ex, Object p0) {
		if (!level.canLog(lv)) return;

		LogHelper h = LogHelper.LOCAL.get();

		Object[] f = h.holder;
		f[0] = p0;
		h.doLog(ctx, lv, msg, ex, f, 1);
		f[0] = null;
	}

	public void log(Level lv, CharSequence msg, Throwable ex, Object p0, Object p1) {
		if (!level.canLog(lv)) return;

		LogHelper h = LogHelper.LOCAL.get();

		Object[] f = h.holder;
		f[0] = p0;
		f[1] = p1;
		h.doLog(ctx, lv, msg, ex, f, 2);
		f[0] = null;
		f[1] = null;
	}

	public void log(Level lv, CharSequence msg, Throwable ex, Object p0, Object p1, Object p2) {
		if (!level.canLog(lv)) return;

		LogHelper h = LogHelper.LOCAL.get();

		Object[] f = h.holder;
		f[0] = p0;
		f[1] = p1;
		f[2] = p2;
		h.doLog(ctx, lv, msg, ex, f, 3);
		f[0] = null;
		f[1] = null;
		f[2] = null;
	}

	public void log(Level lv, CharSequence msg, Throwable ex, Object p0, Object p1, Object p2, Object p3) {
		if (!level.canLog(lv)) return;

		LogHelper h = LogHelper.LOCAL.get();

		Object[] f = h.holder;
		f[0] = p0;
		f[1] = p1;
		f[2] = p2;
		f[3] = p3;
		h.doLog(ctx, lv, msg, ex, f, 4);
		f[0] = null;
		f[1] = null;
		f[2] = null;
		f[3] = null;
	}

	public void log(Level lv, CharSequence msg, Throwable ex, Object p0, Object p1, Object p2, Object p3, Object p4) {
		if (!level.canLog(lv)) return;

		LogHelper h = LogHelper.LOCAL.get();

		Object[] f = h.holder;
		f[0] = p0;
		f[1] = p1;
		f[2] = p2;
		f[3] = p3;
		f[4] = p4;
		h.doLog(ctx, lv, msg, ex, f, 6);
		for (int i = 0; i < 5; i++) f[i] = null;
	}

	public void log(Level lv, CharSequence msg, Throwable ex, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5) {
		if (!level.canLog(lv)) return;

		LogHelper h = LogHelper.LOCAL.get();

		Object[] f = h.holder;
		f[0] = p0;
		f[1] = p1;
		f[2] = p2;
		f[3] = p3;
		f[4] = p4;
		f[5] = p5;
		h.doLog(ctx, lv, msg, ex, f, 6);
		for (int i = 0; i < 6; i++) f[i] = null;
	}

	public void log(Level lv, CharSequence msg, Throwable ex, Object... pars) {
		if (!level.canLog(lv)) return;

		LogHelper h = LogHelper.LOCAL.get();
		h.doLog(ctx, lv, msg, null, pars, pars.length);
	}
}
