package roj.config.auto;

import org.jetbrains.annotations.Nullable;
import roj.asm.type.IType;
import roj.collect.MyBitSet;
import roj.config.serial.CVisitor;

import java.util.List;

/**
 * @author Roj233
 * @since 2022/1/11 17:45
 */
final class EitherSer extends Adapter {
	private Adapter leftType, rightType;
	private byte lType, rType;

	EitherSer() {}
	public EitherSer(Adapter left, Adapter right) {
		lType = typeOf(left);
		rType = typeOf(right);
		if (lType == rType || left instanceof ObjAny || right instanceof ObjAny)
			throw new IllegalArgumentException("Either的泛型类型无效(映射，列表，基本类型三抽二)");
		this.leftType = left;
		this.rightType = right;
	}
	private static byte typeOf(Adapter adapter) {
		if (adapter.valueIsMap()) return 2;//MAP
		if (adapter instanceof PrimObj) return 1;//PRIMITIVE
		return 3;//LIST
	}

	@Override
	public Adapter transform(SerializerFactoryImpl man, Class<?> subclass, @Nullable List<IType> generic) {
		if (generic == null || generic.size() != 2) throw new IllegalArgumentException("Either的泛型定义无效:"+generic);
		return new EitherSer(man.get(generic.get(0)), man.get(generic.get(1)));
	}

	@Override
	public void map(AdaptContext ctx, int size) {
		Adapter ser;
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
	public void list(AdaptContext ctx, int size) {
		Adapter ser;
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
	public void read(AdaptContext ctx, Object o) {
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
	public int plusOptional(int fieldState, @Nullable MyBitSet fieldStateEx) { return 1; }

	@Override
	public void write(CVisitor c, Object o) {
		var either = (Either<?, ?>) o;
		switch (either.getState()) {
			case 0 -> c.valueNull();
			case 1 -> leftType.write(c, either.asLeft());
			case 2 -> rightType.write(c, either.asRight());
		}
	}
}