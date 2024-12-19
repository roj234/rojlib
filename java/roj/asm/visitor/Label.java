package roj.asm.visitor;

import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

/**
 * @author Roj234
 * @since 2022/11/17 0017 12:53
 */
public class Label implements Comparable<Label>, ReadonlyLabel {
	public static final short LABEL_UNSET = -2;

	protected short block;
	protected char offset;
	char value;

	public static Label atZero() {return new Label(0);}
	public Label() { block = -1; }
	public Label(int raw) { setRaw(raw); }
	public Label(ReadonlyLabel label) { set(label); }

	public final void set(ReadonlyLabel label) {
		block = (short) label.getBlock();
		offset = (char) label.getOffset();
		value = (char) label.getValue();
	}

	final boolean isUnset() {return block == -1 && offset == 0;}
	final boolean isRaw() {return block == -1 && offset != 0;}
	public final void clear() {
		block = -1;
		value = offset = 0;
	}

	// first segment
	final void setFirst(int off) {
		block = 0;
		value = offset = (char) off;
	}
	// negative relative offset (in _rel)
	final void setRaw(int off) {
		assert off > 0;
		block = -1;
		offset = (char) off;
		value = 0;
	}

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
			if (isUnset()) throw new IllegalStateException("无法序列化未初始化的标签");

			block = (short) findBlock(sum, offset, len);
			if (block >= len) throw new IllegalStateException("标签偏移量 "+(int)offset+" 超过bytecode长度 "+sum[len-1]);

			offset -= sum[block];
			pos = -1;
		} else {
			int blockSize = sum[block+1] - sum[block];
			if (offset > blockSize) {
				//FIXME 谁动了我的length？
				block++;
				offset = 0;
			}
		}

		int off = value = (char) (offset + sum[block]);
		return pos != off;
	}

	@Override
	public String toString() {
		if (isUnset()) return "<uninitialized>";
		if (block == -1) return "<unresolved "+(int)offset+">";
		return "<"+(int)value + " in [b"+block+" + "+(int)offset+"]>";
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