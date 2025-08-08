package roj.compiler.jpp;

import roj.asm.attr.Attribute;
import roj.asm.cp.ConstantPool;
import roj.collect.IntList;
import roj.util.DynByteBuf;

/**
 * @author Roj234
 * @since 2025/3/31 4:57
 */
public class NativeStruct extends Attribute {
	public static final String NAME = "LavaNativeStruct";

	public IntList arraySize;

	public NativeStruct(IntList list) {

	}

	@Override
	public void toByteArrayNoHeader(DynByteBuf w, ConstantPool cp) {

	}

	@Override public final String name() {return NAME;}
	@Override public String toString() {return "LavaNativeStruct"+arraySize;}
}
