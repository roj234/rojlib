package roj.text.logging;

import roj.text.Formatter;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * @author Roj233
 * @since 2022/6/1 5:09
 */
public final class LogContext {
	LogContext parent;

	private String name;
	private Level level;

	private Formatter prefix;
	private List<Formatter> components;

	private LogDestination destination;

	Logger logger;

	public LogContext(LogContext parent, String name) {
		this.parent = Objects.requireNonNull(parent);
		this.name = name;
	}

	LogContext() {
		name = "root";
		level = Level.INFO;
		prefix = Formatter.simple("[${0}][${THREAD}/${LEVEL}]: ");
		components = Collections.singletonList(Formatter.time("H:i:s"));
		destination = LogDestination.stdout();
	}

	public List<Formatter> getComponents() { return components != null ? components : parent.getComponents(); }
	public void setComponents(List<Formatter> components) {this.components = components;}
	public Formatter getPrefix() { return prefix != null ? prefix : parent.getPrefix(); }
	public void setPrefix(Formatter t) { prefix = t; }

	public String name() { return name; }
	public LogContext name(String name) { this.name = name; return this; }

	public LogDestination destination() { return destination != null ? destination : parent.destination(); }
	public LogContext destination(LogDestination d) { destination = d; return this; }

	public Level level() {return level != null ? level : parent.level;}
	public LogContext level(Level level) {this.level = level;return this;}

	LogWriter getWriter() {return getPrefix() == null ? LogWriterJson.LOCAL.get() : LogWriter.LOCAL.get();}

	public LogContext child(String name) {return new LogContext(this, this.name+"/"+name);}
}