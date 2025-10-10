package roj.text.logging;

import org.jetbrains.annotations.Unmodifiable;
import roj.config.node.ConfigValue;
import roj.config.node.ListValue;
import roj.config.node.MapValue;
import roj.config.node.Type;
import roj.text.CharList;
import roj.text.DateFormat;
import roj.text.Formatter;
import roj.text.TextUtil;
import roj.ui.Tty;

import java.io.PrintStream;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * @author Roj233
 * @since 2022/6/1 5:09
 */
public final class LogContext {
	private LogContext parent;
	private Map<String, LogContext> children = Collections.emptyMap();

	private String name;
	private Level level;

	private Formatter format;

	public record Appender(LogAppender appender, Level threshold, Formatter format) {
		public Appender(LogAppender appender) {
			this(appender, null, null);
		}
	}
	private Appender[] appenders;

	private Logger logger;

	LogContext(LogContext parent, String name) {
		if (parent.children.isEmpty())
			parent.children = new HashMap<>();
		parent.children.put(name, this);
		this.parent = parent;
		this.name = name;
	}

	LogContext() {
		name = "root";
		level = Level.INFO;
		format = new Template(FMT, "[%d{MM-DD\" \"HH:ii:ss}] %padLeft{5}%level [%limit{10}%logger] ");
		appender(LogAppender.console());
	}

	LogContext(boolean a) {
		name = "root";
	}

	static final Map<String, BiFunction<String, Formatter, Formatter>> FMT = new roj.collect.HashMap<>();
	static {
		FMT.put("d", (params, prev) -> {
			if (prev != null) throw new IllegalArgumentException("%d does not support value");
			DateFormat format1 = DateFormat.create(params);

			return (env, sb) -> {
				TimeZone tz = env.containsKey("tz") ? (TimeZone) env.get("tz") : DateFormat.getLocalTimeZone();
				format1.format(System.currentTimeMillis(), sb, tz);
				return sb;
			};
		});
		FMT.put("padLeft", (params, prev) -> {
			if (prev == null) throw new IllegalArgumentException("%padLeft requires value");
			int width = Integer.parseInt(params.trim());
			return (env, sb) -> {
				CharList temp = new CharList();
				prev.format(env, temp);

				int padCount1 = width - temp.length();
				temp.appendToAndFree(sb.padEnd(' ', padCount1));
				return sb;
			};
		});
		FMT.put("padRight", (params, prev) -> {
			if (prev == null) throw new IllegalArgumentException("%padRight requires value");
			int width = Integer.parseInt(params.trim());
			return (env, sb) -> {
				CharList temp = new CharList();
				prev.format(env, temp);

				int padCount = width - temp.length();
				temp.appendToAndFree(sb);
				sb.padEnd(' ', padCount);
				return sb;
			};
		});
		FMT.put("limit", (params, prev) -> {
			if (prev == null) throw new IllegalArgumentException("%limit requires value");
			int width = Integer.parseInt(params.trim());
			return (env, sb) -> {
				CharList temp = new CharList();
				prev.format(env, temp);

				int len = Math.min(width, temp.length());
				sb.append(temp, 0, len);
				temp._free();
				return sb;
			};
		});
		FMT.put("color", (params, prev) -> {
			if (prev != null) throw new IllegalArgumentException("%color does not support value");
			if (!Tty.IS_RICH) return Formatter.constant("");
			return (env, sb) -> sb.append("\u001b[").append(((Level) env.get("level")).color).append('m');
		});
	}

	public static void loadFromConfig(MapValue config) {
		var oldRoot = Logger.getRootContext();
		LogContext root = loadFromConfig(config, oldRoot);
		Logger.setRootContext(root);
	}
	public static LogContext loadFromConfig(MapValue config, LogContext oldRoot) {
		Map<String, Appender> appenders = new HashMap<>();
		Map<String, Formatter> formatters = new HashMap<>();
		Function<String, Formatter> ccb = s -> new Template(FMT, s);
		formatters.put("", null);

		LogContext root = new LogContext(true);

		for (Map.Entry<String, ConfigValue> entry : config.getMap("appenders").entrySet()) {
			MapValue data = entry.getValue().asMap();

			LogAppender appender = switch (data.getString("type")) {
				case "CONSOLE" -> LogAppender.console();
				case "FILE" -> new AdvancedFileAppender(data);
				default -> throw new UnsupportedOperationException("Unknown appender " + data);
			};

			String fmt = data.getString("format");
			if (data.containsKey("ansi_format") && Tty.IS_RICH)
				fmt = data.getString("ansi_format");

			Formatter format = formatters.computeIfAbsent(fmt, ccb);
			var info = new Appender(appender, null, format);
			appenders.put(entry.getKey(), info);
		}

		for (Map.Entry<String, ConfigValue> entry : config.getMap("loggers").entrySet()) {
			String name = entry.getKey();
			LogContext ctx = name.isEmpty() ? root : root.child(name);

			MapValue data = entry.getValue().asMap();
			if (data.containsKey("level")) ctx.level = Level.valueOf(data.getString("level"));
			if (data.containsKey("name")) ctx.name = data.getString("name");
			if (data.containsKey("format")) ctx.format = formatters.computeIfAbsent(data.getString("format"), ccb);
			// variables are removed, use MDC.put(String, Object) instead.

			if (data.containsKey("appenders")) {
				ListValue appendersList = data.getList("appenders");
				Appender[] array = new Appender[appendersList.size()];

				for (int i = 0; i < appendersList.size(); i++) {
					ConfigValue value = appendersList.get(i);
					Appender appender;
					if (value.mayCastTo(Type.MAP)) {
						MapValue conf = value.asMap();

						appender = appenders.get(conf.getString("ref"));
						if (appender == null) throw new NullPointerException("Could not find appender " + conf.getString("ref"));

						Level threshold = appender.threshold;
						if (conf.containsKey("threshold")) {
							threshold = Level.valueOf(conf.getString("threshold"));
						}

						Formatter format = appender.format;
						if (conf.containsKey("format")) {
							format = formatters.computeIfAbsent(data.getString("format"), ccb);
						}

						appender = new Appender(appender.appender, threshold, format);
					} else {
						appender = appenders.get(value.asString());
						if (appender == null) throw new NullPointerException("Could not find appender " + value.asString());
					}

					array[i] = appender;
				}

				ctx.appenders = array;
			}

			if (oldRoot != null) {
				var oldCtx = name.isEmpty() ? oldRoot : oldRoot.child(name);
				extracted(oldCtx, ctx);
			}
		}

		return root;
	}

