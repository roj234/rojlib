package roj.config.serial;

import roj.util.TypedKey;

import java.io.Closeable;
import java.io.IOException;

/**
 * @author Roj234
 * @since 2022/11/15 22:37
 */
public interface CVisitor extends Closeable {
	void value(boolean b);
	default void value(byte i) { value((int) i); }
	default void value(short i) { value((int) i); }
	default void value(char i) { value(String.valueOf(i)); }
	void value(int i);
	void value(long i);
	default void value(float i) { value((double) i); }
	void value(double i);
	void value(String s);
	void valueNull();

	default void valueDate(long mills) { value(mills); }
	default void valueTimestamp(long mills) { value(mills); }
	default void valueTimestamp(long seconds, int nanos) {valueTimestamp(seconds * 1000 + nanos / 1_000_000);}

	default boolean supportArray() {return false;}
	default void value(byte[] array) {
		valueList(array.length);
		for (byte b : array) value(b);
		pop();
	}
	default void value(int[] array) {
		valueList(array.length);
		for (int i : array) value(i);
		pop();
	}
	default void value(long[] array) {
		valueList(array.length);
		for (long l : array) value(l);
		pop();
	}

	void valueMap();
	default void valueMap(int size) { valueMap(); }

	void key(String key);
	default void intKey(int key) {key(Integer.toString(key));}

	void valueList();
	default void valueList(int size) { valueList(); }

	void pop();

	default void comment(String comment) {}

	/**
	 * Use ordered (Linked) map
	 */
	TypedKey<Boolean> ORDERED_MAP = new TypedKey<>("generic:orderedMap");
	/**
	 * Max depth (list / map)
	 */
	TypedKey<Integer> MAX_DEPTH = new TypedKey<>("generic:maxDepth");
	default <T> void setProperty(TypedKey<T> k, T v) {}

	CVisitor reset();
	default void close() throws IOException {}
}