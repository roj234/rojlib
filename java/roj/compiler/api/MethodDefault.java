package roj.compiler.api;

import roj.asm.cp.ConstantPool;
import roj.asm.tree.attr.Attribute;
import roj.collect.IntMap;
import roj.util.AttributeKey;
import roj.util.DynByteBuf;

/**
 * @author Roj234
 * @since 2024/6/5 20:00
 */
public class MethodDefault extends Attribute {
	public static final AttributeKey<MethodDefault> METHOD_DEFAULT = new AttributeKey<>("MethodDefault");

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