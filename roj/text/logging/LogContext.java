package roj.text.logging;

import roj.collect.SimpleList;
import roj.text.Template;
import roj.text.TextUtil;
import roj.text.logging.c.LCTime;
import roj.text.logging.c.LogComponent;
import roj.text.logging.d.LDStream;
import roj.text.logging.d.LogDestination;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author Roj233
 * @since 2022/6/1 5:09
 */
public class LogContext {
	private String name;

	private Template prefix;
	private List<LogComponent> components;

	private LogDestination destination;

	Logger logger;

	public LogContext(LogContext copy) {
		prefix = copy.prefix;
		components = new SimpleList<>(copy.components);
		name = copy.name;
		destination = copy.destination;
		logger = null;
	}

	public LogContext() {
		name = "root";
		prefix = Template.compile("[${0}][${THREAD}/${LEVEL}]: ");
		components = Collections.singletonList(LCTime.of("H:i:s"));
		destination = LDStream.of(System.out, TextUtil.ConsoleCharset);
	}

	public List<LogComponent> getComponents() { return components; }
	public Template getPrefix() { return prefix; }
	public void setPrefix(Template t) { prefix = t; }

	public LogContext format(String template, LogComponent... components) {
		this.components = Arrays.asList(components);
		this.prefix = Template.compile(template);
		return this;
	}
	public LogContext name(String name) { this.name = name; return this; }
	public String name() { return name; }

	public LogDestination destination() { return destination; }
	public LogContext destination(LogDestination d) { destination = d; return this; }
}