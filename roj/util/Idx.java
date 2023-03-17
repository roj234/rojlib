package roj.util;

import roj.collect.IntIterator;
import roj.collect.MyBitSet;

/**
 * Idx 槽位筛选器
 *
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public final class Idx {
	private final MyBitSet list;
	private short size, len;
	private byte str = 0;

	public Idx(int length) {
		list = new MyBitSet(length);
		list.fill(length);
		len = (short) length;
	}

	public Idx(int offset, int length) {
		this(length);
		str = (byte) offset;
	}

	public void reset(int length) {
		list.fill(length);
		len = (short) length;
		size = 0;
	}

	public void add(int id) {
		list.remove(id - str);
		size++;
	}

	public boolean contains(int id) {
		return !list.contains(id - str);
	}

	public boolean isFull() {
		return size == len;
	}

	public IntIterator remains() {
		assert str == 0;
		return list.iterator();
	}

	@Override
	public String toString() {
		return "Idx{" + list + ", remain=" + (len - size) + ", off=" + str + '}';
	}

	public int size() {
		return size;
	}
}