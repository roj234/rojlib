package roj.config.serial;

import java.io.Closeable;
import java.io.IOException;

/**
 * @author Roj234
 * @since 2022/11/15 0015 22:37
 */
public interface CVisitor extends Closeable {
	void value(boolean l);
	default void value(byte l) { value((int) l); }
	default void value(short l) { value((int) l); }
	default void value(char l) { value(String.valueOf(l)); }
	void value(int l);
	void value(long l);
	default void value(float l) { value((double) l); }
	void value(double l);
	void value(String l);
	void valueNull();

	default void valueDate(long value) { value(value); }
	default void valueTimestamp(long value) { value(value); }

	default boolean supportArray() {return false;}
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

	void valueMap();
	default void valueMap(int size) { valueMap(); }

	void key(String key);

	void valueList();
	default void valueList(int size) { valueList(); }

	void pop();

	default void comment(String comment) {}

	default void setProperty(String k, Object v) {}
	CVisitor reset();
	default void close() throws IOException {}
}