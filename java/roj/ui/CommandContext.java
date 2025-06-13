package roj.ui;

import roj.collect.HashMap;

import java.util.Map;

/**
 * @author Roj234
 * @since 2023/11/21 17:34
 */
public class CommandContext {
	public final String context;
	private final HashMap<String, Object> map = new HashMap<>();
	public CommandContext(String context, Map<String, Object> map) { this.context = context; this.map.putAll(map); }

	public <T> T argument(String name, Class<T> type) { return type.cast(map.get(name)); }
	public <T> T argument(String name, Class<T> type, T defValue) {
		T argument = argument(name, type);
		return argument == null ? defValue : argument;
	}

	public void put(String name, Object value) {map.put(name, value);}
}