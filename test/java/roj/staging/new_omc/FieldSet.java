package roj.staging.new_omc;

import org.jetbrains.annotations.NotNull;
import roj.collect.LinkedOpenHashKVSet;

import java.util.*;

/**
 * @author Roj234
 * @since 2026/02/06 21:15
 */
public class FieldSet extends LinkedOpenHashKVSet<String, State.Field> implements List<State.Field> {
	@Override
	protected String getKey(State.Field value) {
		return value.name;
	}

	@Override
	public boolean addAll(int index, @NotNull Collection<? extends State.Field> c) {
		throw new UnsupportedOperationException();
	}

	@Override
	public State.Field get(int index) {
		return getItems().get(index);
	}

	@Override
	public State.Field set(int index, State.Field element) {
		return getItems().set(index, element);
	}

	@Override
	public void add(int index, State.Field element) {
		throw new UnsupportedOperationException();
	}

	@Override
	public State.Field remove(int index) {
		return removeKey(getKey(getItems().get(index)));
	}

	@Override
	public int lastIndexOf(Object o) {
		return indexOf(o);
	}

	@NotNull
	@Override
	public ListIterator<State.Field> listIterator() {
		return null;
	}

	@NotNull
	@Override
	public ListIterator<State.Field> listIterator(int index) {
		return null;
	}

	@Override
	public Spliterator<State.Field> spliterator() {
		return super.spliterator();
	}

	@NotNull
	@Override
	public List<State.Field> subList(int fromIndex, int toIndex) {
		return Collections.unmodifiableList(getItems().subList(fromIndex, toIndex));
	}
}
