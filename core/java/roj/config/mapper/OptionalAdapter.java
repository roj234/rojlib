package roj.config.mapper;

import org.jetbrains.annotations.Nullable;
import roj.asm.type.IType;
import roj.collect.BitSet;
import roj.config.ValueEmitter;

import java.util.List;
import java.util.Optional;

/**
 * @author Roj233
 * @since 2022/1/11 17:45
 */
final class OptionalAdapter extends TypeAdapter {
	private TypeAdapter contentType;

	OptionalAdapter() {}
	public OptionalAdapter(TypeAdapter left) {contentType = left;}

	@Override
	public TypeAdapter transform(Factory man, Class<?> subclass, @Nullable List<IType> generic) {
		if (generic == null || generic.size() != 1) throw new IllegalArgumentException("Optional的泛型定义无效:"+generic);
		return new OptionalAdapter(man.get(generic.get(0)));
	}

	@Override public void push(MappingContext ctx) {ctx.setRef(Optional.empty());}

	@Override
	public void map(MappingContext ctx, int size) {
		ctx.fieldId = 0;
		ctx.push(contentType);
		contentType.map(ctx, size);
	}

	@Override
	public void list(MappingContext ctx, int size) {
		ctx.fieldId = 0;
		ctx.push(contentType);
		contentType.list(ctx, size);
	}

	@Override
	public void read(MappingContext ctx, Object o) {
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
	public int plusOptional(int fieldState, @Nullable BitSet fieldStateEx) { return 1; }

	@Override
	public void write(ValueEmitter c, Object o) {
		var optional = (Optional<?>) o;
		contentType.write(c, optional.get());
	}
}