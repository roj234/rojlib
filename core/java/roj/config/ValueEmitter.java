package roj.config;

import roj.util.TypedKey;

import java.io.Closeable;
import java.io.IOException;

/**
 * @author Roj234
 * @since 2022/11/15 22:37
 */
public interface ValueEmitter extends Closeable {
	void emit(boolean b);
	default void emit(byte i) { emit((int) i); }
	default void emit(short i) { emit((int) i); }
	default void emit(char i) { emit(String.valueOf(i)); }
	void emit(int i);
	void emit(long i);
	default void emit(float i) { emit((double) i); }
	void emit(double i);
	void emit(String s);
	void emitNull();

	default void emitDate(long millis) { emit(millis); }
	default void emitTimestamp(long millis) { emit(millis); }
	default void emitTimestamp(long seconds, int nanos) {emitTimestamp(seconds * 1000 + nanos / 1_000_000);}

	default boolean supportArray() {return false;}
	default void emit(byte[] array) {
		emitList(array.length);
		for (byte b : array) emit(b);
		pop();
	}
	default void emit(int[] array) {
		emitList(array.length);
		for (int i : array) emit(i);
		pop();
	}
	default void emit(long[] array) {
		emitList(array.length);
		for (long l : array) emit(l);
		pop();
	}

	void emitMap();
	default void emitMap(int size) { emitMap(); }

	void key(String key);
	default void intKey(int key) {key(Integer.toString(key));}

	void emitList();
	default void emitList(int size) { emitList(); }

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

	ValueEmitter reset();
	default void close() throws IOException {}
}