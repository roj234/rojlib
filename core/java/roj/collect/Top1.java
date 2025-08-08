package roj.collect;


import java.util.AbstractList;
import java.util.Set;
import java.util.Spliterator;
import java.util.function.BiFunction;

/**
 * 一个特殊的集合，仅保留通过特定规则选出的“最佳”元素。
 * 继承自AbstractList并实现Set接口，但实际行为与标准集合不同：它仅维护一个最佳元素，
 * 添加新元素时会通过提供的比较函数更新最佳元素，且最多只包含一个元素。
 *
 * @param <E> 集合中元素的类型
 * @author Roj234
 * @since 2020/8/23 0:44
 */
public final class Top1<E> extends AbstractList<E> implements Set<E> {
	public E best;

	private final BiFunction<E, E, E> reducer;
	public Top1(BiFunction<E, E, E> reducer) {this.reducer = reducer;}

	@Override public void add(int index, E element) {best = reducer.apply(best, element);}

	@Override public Spliterator<E> spliterator() {return super.spliterator();}

	@Override public E get(int index) {
		if (index >= size()) throw new ArrayIndexOutOfBoundsException(index);
		return best;
	}

	@Override public int size() {return best == null ? 0 : 1;}
}