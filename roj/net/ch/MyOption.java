package roj.net.ch;

import java.net.SocketOption;

/**
 * @author Roj234
 * @since 2023/5/22 0022 20:07
 */
final class MyOption<T> implements SocketOption<T> {
	private final String name;
	private final Class<T> type;

	MyOption(String name, Class<T> type) {
		this.name = name;
		this.type = type;
	}

	public String name() {return name;}
	public Class<T> type() {return type;}
}
