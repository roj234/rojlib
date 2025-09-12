package roj.config.mapper;

import org.jetbrains.annotations.Nullable;
import roj.asm.type.IType;
import roj.collect.BitSet;
import roj.config.ValueEmitter;

import java.util.List;

/**
 * @author Roj233
 * @since 2022/1/11 17:45
 */
final class EitherAdapter extends TypeAdapter {
	private TypeAdapter leftType, rightType;
	private byte lType, rType;

	EitherAdapter() {}
	public EitherAdapter(TypeAdapter left, TypeAdapter right) {
		lType = typeOf(left);
		rType = typeOf(right);
		if (lType == rType || left instanceof AnyAdapter || right instanceof AnyAdapter)
			throw new IllegalArgumentException("Either的泛型类型无效(映射，列表，基本类型三抽二)");
		this.leftType = left;
		this.rightType = right;
	}
	private static byte typeOf(TypeAdapter adapter) {
		if (adapter.valueIsMap()) return 2;//MAP
		if (adapter instanceof PrimitiveAdapter) return 1;//PRIMITIVE
		return 3;//LIST
	}

	@Override
	public TypeAdapter transform(Factory man, Class<?> subclass, @Nullable List<IType> generic) {
		if (generic == null || generic.size() != 2) throw new IllegalArgumentException("Either的泛型定义无效:"+generic);
		return new EitherAdapter(man.get(generic.get(0)), man.get(generic.get(1)));
	}

	@Override
	public void map(MappingContext ctx, int size) {
		TypeAdapter ser;
		if (lType == 2) {
			ctx.fieldId = 0;
			ctx.push(ser = leftType);
		} else if (rType == 2) {
			ctx.fieldId = 1;
			ctx.push(ser = rightType);
		} else {
			super.map(ctx, size);
			return;
		}
		ser.map(ctx, size);
	}

	@Override
	public void list(MappingContext ctx, int size) {
		TypeAdapter ser;
		if (lType == 3) {
			ctx.fieldId = 0;
			ctx.push(ser = leftType);
		} else if (rType == 3) {
			ctx.fieldId = 1;
			ctx.push(ser = rightType);
		} else {
			super.list(ctx, size);
			return;
		}
		ser.list(ctx, size);
	}

	@Override
	public void read(MappingContext ctx, Object o) {
		var either = new Either<>();

		if (ctx.fieldId == -2) {
			if (lType == 1) {
				either.setLeft(o);
			} else if (rType == 1) {
				either.setRight(o);
			} else {
				super.read(ctx, o);
			}
		} else {
			if (ctx.fieldId == 0) either.setLeft(o);
			else either.setRight(o);
		}

		ctx.setRef(either);
		ctx.popd(true);
	}

	// empty collection
	@Override
	public int plusOptional(int fieldState, @Nullable BitSet fieldStateEx) { return 1; }

	@Override
	public void write(ValueEmitter c, Object o) {
		var either = (Either<?, ?>) o;
		switch (either.getState()) {
			case 0 -> c.emitNull();
			case 1 -> leftType.write(c, either.asLeft());
			case 2 -> rightType.write(c, either.asRight());
		}
	}
}