package roj.compiler.api;

import roj.asm.attr.Attribute;
import roj.asm.cp.ConstantPool;
import roj.collect.IntMap;
import roj.util.DynByteBuf;
import roj.util.TypedKey;

/**
 * @author Roj234
 * @since 2024/6/5 20:00
 */
public class MethodDefault extends Attribute {
	public static final TypedKey<MethodDefault> METHOD_DEFAULT = new TypedKey<>("MethodDefault");

	@Override
	public final boolean isEmpty() {return defaultValue.isEmpty();}

	@Override
	public String toString() {return name()+defaultValue;}

	@Override
	public final String name() {return "MethodDefault";}

	@Override
	public void toByteArrayNoHeader(DynByteBuf w, ConstantPool cp) {
		w.put(defaultValue.size());
		for (IntMap.Entry<String> entry : defaultValue.selfEntrySet()) {
			w.put(entry.getIntKey()).putVUIGB(entry.getValue());
		}
	}

	public IntMap<String> defaultValue = new IntMap<>();
}