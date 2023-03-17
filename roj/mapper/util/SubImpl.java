package roj.mapper.util;

import roj.collect.MyHashSet;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author Roj233
 * @since 2021/7/21 2:42
 */
public class SubImpl {
	public final MyHashSet<String> owners = new MyHashSet<>();
	public Desc type;
	public boolean immutable;

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		SubImpl sub = (SubImpl) o;

		return type.equals(sub.type);
	}

	@Override
	public int hashCode() {
		return type.hashCode();
	}

	@Override
	public String toString() {
		return owners + ": " + type;
	}
}
