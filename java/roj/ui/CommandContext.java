package roj.ui;

import roj.collect.MyHashMap;

import java.util.Map;

/**
 * @author Roj234
 * @since 2023/11/21 0021 17:34
 */
public class CommandContext {
	public final String context;
	private final MyHashMap<String, Object> map = new MyHashMap<>();
	public CommandContext(String context, Map<String, Object> map) { this.context = context; this.map.putAll(map); }

	public <T> T argument(String name, Class<T> type) { return type.cast(map.get(name)); }
	public <T> T argument(String name, Class<T> type, T defValue) {
		T argument = argument(name, type);
		return argument == null ? defValue : argument;
	}

	public void put(String name, Object value) {map.put(name, value);}
}