package roj.text.logging;

import roj.reflect.TraceUtil;

/**
 * @author Roj233
 * @since 2022/6/1 5:04
 */
public final class Logger {
	private static LogContext rootContext = new LogContext();
	public static void setRootContext(LogContext c) { rootContext = c; }
	public static LogContext getRootContext() { return rootContext; }

	public static Logger getLogger() { return getLogger(TraceUtil.getCallerClass1().getSimpleName()); }
	public static Logger getLogger(String name) { return getLogger(new LogContext(rootContext).name(name)); }
	public static Logger getLogger(LogContext ctx) { return ctx.logger == null ? ctx.logger = new Logger(ctx) : ctx.logger; }

	LogContext ctx;
	Level level;

	private Logger(LogContext ctx) {
		this.ctx = ctx;
		this.level = Level.DEBUG;
	}

	public void setLevel(Level level) { this.level = level; }
	public Level getLevel() { return level; }

	public LogContext getContext() {
		return ctx;
	}

	public final void error(Throwable e) { log(Level.ERROR, "Catching {}", e, e.getClass().getSimpleName()); }
	public final void error(String msg, Throwable e) { log(Level.ERROR, msg, e); }

	public final void debug(String msg, Object par0) { log(Level.DEBUG, msg, null, par0); }

	public final void info(String msg) { log(Level.INFO, msg, null); }
	public final void info(String msg, Object p0) { log(Level.INFO, msg, null, p0); }
	public final void info(String msg, Object p0, Object p1) { log(Level.INFO, msg, null, p0, p1); }
	public final void info(String msg, Object p0, Object p1, Object p2) { log(Level.INFO, msg, null, p0, p1, p2); }
	public final void info(String msg, Object p0, Object p1, Object p2, Object p3) { log(Level.INFO, msg, null, p0, p1, p2, p3); }
	public final void info(String msg, Object p0, Object p1, Object p2, Object p3, Object p4) { log(Level.INFO, msg, null, p0, p1, p2, p3, p4); }
	public final void info(String msg, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5) { log(Level.INFO, msg, null, p0, p1, p2, p3, p4, p5); }

	public final void log(Level lv, CharSequence msg, Throwable ex) {
		if (!level.canLog(lv)) return;

		LogHelper h = LogHelper.LOCAL.get();
		h.doLog(ctx, lv, msg, ex, null, 0);
	}
	public final void log(Level lv, String msg, Throwable ex, Object p0) {
		if (!level.canLog(lv)) return;

		LogHelper h = LogHelper.LOCAL.get();

		Object[] f = h.holder;
		f[0] = p0;
		h.doLog(ctx, lv, msg, ex, f, 1);
		f[0] = null;
	}
	public final void log(Level lv, String msg, Throwable ex, Object p0, Object p1) {
		if (!level.canLog(lv)) return;

		LogHelper h = LogHelper.LOCAL.get();

		Object[] f = h.holder;
		f[0] = p0;f[1] = p1;
		h.doLog(ctx, lv, msg, ex, f, 2);
		f[0] = null;f[1] = null;
	}
	public final void log(Level lv, String msg, Throwable ex, Object p0, Object p1, Object p2) {
		if (!level.canLog(lv)) return;

		LogHelper h = LogHelper.LOCAL.get();

		Object[] f = h.holder;
		f[0] = p0;f[1] = p1;f[2] = p2;
		h.doLog(ctx, lv, msg, ex, f, 3);
		f[0] = null;f[1] = null;f[2] = null;
	}
	public final void log(Level lv, String msg, Throwable ex, Object p0, Object p1, Object p2, Object p3) {
		if (!level.canLog(lv)) return;

		LogHelper h = LogHelper.LOCAL.get();

		Object[] f = h.holder;
		f[0] = p0;f[1] = p1;f[2] = p2;f[3] = p3;
		h.doLog(ctx, lv, msg, ex, f, 4);
		for (int i = 0; i < 4; i++) f[i] = null;
	}
	public final void log(Level lv, String msg, Throwable ex, Object p0, Object p1, Object p2, Object p3, Object p4) {
		if (!level.canLog(lv)) return;

		LogHelper h = LogHelper.LOCAL.get();

		Object[] f = h.holder;
		f[0] = p0;f[1] = p1;f[2] = p2;f[3] = p3;f[4] = p4;
		h.doLog(ctx, lv, msg, ex, f, 6);
		for (int i = 0; i < 5; i++) f[i] = null;
	}
	public final void log(Level lv, String msg, Throwable ex, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5) {
		if (!level.canLog(lv)) return;

		LogHelper h = LogHelper.LOCAL.get();

		Object[] f = h.holder;
		f[0] = p0;f[1] = p1;f[2] = p2;f[3] = p3;f[4] = p4;f[5] = p5;
		h.doLog(ctx, lv, msg, ex, f, 6);
		for (int i = 0; i < 6; i++) f[i] = null;
	}
	public final void log(Level lv, String msg, Throwable ex, Object... pars) {
		if (!level.canLog(lv)) return;

		LogHelper h = LogHelper.LOCAL.get();
		h.doLog(ctx, lv, msg, null, pars, pars.length);
	}
}
