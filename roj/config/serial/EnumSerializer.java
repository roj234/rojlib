package roj.config.serial;

import roj.config.data.CEntry;
import roj.config.data.CString;
import roj.reflect.EnumHelper;

/**
 * @author Roj233
 * @since 2022/1/11 17:45
 */
class EnumSerializer implements Serializer<Enum<?>> {
	final Class<? extends Enum<?>> enumc;

	@SuppressWarnings("unchecked")
	EnumSerializer(Class<?> enumc) {
		if (!enumc.isEnum()) throw new ClassCastException();
		this.enumc = (Class<? extends Enum<?>>) enumc;
	}

	@Override
	public CEntry serializeRc(Enum<?> t) {
		return CString.valueOf(t.name());
	}

	@Override
	public Enum<?> deserializeRc(CEntry o) {
		return EnumHelper.cDirAcc.enumConstantDirectory(enumc).get(o.asString());
	}
}
