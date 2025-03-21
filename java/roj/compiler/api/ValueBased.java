package roj.compiler.api;

import roj.asm.attr.Attribute;
import roj.asm.type.Type;

/**
 * PseudoType exact
 * @author Roj234
 * @since 2024/12/6 0015 1:14
 */
public final class ValueBased extends Attribute {
	public static final String NAME = "LavaValueBased";

	public final Type exactType;

	public ValueBased(Type type) {exactType = type;}

	@Override public final boolean writeIgnore() {return true;}
	@Override public final String name() {return NAME;}
	@Override public String toString() {return name()+" "+exactType;}
}