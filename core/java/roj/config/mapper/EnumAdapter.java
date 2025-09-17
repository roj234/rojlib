package roj.config.mapper;

import roj.config.ValueEmitter;
import roj.reflect.Reflection;
import roj.util.Helpers;

/**
 * @author Roj233
 * @since 2022/1/11 17:45
 */
final class EnumAdapter extends TypeAdapter {
	final Class<? extends Enum<?>> enumType;

	@SuppressWarnings("unchecked")
	EnumAdapter(Class<?> enumType) {
		if (!enumType.isEnum()) throw new ClassCastException();
		this.enumType = (Class<? extends Enum<?>>) enumType;
	}

	@Override
	public void read(MappingContext ctx, Object o) {
		if (o != null && o.getClass() != String.class) throw new IllegalStateException();
		ctx.setRef(o == null ? null : Reflection.enumConstantDirectory(Helpers.cast(enumType)).get(o));
		ctx.fieldState = 1;
		ctx.fieldId = -1;
		ctx.pop();
	}

	@Override
	public void write(ValueEmitter c, Object o) {
		if (o == null) c.emitNull();
		else c.emit(((Enum<?>) o).name());
	}
}