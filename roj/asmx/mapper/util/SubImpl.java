package roj.asmx.mapper.util;

import roj.asm.type.Desc;
import roj.collect.SimpleList;

import java.util.List;
import java.util.Set;

/**
 * @author Roj233
 * @since 2021/7/21 2:42
 */
public class SubImpl {
	public final List<Set<String>> owners = new SimpleList<>();
	public Desc type;

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