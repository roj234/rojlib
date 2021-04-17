package roj.asm.visitor;

import roj.collect.IntList;
import roj.math.MutableInt;

import java.util.Arrays;

/**
 * @author Roj234
 * @since 2022/11/17 0017 12:53
 */
public final class Label extends MutableInt {
	int block, offset;
	Throwable trace = new Throwable();

	public Label() { clear(); }

	public void clear() {
		value = -1;
		block = -2;
		offset = -1;
	}

	public void set(Label label) {
		value = label.value;
		block = label.block;
		offset = label.offset;
	}

	@Override
	public int getValue() {
		int v = super.getValue();
		if (v < 0) throw new IllegalArgumentException("Not ready for serialize/"+this, trace);
		return v;
	}
	public int getOptionalValue() {
		return super.getValue();
	}

	int findPos(IntList sum, int offset) {
		int i = Arrays.binarySearch(sum.getRawArray(), 0, sum.size(), offset);
		if (i > 0) return i;
		else if (i == 0) throw new IllegalArgumentException();
		else return -(i+1) -1;
	}

	boolean update(IntList sum) {
		boolean changed = false;
		if (block < 0) {
			block = findPos(sum, offset);
			if (block >= sum.size()) {
				throw new IllegalStateException("Offset "+offset+" exceeded bytecode boundary("+sum.get(sum.size()-1)+")");
			}

			offset -= sum.get(block);
			changed = true;
		}

		int pos = super.getValue();
		int off = offset + sum.get(block);
		setValue(off);
		return changed || pos != off;
	}

	void _first(int off) {
		block = 0;
		setValue(offset = off);
	}

	@Override
	public String toString() {
		return "([b" + block + " + " + offset + "] => " + super.getValue() + ')';
	}
}
