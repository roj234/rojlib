package roj.text.logging;

import roj.collect.SimpleList;
import roj.text.CharList;
import roj.text.logging.c.*;
import roj.text.logging.d.LDStream;
import roj.text.logging.d.LogDestination;

import java.util.List;
import java.util.Map;

/**
 * @author Roj233
 * @since 2022/6/1 5:09
 */
public class LogContext {
	private final List<LogComponent> components;
	private String name;
	private LogDestination destination;

	public LogContext(LogContext copy) {
		components = new SimpleList<>(copy.components);
		name = copy.name;
		destination = copy.destination;
	}

	public LogContext() {
		components = new SimpleList<>();
		components.add(LCString.of("["));
		components.add(LCTime.of("H:i:s"));
		components.add(LCString.of("]["));
		components.add(LCThreadName.INSTANCE);
		components.add(LCString.of("/"));
		components.add(LCMapValue.LEVEL);
		components.add(LCString.of("]: "));

		name = "Default";

		destination = LDStream.of(System.out);
	}

	public LogContext addComponent(LogComponent c) {
		components.add(c);
		return this;
	}

	public List<LogComponent> getComponents() {
		return components;
	}

	public LogContext name(String name) {
		this.name = name;
		return this;
	}

	public String name() {
		return name;
	}

	public LogDestination destination() {
		return destination;
	}

	public LogContext destination(LogDestination d) {
		this.destination = d;
		return this;
	}

	public Map<String, Object> getLocalMap() {
		return LogHelper.LOCAL.get().localMap;
	}

	protected void fillIn(CharList tmp, Level level) {
		Map<String, Object> m = getLocalMap();
		m.put("LEVEL", level);
		m.put("NAME", name);

		List<LogComponent> c = components;
		for (int i = 0; i < c.size(); i++) {
			c.get(i).appendTo(this, m, tmp);
		}
	}

	public CharSequence placeholderMissing() {
		return "Missing";
	}
}
