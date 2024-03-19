package roj.config.auto;

import org.jetbrains.annotations.Nullable;
import roj.asm.type.IType;
import roj.collect.MyBitSet;
import roj.collect.MyHashSet;
import roj.collect.SimpleList;
import roj.config.serial.CVisitor;
import roj.util.Helpers;

import java.util.Collection;
import java.util.List;
import java.util.function.IntFunction;

/**
 * @author Roj233
 * @since 2022/1/11 17:45
 */
final class CollectionSer extends Adapter {
	private final Adapter valueType;
	private final boolean set;
	private final IntFunction<Collection<?>> newCollection;

	CollectionSer(Adapter type, boolean set, IntFunction<Collection<?>> newCollection) {
		this.valueType = type;
		this.set = set;
		this.newCollection = newCollection;
	}

	@Override
	Adapter inheritBy(SerializerFactoryImpl factory, Class<?> type) {
		IntFunction<Collection<?>> subType = SerializerFactory.dataContainer(type);
		return subType == null ? this : new CollectionSer(valueType, set, subType);
	}

	@Override
	Adapter withGenericType(SerializerFactoryImpl man, List<IType> genericType) {
		if (genericType.size() != 1) throw new IllegalArgumentException(genericType.toString());
		return new CollectionSer(man.get(genericType.get(0)), set, newCollection);
	}

	@Override
	void list(AdaptContext ctx, int size) {
		ctx.fieldId = -1;
		ctx.ref = newCollection != null ? newCollection.apply(size) :
				size < 0 ?
						set ? new MyHashSet<>() : SimpleList.withCapacityType(0,2) :
						set ? new MyHashSet<>(size) : new SimpleList<>(size);
		ctx.push(valueType);
	}

	@Override
	void read(AdaptContext ctx, Object o) {
		Collection<?> ref = (Collection<?>) ctx.ref;
		if (ref == null) {
			if (o == null) {
				ctx.popd(true);
				return;
			}
			throw new IllegalStateException("ILS:"+o);
		}
		ref.add(Helpers.cast(o));

		ctx.fieldState = 1;
		ctx.push(valueType);
	}

	// empty collection
	@Override
	int plusOptional(int fieldState, @Nullable MyBitSet fieldStateEx) { return 1; }

	@Override
	void write(CVisitor c, Object o) {
		Collection<?> ref = (Collection<?>) o;
		if (o == null) c.valueNull();
		else {
			c.valueList(ref.size());
			for (Object o1 : ref) {
				valueType.write(c, o1);
			}
			c.pop();
		}
	}
}