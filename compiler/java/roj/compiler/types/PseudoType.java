package roj.compiler.types;

import roj.asm.type.Type;
import roj.text.CharList;

/**
 * @author Roj234
 * @since 2024/11/30 13:30
 */
@Deprecated
public class PseudoType extends Type {
	public PseudoType(int type, String owner) {
		super((char) type);
		this.owner = owner;
	}

	@Override public byte kind() {return IDENTITY_TYPE;}
	@Override public boolean isPrimitive() {return false;}
	//@Override public int getActualType() {return type;}
	//@Override public Type rawType() {return std(type);}

	@Override
	public void toString(CharList sb) {
		super.toString(sb);
		if (type != OBJECT) sb.append("<alias of ").append(getName(type)).append(">");
	}
}
