package roj.text.logging;

import roj.reflect.Reflection;

/**
 * @author Roj233
 * @since 2022/6/1 5:04
 */
public final class Logger {
	private static LogContext rootContext = new LogContext();
	public static void setRootContext(LogContext c) { rootContext = c; }
	public static LogContext getRootContext() { return rootContext; }

	public static Logger getLogger() { return getLogger(Reflection.getCallerClass(2).getSimpleName()); }
	public static Logger getLogger(String name) {return rootContext.child(name).logger();}

	public static final Logger FALLBACK = getLogger("RojLib");

	LogContext ctx;
	Logger(LogContext ctx) {this.ctx = ctx;}

	public void setLevel(Level level) {ctx.level(level);}
	public Level getLevel() {return ctx.level();}
	public boolean canLog(Level level) {return getLevel().canLog(level);}

	public LogContext context() {return ctx;}

	public final void trace(String msg) { log(Level.TRACE, msg, null); }
	public final void trace(String msg, Object p0) { log(Level.TRACE, msg, null, p0); }
	public final void trace(String msg, Object p0, Object p1) { log(Level.TRACE, msg, null, p0, p1); }
	public final void trace(String msg, Object p0, Object p1, Object p2) { log(Level.TRACE, msg, null, p0, p1, p2); }

	public final void debug(String msg) { log(Level.DEBUG, msg, null); }
	public final void debug(String msg, Object p0) { log(Level.DEBUG, msg, null, p0); }
	public final void debug(String msg, Object p0, Object p1) { log(Level.DEBUG, msg, null, p0, p1); }
	public final void debug(String msg, Object p0, Object p1, Object p2) { log(Level.DEBUG, msg, null, p0, p1, p2); }

	public final void debug(String msg, Throwable ex) { log(Level.DEBUG, msg, ex); }
	public final void debug(String msg, Throwable ex, Object p0) { log(Level.DEBUG, msg, ex, p0); }
	public final void debug(String msg, Throwable ex, Object p0, Object p1) { log(Level.DEBUG, msg, ex, p0, p1); }
	public final void debug(String msg, Throwable ex, Object ...p) { log(Level.DEBUG, msg, ex, p); }

	public final void info(String msg) { log(Level.INFO, msg, null); }
	public final void info(String msg, Object p0) { log(Level.INFO, msg, null, p0); }
	public final void info(String msg, Object p0, Object p1) { log(Level.INFO, msg, null, p0, p1); }
	public final void info(String msg, Object p0, Object p1, Object p2) { log(Level.INFO, msg, null, p0, p1, p2); }
	public final void info(String msg, Object ...p) { log(Level.INFO, msg, null, p); }

	public final void info(String msg, Throwable ex) { log(Level.INFO, msg, ex); }
	public final void info(String msg, Throwable ex, Object p0) { log(Level.INFO, msg, ex, p0); }
	public final void info(String msg, Throwable ex, Object p0, Object p1) { log(Level.INFO, msg, ex, p0, p1); }
	public final void info(String msg, Throwable ex, Object ...p) { log(Level.INFO, msg, ex, p); }

	public final void warn(String msg) { log(Level.WARN, msg, null); }
	public final void warn(String msg, Object p0) { log(Level.WARN, msg, null, p0); }
	public final void warn(String msg, Object p0, Object p1) { log(Level.WARN, msg, null, p0, p1); }
	public final void warn(String msg, Object p0, Object p1, Object p2) { log(Level.WARN, msg, null, p0, p1, p2); }
	public final void warn(String msg, Object ...p) { log(Level.ERROR, msg, null, p); }

	public final void warn(String msg, Throwable ex) { log(Level.WARN, msg, ex); }
	public final void warn(String msg, Throwable ex, Object p0) { log(Level.WARN, msg, ex, p0); }
	public final void warn(String msg, Throwable ex, Object p0, Object p1) { log(Level.WARN, msg, ex, p0, p1); }
	public final void warn(String msg, Throwable ex, Object ...p) { log(Level.WARN, msg, ex, p); }


