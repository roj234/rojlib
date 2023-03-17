package roj.kscript.api;

import roj.kscript.type.KType;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2020/10/27 22:51
 */
public interface IArray extends Iterable<KType>, KType, IObject {
	//int size();

	//@Nonnull
	//Iterator<KType> iterator();

	void add(@Nullable KType entry);

	void set(int index, @Nullable KType entry);

	@Nonnull
	KType get(int index);

	@Nonnull
	default IArray asArray() {
		return this;
	}

	void addAll(IArray list);

	default List<KType> getInternal() {
		throw new UnsupportedOperationException();
	}

	void clear();
}
