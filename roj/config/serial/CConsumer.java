package roj.config.serial;

/**
 * @author Roj234
 * @since 2022/11/15 0015 22:37
 */
public interface CConsumer {
	void value(int l);
	void value(String l);
	void value(long l);
	void value(double l);
	void value(boolean l);
	void valueNull();
	void pop();
	void key(String key);
	void valueMap();
	void valueList();
	default void comment(String comment) {}

	default void valueDate(long value) {
		value(value);
	}
	default void valueTimestamp(long value) {
		value(value);
	}
}
