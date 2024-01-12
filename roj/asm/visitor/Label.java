package roj.asm.visitor;

import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

/**
 * @author Roj234
 * @since 2022/11/17 0017 12:53
 */
public final class Label extends Number implements Comparable<Label>, ReadonlyLabel {
	short block;
	char offset;
	char value;

	public Label() { clear(); }
	public Label(int raw) { setRaw(raw); }
	public Label(ReadonlyLabel label) { set(label); }

	public final void set(ReadonlyLabel label) {
		block = (short) label.getBlock();
		offset = (char) label.getOffset();
		value = (char) label.getValue();
	}

	final void dispose() { block = -3; }
	final void setFirst(int off) {
		block = 0;
		value = offset = (char) off;
	}
	public final void setRaw(int off) {
		block = (short) (off==0?0:-1);
		offset = (char) off;
		value = 0;
	}
	public final void clear() {
		block = -2;
		value = offset = 0;
	}

	public int intValue() { return value; }
	public long longValue() { return value; }
	public float floatValue() { return value; }
	public double doubleValue() { return value; }

	public boolean isValid() { return block >= 0; }
	public int getBlock() { return block; }
	public int getOffset() { return block < 0 ? -1 : offset; }
	public int getValue() { return block < -1 ? -1 : value; }

	private static int findBlock(int[] lengths, int val, int len) {
		int i = Arrays.binarySearch(lengths, 0, len, val);
		if (i > 0) return i;
		else if (i == 0) throw new IllegalArgumentException();
		else return -i - 2;
	}

	boolean update(int[] sum, int len) {
		int pos = value;
		if (block < 0) {
			if (block != -1) throw new IllegalStateException("label: "+this);

			block = (short) findBlock(sum, offset, len);
			if (block >= len) throw new IllegalStateException("Offset "+offset+" exceeded bytecode boundary("+sum[len-1]+")");

			offset -= sum[block];
			pos = -1;
		}

		int off = value = (char) (offset + sum[block]);
		return pos != off;
	}

	@Override
	public String toString() {
		switch (block) {
			case -3: return "<disposed>";
			case -2: return "<unset>";
			case -1: return (int)offset+" // <raw>";
			default: return (int)value+" // [b"+block+" + "+(int)offset+"]";
		}
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		Label label = (Label) o;

		assert isValid();
		if (block != label.block) return false;
		return offset == label.offset;
	}

	@Override
	public int hashCode() {
		assert isValid();
		return (block << 16) ^ offset;
	}

	@Override
	public int compareTo(@NotNull Label o) {
		if (!isValid() || !o.isValid()) throw new IllegalArgumentException(this+"|"+o);

		if (block != o.block) return block > o.block ? 1 : -1;
		if (offset != o.offset) return offset > o.offset ? 1 : -1;
		return 0;
	}
}