package roj.asm.type;

import roj.io.IOUtil;
import roj.text.CharList;
import roj.util.Helpers;

import static roj.asm.type.Generic.EX_EXTENDS;
import static roj.asm.type.Generic.EX_SUPER;

/**
 * @author Roj234
 * @since 2022/11/1 0001 10:47
 */
public final class TypeParam implements IType {
	public String name;

	public byte extendType;
	private byte array;

	public TypeParam(String name) {
		this.name = name;
	}

	public TypeParam(String name, int array, int extendType) {
		this.name = name;
		setArrayDim(array);
		this.extendType = (byte) extendType;
	}

	@Override
	public int array() {
		return array&0xFF;
	}

	@Override
	public void setArrayDim(int array) {
		if (array > 255 || array < 0)
			throw new ArrayIndexOutOfBoundsException(array);
		this.array = (byte) array;
	}

	@Override
	public byte genericType() {
		return TYPE_PARAMETER_TYPE;
	}

	@Override
	public String owner() {
		return "/Type parameter '"  + name + "'/";
	}

	@Override
	public TypeParam clone() {
		try {
			return (TypeParam) super.clone();
		} catch (CloneNotSupportedException e) {
			Helpers.athrow(e);
			return Helpers.nonnull();
		}
	}

	@Override
	public void toDesc(CharList sb) {
		if (extendType != 0) sb.append(extendType == EX_SUPER ? '-' : '+');
		for (int i = array & 0xFF; i > 0; i--) sb.append("[");
		sb.append('T').append(name).append(';');
	}

	@Override
	public void toString(CharList sb) {
		switch (extendType) {
			case EX_SUPER: sb.append("? super "); break;
			case EX_EXTENDS: sb.append("? extends "); break;
		}

		sb.append(name);
		for (int i = array & 0xFF; i > 0; i--) sb.append("[]");
	}

	@Override
	public void checkPosition(int env, int pos) {}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		TypeParam param = (TypeParam) o;

		if (extendType != param.extendType) return false;
		if (array != param.array) return false;
		return name.equals(param.name);
	}

	@Override
	public int hashCode() {
		int result = name.hashCode();
		result = 31 * result + extendType;
		result = 31 * result + array;
		return result;
	}

	@Override
	public String toString() {
		CharList cl = IOUtil.getSharedCharBuf();
		toString(cl);
		return cl.toString();
	}
}
