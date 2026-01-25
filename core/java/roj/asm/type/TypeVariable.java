package roj.asm.type;

import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;
import roj.io.IOUtil;
import roj.text.CharList;
import roj.util.OperationDone;

import static roj.asm.type.ParameterizedType.*;

/**
 * @author Roj234
 * @since 2022/11/1 10:47
 */
public final class TypeVariable implements IType {
	public TypeVariableDeclaration decl;
	@MagicConstant(intValues = {NO_WILDCARD, SUPER_WILDCARD, EXTENDS_WILDCARD})
	public byte wildcard;
	private byte array;

	public TypeVariable(TypeVariableDeclaration decl) {
		this.decl = decl;
		if (decl.state == 1) decl.add(this);
	}
	public TypeVariable(TypeVariableDeclaration decl, int array, @MagicConstant(intValues = {NO_WILDCARD, SUPER_WILDCARD, EXTENDS_WILDCARD}) int wildcard) {
		this(decl);
		setArrayDim(array);
		this.wildcard = (byte) wildcard;
	}

	@NotNull public TypeVariableDeclaration getDeclaration() {return decl;}
	@NotNull public String name() {return decl.name;}

	@Override public byte kind() {return TYPE_VARIABLE;}

	@Override public void toDesc(CharList sb) {
		if (wildcard != NO_WILDCARD) sb.append(wildcard == SUPER_WILDCARD ? '-' : '+');
		for (int i = array & 0xFF; i > 0; i--) sb.append("[");
		sb.append('T').append(decl.name).append(';');
	}
	@Override public void toString(CharList sb) {
		switch (wildcard) {
			case SUPER_WILDCARD: sb.append("? super "); break;
			case EXTENDS_WILDCARD: sb.append("? extends "); break;
		}

		sb.append(decl.name);
		for (int i = array & 0xFF; i > 0; i--) sb.append("[]");
	}
	@Override public String toString() {
		CharList cl = IOUtil.getSharedCharBuf();
		toString(cl);
		return cl.toString();
	}

	//@Override public int getActualType() { return 'T'; }

	@Override public int array() { return array & 0xFF; }
	@Override public void setArrayDim(int array) {
		if (array > 255 || array < 0) throw new ArrayIndexOutOfBoundsException(array);
		this.array = (byte) array;
	}

	@Override public Type rawType() {return decl.getState() != 0 ? IType.super.rawType() : decl.get(0).rawType();}
	@Override public String owner() { return decl.getState() != 0 ? IType.super.owner() : decl.get(0).owner(); }

	@Override
	public int hashCode() {
		int result = decl.hashCode();
		result = 31 * result + wildcard;
		result = 31 * result + array;
		return result;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		TypeVariable param = (TypeVariable) o;

		if (wildcard != param.wildcard) return false;
		if (array != param.array) return false;
		return decl == param.decl;
	}

	@Override
	public TypeVariable clone() {
		try {
			return (TypeVariable) super.clone();
		} catch (CloneNotSupportedException e) {
			throw OperationDone.NEVER;
		}
	}
}