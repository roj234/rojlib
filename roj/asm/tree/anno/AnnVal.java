package roj.asm.tree.anno;

import roj.asm.cst.*;
import roj.asm.type.Type;
import roj.util.DynByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Roj234
 * @since 2021/5/29 17:16
 */
public abstract class AnnVal {
	// same as Type.X
	public static final char BYTE = 'B';
	public static final char CHAR = 'C';
	public static final char DOUBLE = 'D';
	public static final char FLOAT = 'F';
	public static final char INT = 'I';
	public static final char LONG = 'J';
	public static final char SHORT = 'S';
	public static final char BOOLEAN = 'Z';

	public static final char STRING = 's';
	public static final char ENUM = 'e';
	public static final char CLASS = 'c';
	public static final char ANNOTATION = '@';
	public static final char ARRAY = '[';

	public int asInt() {
		throw new UnsupportedOperationException(getClass().getSimpleName() + " is not INT");
	}
	public float asFloat() {
		throw new UnsupportedOperationException(getClass().getSimpleName() + " is not FLOAT");
	}
	public double asDouble() {
		throw new UnsupportedOperationException(getClass().getSimpleName() + " is not DOUBLE");
	}
	public long asLong() {
		throw new UnsupportedOperationException(getClass().getSimpleName() + " is not LONG");
	}
	public String asString() {
		throw new UnsupportedOperationException(getClass().getSimpleName() + " is not STRING");
	}
	public AnnValEnum asEnum() {
		throw new UnsupportedOperationException(getClass().getSimpleName() + " is not ENUM");
	}
	public Type asClass() {
		throw new UnsupportedOperationException(getClass().getSimpleName() + " is not CLASS");
	}
	public Annotation asAnnotation() {
		throw new UnsupportedOperationException(getClass().getSimpleName() + " is not ANNOTATION");
	}
	public List<AnnVal> asArray() {
		throw new UnsupportedOperationException(getClass().getSimpleName() + " is not ARRAY");
	}

	AnnVal() {}

	public static AnnVal parse(ConstantPool pool, DynByteBuf r) {
		int type = r.readUnsignedByte();

		switch (type) {
			case BOOLEAN:
			case BYTE:
			case SHORT:
			case CHAR:
			case INT:
			case DOUBLE:
			case FLOAT:
			case LONG:
			case STRING:
			case CLASS:
				Constant c = pool.get(r);
				switch (type) {
					case DOUBLE:
						return new AnnValDouble(((CstDouble) c).value);
					case FLOAT:
						return new AnnValFloat(((CstFloat) c).value);
					case LONG:
						return new AnnValLong(((CstLong) c).value);
					case STRING:
						return new AnnValString(((CstUTF) c).getString());
					case CLASS:
						return new AnnValClass(((CstUTF) c).getString());
					default:
						return new AnnValInt((char) type, ((CstInt) c).value);
				}
			case ENUM:
				return new AnnValEnum(((CstUTF) pool.get(r)).getString(), ((CstUTF) pool.get(r)).getString());
			case ANNOTATION:
				return new AnnValAnnotation(Annotation.deserialize(pool, r));
			case ARRAY:
				int len = r.readUnsignedShort();
				List<AnnVal> annos = new ArrayList<>(len);
				while (len-- > 0) {
					annos.add(parse(pool, r));
				}
				return new AnnValArray(annos);
		}
		throw new IllegalArgumentException("Unknown annotation value type '" + (char) type + "'");
	}

	public abstract byte type();

	public abstract void toByteArray(ConstantPool pool, DynByteBuf w);

	public abstract String toString();

}