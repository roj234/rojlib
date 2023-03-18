package roj.config.serial;

/**
 * @author Roj234
 * @since 2022/11/15 0015 22:37
 */
public interface CVisitor {
	default void value(byte l) { value((int) l); }
	default void value(short l) { value((int) l); }
	default void value(char l) { value(String.valueOf(l)); }
	void value(int l);
	void value(String l);
	void value(long l);
	default void value(float l) { value((double) l); }
	void value(double l);
	void value(boolean l);
	void valueNull();
	default void value(byte[] ba) {
		valueList(ba.length);
		for (byte b : ba) value(b);
		pop();
	}
	default void value(int[] ia) {
		valueList(ia.length);
		for (int i : ia) value(i);
		pop();
	}
	default void value(long[] la) {
		valueList(la.length);
		for (long l : la) value(l);
		pop();
	}
	void pop();

	void key(String key);

	void valueMap();
	default void valueMap(int size) { valueMap(); }

	void valueList();
	default void valueList(int size) { valueList(); }

	default void comment(String comment) {}

	default void valueDate(long value) { value(value); }
	default void valueTimestamp(long value) { value(value); }

	default void rawString(CharSequence v) {
		throw new UnsupportedOperationException();
	}
}
