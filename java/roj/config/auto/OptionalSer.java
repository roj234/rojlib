package roj.config.auto;

import org.jetbrains.annotations.Nullable;
import roj.asm.type.IType;
import roj.collect.MyBitSet;
import roj.config.serial.CVisitor;

import java.util.List;
import java.util.Optional;

/**
 * @author Roj233
 * @since 2022/1/11 17:45
 */
final class OptionalSer extends Adapter {
	private Adapter contentType;

	OptionalSer() {}
	public OptionalSer(Adapter left) {contentType = left;}

	@Override
	public Adapter transform(SerializerFactoryImpl man, Class<?> subclass, @Nullable List<IType> generic) {
		if (generic == null || generic.size() != 1) throw new IllegalArgumentException("Optional的泛型定义无效:"+generic);
		return new OptionalSer(man.get(generic.get(0)));
	}

	@Override public void push(AdaptContext ctx) {ctx.setRef(Optional.empty());}

	@Override
	public void map(AdaptContext ctx, int size) {
		ctx.fieldId = 0;
		ctx.push(contentType);
		contentType.map(ctx, size);
	}

	@Override
	public void list(AdaptContext ctx, int size) {
		ctx.fieldId = 0;
		ctx.push(contentType);
		contentType.list(ctx, size);
	}

	@Override
	public void read(AdaptContext ctx, Object o) {
		if (ctx.fieldId == -2) {
			ctx.fieldId = 0;
			ctx.push(contentType);
			contentType.read(ctx, o);
			return;
		}

		ctx.setRef(Optional.ofNullable(o));
		ctx.popd(true);
	}

	// empty collection
	@Override
	public int plusOptional(int fieldState, @Nullable MyBitSet fieldStateEx) { return 1; }

	@Override
	public void write(CVisitor c, Object o) {
		var optional = (Optional<?>) o;
		contentType.write(c, optional.get());
	}
}