	private static void extracted(LogContext oldCtx, LogContext ctx) {
		var instance = oldCtx.logger;
		if (instance != null) instance.ctx = ctx;

		for (var entry : oldCtx.children.entrySet()) {
			if (entry.getValue().children != null) {
				extracted(entry.getValue(), ctx.child(entry.getKey()));
			}
		}
	}

	public static void setupDefaultConsoleLogFormat() {
		if (Tty.IS_RICH) {
			var dc = DateFormat.create("MM-DD\" \"HH:ii:ss");
			Logger.getRootContext().format = (env, sb) -> {
				TimeZone tz = env.containsKey("tz") ? (TimeZone) env.get("tz") : DateFormat.getLocalTimeZone();
				dc.format(System.currentTimeMillis(), sb.append('['), tz);

				Level level1 = (Level) env.get("level");
				sb.append("]\u001b[").append(level1.color).append("m[").append(env.get("logger"));
				if (level1.ordinal() > Level.WARN.ordinal())
					sb.append("][").append(env.get("thread"));

				return sb.append("]\u001b[0m: ");
			};
		}
	}

	public Formatter format() {
		var self = this;
		while (self.format == null) {
			self = self.parent;
			if (self == null) return null;
		}
		return self.format;
	}
	public void format(Formatter t) { format = t; }

	public String name() { return name; }
	public LogContext name(String name) { this.name = name; return this; }

	@Unmodifiable public Appender[] appenders() {
		var self = this;
		while (self.appenders == null) {
			self = self.parent;
		}
		return self.appenders;
	}
	public LogContext appenders(Appender... appenders) { this.appenders = appenders;return this; }

	public LogContext appender(LogAppender appender) { appenders = new Appender[] {new Appender(Objects.requireNonNull(appender))};return this; }
	public LogContext appender(Appender appender) { appenders = new Appender[] {Objects.requireNonNull(appender)};return this; }

	public Level level() {
		var self = this;
		while (self.level == null) {
			self = self.parent;
			if (self == null) return null;
		}
		return self.level;
	}
	public LogContext level(Level level) {this.level = level;return this;}

	public LogContext child(String name) {
		int slash = name.indexOf('/');
		if (slash >= 0) {
			var self = this;
			for (String path : TextUtil.split(name, '/')) {
				self = self.child(path);
			}
			return self;
		}

		synchronized (this) {
			LogContext ctx = children.get(name);
			return ctx != null ? ctx : new LogContext(this, name);
		}
	}

	public synchronized Logger logger() {return logger == null ? logger = new Logger(this) : logger;}

	LogWriter writer() {return LogWriter.LOCAL.get();}

	void log(LogWriter writer, Level level, CharSequence msg, Throwable ex, Object[] args, int argc) {
		Formatter prevFormatter = null;
		CharList packet = null;

		for (Appender appender : appenders()) {
			if (appender.threshold != null && level.ordinal() < appender.threshold.ordinal()) continue;
			Formatter format = appender.format != null ? appender.format : format();
			if (prevFormatter != format) {
				prevFormatter = format;
				// TODO json
				if (format == null) {
					packet = writer.serializeJson(this, level, msg, ex, args, argc);
				} else {
					packet = writer.serialize(this, format, level, msg, ex, args, argc);
				}
			}

			try {
				appender.appender.append(packet);
			} catch (Exception e) {
				try {
					PrintStream prevErr = System.err;
					prevErr.println("Exception when writing Appender["+appender.getClass().getName()+"]");
					e.printStackTrace(prevErr);
				} catch (Exception ignored) {
				}
			}
		}
	}
}