	public final void error(String msg) { log(Level.ERROR, msg, null); }
	public final void error(String msg, Object p0) { log(Level.ERROR, msg, null, p0); }
	public final void error(String msg, Object p0, Object p1) { log(Level.ERROR, msg, null, p0, p1); }
	public final void error(String msg, Object p0, Object p1, Object p2) { log(Level.ERROR, msg, null, p0, p1, p2); }
	public final void error(String msg, Object ...p) { log(Level.ERROR, msg, null, p); }

	public final void error(Throwable e) { log(Level.ERROR, "Catching {}", e, e.getClass().getSimpleName()); }
	public final void error(String msg, Throwable ex) { log(Level.ERROR, msg, ex); }
	public final void error(String msg, Throwable ex, Object p0) { log(Level.ERROR, msg, ex, p0); }
	public final void error(String msg, Throwable ex, Object p0, Object p1) { log(Level.ERROR, msg, ex, p0, p1); }
	public final void error(String msg, Throwable ex, Object ...p) { log(Level.ERROR, msg, ex, p); }


	public final void fatal(String msg) { log(Level.FATAL, msg, null); }
	public final void fatal(String msg, Object p0) { log(Level.FATAL, msg, null, p0); }
	public final void fatal(String msg, Object p0, Object p1) { log(Level.FATAL, msg, null, p0, p1); }
	public final void fatal(String msg, Object p0, Object p1, Object p2) { log(Level.FATAL, msg, null, p0, p1, p2); }

	public final void fatal(Throwable e) { log(Level.FATAL, "Catching {}", e, e.getClass().getSimpleName()); }
	public final void fatal(String msg, Throwable ex) { log(Level.FATAL, msg, ex); }
	public final void fatal(String msg, Throwable ex, Object p0) { log(Level.FATAL, msg, ex, p0); }
	public final void fatal(String msg, Throwable ex, Object p0, Object p1) { log(Level.FATAL, msg, ex, p0, p1); }

	public final void log(Level lv, CharSequence msg, Throwable ex) {
		if (!canLog(lv)) return;

		LogWriter h = ctx.writer();
		ctx.log(h, lv, msg, ex, null, 0);
	}
	public final void log(Level lv, String msg, Throwable ex, Object p0) {
		if (!canLog(lv)) return;

		LogWriter h = ctx.writer();

		Object[] f = h.sharedArguments;
		f[0] = p0;
		ctx.log(h, lv, msg, ex, f, 1);
		f[0] = null;
	}
	public final void log(Level lv, String msg, Throwable ex, Object p0, Object p1) {
		if (!canLog(lv)) return;

		LogWriter h = ctx.writer();

		Object[] f = h.sharedArguments;
		f[0] = p0;f[1] = p1;
		ctx.log(h, lv, msg, ex, f, 2);
		f[0] = null;f[1] = null;
	}
	public final void log(Level lv, String msg, Throwable ex, Object p0, Object p1, Object p2) {
		if (!canLog(lv)) return;

		LogWriter h = ctx.writer();

		Object[] f = h.sharedArguments;
		f[0] = p0;f[1] = p1;f[2] = p2;
		ctx.log(h, lv, msg, ex, f, 3);
		f[0] = null;f[1] = null;f[2] = null;
	}
	public final void log(Level lv, String msg, Throwable ex, Object p0, Object p1, Object p2, Object p3) {
		if (!canLog(lv)) return;

		LogWriter h = ctx.writer();

		Object[] f = h.sharedArguments;
		f[0] = p0;f[1] = p1;f[2] = p2;f[3] = p3;
		ctx.log(h, lv, msg, ex, f, 4);
		f[0] = null;f[1] = null;f[2] = null;f[3] = null;
	}
	public final void log(Level lv, String msg, Throwable ex, Object... pars) {
		if (!canLog(lv)) return;

		LogWriter h = ctx.writer();
		ctx.log(h, lv, msg, ex, pars, pars.length);
	}
}