package roj.config.serial;

import roj.reflect.EnumHelper;

/**
 * @author Roj233
 * @since 2022/1/11 17:45
 */
final class EnumSer extends Adapter {
	final Class<? extends Enum<?>> enumc;

	@SuppressWarnings("unchecked")
	EnumSer(Class<?> enumc) {
		if (!enumc.isEnum()) throw new ClassCastException();
		this.enumc = (Class<? extends Enum<?>>) enumc;
	}

	@Override
	void read(AdaptContext ctx, Object o) {
		if (o != null && o.getClass() != String.class) throw new IllegalStateException();
		ctx.ref = o == null ? null : EnumHelper.cDirAcc.enumConstantDirectory(enumc).get(o);
		ctx.fieldState = 1;
		ctx.fieldId = -1;
		ctx.pop();
	}

	@Override
	void write(CVisitor c, Object o) {
		if (o == null) c.valueNull();
		else c.value(((Enum<?>) o).name());
	}
}
