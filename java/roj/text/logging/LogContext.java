package roj.text.logging;

import roj.text.Formatter;
import roj.text.Template;
import roj.text.logging.c.LCTime;
import roj.text.logging.c.LogComponent;
import roj.text.logging.d.LogDestination;

import java.util.Collections;
import java.util.List;

/**
 * @author Roj233
 * @since 2022/6/1 5:09
 */
public class LogContext {
	LogContext parent;

	private String name;
	private Level level;

	private Formatter prefix;
	private List<LogComponent> components;

	private LogDestination destination;

	Logger logger;

	public LogContext(LogContext parent, String name) {
		this.parent = parent;
		this.name = name;
	}

	LogContext() {
		name = "root";
		level = Level.DEBUG;
		prefix = Template.compile("[${0}][${THREAD}/${LEVEL}]: ");
		components = Collections.singletonList(LCTime.of("H:i:s"));
		destination = () -> System.out;
	}

	public List<LogComponent> getComponents() { return components != null ? components : parent.getComponents(); }
	public void setComponents(List<LogComponent> components) {this.components = components;}
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