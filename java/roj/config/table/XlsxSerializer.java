package roj.config.table;

import roj.collect.ToIntMap;
import roj.config.serial.CVisitor;
import roj.util.Helpers;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * @author Roj234
 * @since 2024/11/8 0008 18:54
 */
final class XlsxSerializer implements CVisitor {
	private final ToIntMap<String> columnIndex;
	private final List<Object> dataBuffer;
	private final XlsxWriter writer;
	private final byte initialState;
	private int state;
	private static final int EXCEPTING_LIST = -3, EXCEPTING_MAP = -2, EXCEPTING_KEY = -1;

	public XlsxSerializer(ToIntMap<String> index, int colCount, XlsxWriter writer) {
		assert colCount >= index.size();
		columnIndex = index;
		dataBuffer = Arrays.asList(new Object[colCount]);
		this.writer = writer;
		this.initialState = EXCEPTING_LIST;
	}

	@Override public void key(String key) {
		if (state == EXCEPTING_KEY) {
			state = columnIndex.getOrDefault(key, EXCEPTING_KEY);
			if (state == EXCEPTING_KEY) throw new IllegalStateException("unknown key "+key);
		} else throw new IllegalStateException("state="+state);
	}

	@Override public void value(boolean l) {doSet(l ? "true" : "false");}
	@Override public void value(int l) {doSet(l);}
	@Override public void value(long l) {doSet(l);}
	@Override public void value(double l) {doSet(l);}
	@Override public void value(String l) {doSet(l);}
	@Override public void valueNull() {doSet(null);}
	private void doSet(Object o) {
		dataBuffer.set(state, o);
		state = EXCEPTING_KEY;
	}

	@Override public void valueMap() {
		if (state == EXCEPTING_MAP) state = EXCEPTING_KEY;
		else throw new IllegalStateException("state="+state);
	}
	@Override public void valueList() {
		if (state == EXCEPTING_LIST) state = EXCEPTING_MAP;
		else throw new IllegalStateException("state="+state);
	}

	@Override public void pop() {
		if (state == EXCEPTING_KEY) {
			try {
				writer.writeRow(dataBuffer);
			} catch (IOException e) {
				Helpers.athrow(e);
			}
			state = EXCEPTING_MAP;
		} else if (state == EXCEPTING_MAP) {
			state = EXCEPTING_LIST;
		} else throw new IllegalStateException("state="+state);
	}

	@Override public CVisitor reset() {state = initialState;return this;}
}
