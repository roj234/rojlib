package roj.ui.terminal;

import roj.collect.MyHashMap;
import roj.ui.CLIConsole;

import java.util.Map;

/**
 * @author Roj234
 * @since 2023/11/21 0021 17:34
 */
public class CommandContext {
	private final MyHashMap<String, Object> map = new MyHashMap<>();
	public CommandContext(Map<String, Object> map) { this.map.putAll(map); }

	public <T> T argument(String name, Class<T> type) { return type.cast(map.get(name)); }
	public <T> T argument(String name, Class<T> type, T defValue) {
		T argument = argument(name, type);
		return argument == null ? defValue : argument;
	}

	public void writeToSystemIn(byte[] b, int off, int len) { CLIConsole.writeToSystemIn(b, off, len); }
}
