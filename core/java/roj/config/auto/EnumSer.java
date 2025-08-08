package roj.config.auto;

import roj.config.serial.CVisitor;
import roj.reflect.EnumHelper;
import roj.util.Helpers;

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
	public void read(AdaptContext ctx, Object o) {
		if (o != null && o.getClass() != String.class) throw new IllegalStateException();
		ctx.setRef(o == null ? null : EnumHelper.CONSTANTS.enumConstantDirectory(Helpers.cast(enumc)).get(o));
		ctx.fieldState = 1;
		ctx.fieldId = -1;
		ctx.pop();
	}

	@Override
	public void write(CVisitor c, Object o) {
		if (o == null) c.valueNull();
		else c.value(((Enum<?>) o).name());
	}
}