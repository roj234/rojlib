package roj.mod;

import java.util.List;

/**
 * @author Roj234
 * @since 2024/1/3 0003 21:13
 */
final class ATDesc {
	String clazz;
	List<String> value;
	boolean compile;

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		ATDesc entry = (ATDesc) o;

		if (compile != entry.compile) return false;
		if (!clazz.equals(entry.clazz)) return false;
		return value.equals(entry.value);
	}

	@Override
	public int hashCode() {
		int result = clazz.hashCode();
		result = 31 * result + value.hashCode();
		result = 31 * result + (compile ? 1 : 0);
		return result;
	}
}