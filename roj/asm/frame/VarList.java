package roj.asm.frame;

import roj.util.ArrayUtil;

import java.util.Arrays;

/**
 * @author Roj234
 * @since 2021/6/2 23:28
 */
public final class VarList {
	static final Var2[] EMPTY = new Var2[0];

	public Var2[] list = EMPTY;
	public int length = 0;

	public VarList() {}

	public VarList(Var2... array) {
		list = array;
		length = array.length;
		for (int i = 0; i < array.length; i++) {
			if (array[i] == null) array[i] = Var2.TOP;
		}
	}

	public VarList copyFrom(VarList other) {
		if (this != other) {
			this.length = 0;
			ensureCapacity(other.length + 4);
			this.length = other.length;
			System.arraycopy(other.list, 0, this.list, 0, other.length);
		}
		return this;
	}

	public void ensureCapacity(int size) {
		if (list.length >= size) return;
		Var2[] newList = new Var2[size];
		if (this.length > 0) System.arraycopy(list, 0, newList, 0, length);
		list = newList;
	}

	public void add(Var2 e) {
		if (e == null) throw new IllegalArgumentException();
		if (length >= list.length) ensureCapacity(list.length + 4);

		list[length++] = e;
	}

	public void set(int index, Var2 e) {
		if (e == null) throw new IllegalArgumentException();
		if (index >= list.length) ensureCapacity(index + 4);

		list[index] = e;

		if (length < index + 1) {
			length = index + 1;
		}
	}

	public void pop(int index) {
		if (length < index) throw new IllegalArgumentException("Size will < 0 after pop.");
		length -= index;
	}

	public void removeTo(int index) {
		if (index < 0) throw new IllegalArgumentException("Size will < 0 after pop.");
		if (length <= index) return;

		ensureCapacity(index);
		length = index;
	}

	public Var2 get(int index) {
		Var2 v = list[index];
		if (v == null) throw new IllegalArgumentException("Var[" + index + "] is not registered");
		return v;
	}

	public Var2[] toArray() {
		return Arrays.copyOf(list, length);
	}

	public String toString() {
		return ArrayUtil.toString(list, 0, length);
	}

	public void clear() {
		length = 0;
	}
}