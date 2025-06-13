package roj.config.auto;

import org.jetbrains.annotations.Nullable;
import roj.asm.type.IType;
import roj.collect.BitSet;
import roj.collect.HashSet;
import roj.collect.ArrayList;
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
	public Adapter transform(SerializerFactoryImpl man, Class<?> subclass, List<IType> generic) {
		var vType = valueType;
		if (generic != null) {
			if (generic.size() != 1) throw new IllegalArgumentException(generic.toString());
			vType = man.get(generic.get(0));
		}

		IntFunction<Collection<?>> constructor = SerializerFactory.dataContainer(subclass);
		if (constructor == null) constructor = newCollection;

		return vType == valueType && constructor == newCollection ? this : new CollectionSer(vType, set, constructor);
	}

	@Override
	public void list(AdaptContext ctx, int size) {
		ctx.fieldId = -1;
		ctx.setRef(newCollection != null ? newCollection.apply(size) :
			set ? size < 0 ? new HashSet<>() : new HashSet<>(size)
			: size < 0 ? ArrayList.hugeCapacity(0) : new ArrayList<>(size));
		ctx.push(valueType);
	}

	@Override
	public void read(AdaptContext ctx, Object o) {
		Collection<?> ref = (Collection<?>) ctx.ref;
		if (ref == null) {
			if (o == null) {
				ctx.popd(true);
				return;
			}
			throw new IllegalStateException(o+"不是集合对象:"+o.getClass().getName());
		}
		ref.add(Helpers.cast(o));

		ctx.fieldState = 1;
		ctx.push(valueType);
	}

	// empty collection
	@Override
	public int plusOptional(int fieldState, @Nullable BitSet fieldStateEx) { return 1; }

	@Override
	public void write(CVisitor c, Object o) {
		if (valueType == null) throw new IllegalStateException("开启Dynamic模式以序列化任意对象");

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