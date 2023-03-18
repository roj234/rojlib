package roj.config.serial;

import roj.asm.type.IType;
import roj.collect.MyHashSet;
import roj.collect.SimpleList;
import roj.util.Helpers;

import java.util.Collection;
import java.util.List;

/**
 * @author Roj233
 * @since 2022/1/11 17:45
 */
final class ListSer extends Adapter {
	final Adapter targetSer;
	final boolean set;

	ListSer(Adapter type, boolean set) {
		this.targetSer = type;
		this.set = set;
	}

	@Override
	Adapter withGenericType(SerializerManager man, List<IType> genericType) {
		if (genericType.size() != 1) throw new IllegalArgumentException(genericType.toString());
		Adapter genSer = man.get(genericType.get(0));
		return genSer == null ? this : new ListSer(genSer, set);
	}

	@Override
	void list(AdaptContext ctx, int size) {
		ctx.fieldId = -1;
		ctx.ref = size < 0 ? set ? new MyHashSet<>() : new SimpleList<>(0,2) : set ? new MyHashSet<>(size) : new SimpleList<>(size);
		ctx.push(targetSer);
	}

	@Override
	void read(AdaptContext ctx, Object o) {
		((Collection<?>) ctx.ref).add(Helpers.cast(o));

		ctx.fieldState = 1;
		ctx.push(targetSer);
	}

	@Override
	void write(CVisitor c, Object o) {
		Collection<?> ref = (Collection<?>) o;
		if (o == null) c.valueNull();
		else {
			c.valueList(ref.size());
			for (Object o1 : ref) {
				targetSer.write(c, o1);
			}
			c.pop();
		}
	}
}
