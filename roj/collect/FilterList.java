package roj.collect;


import java.util.AbstractList;
import java.util.function.BiFunction;

/**
 * @author Roj234
 * @since 2020/8/23 0:44
 */
public class FilterList<T> extends AbstractList<T> {
	public T found;
	final BiFunction<T, T, Boolean> checker;

	public FilterList(BiFunction<T, T, Boolean> checker) {
		this.checker = checker;
	}

	public void add(int index, T element) {
		if (checker.apply(found, element)) {
			found = element;
		}
	}

	@Override
	public T get(int index) {
		if (index > size()) throw new IndexOutOfBoundsException(String.valueOf(index));
		return found;
	}

	@Override
	public int size() {
		return found == null ? 0 : 1;
	}
}