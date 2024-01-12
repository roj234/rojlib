package roj.asm.tree.anno;

import roj.asm.cp.*;
import roj.asm.type.Type;
import roj.util.DynByteBuf;

import java.util.ArrayList;
import java.util.List;

import static roj.asm.type.Type.*;
/**
 * @author Roj234
 * @since 2021/5/29 17:16
 */
public abstract class AnnVal {
	public static AnnVal valueOf(String v) { return new AnnValString(v); }
	public static AnnVal valueOf(byte v) { return new AnnValInt(BYTE, v); }
	public static AnnVal valueOf(char v) { return new AnnValInt(CHAR, v); }
	public static AnnVal valueOf(short v) { return new AnnValInt(SHORT, v); }
	public static AnnVal valueOf(int v) { return new AnnValInt(INT, v); }
	public static AnnVal valueOf(float v) { return new AnnValFloat(v); }
	public static AnnVal valueOf(long v) { return new AnnValLong(v); }
	public static AnnVal valueOf(double v) { return new AnnValDouble(v); }

	public static final char STRING = 's';
	public static final char ENUM = 'e';
	public static final char ANNOTATION_CLASS = 'c';
	public static final char ANNOTATION = '@';

	public int asInt() { throw new UnsupportedOperationException(getClass().getSimpleName() + " is not INT"); }
	public float asFloat() { throw new UnsupportedOperationException(getClass().getSimpleName() + " is not FLOAT"); }
	public double asDouble() { throw new UnsupportedOperationException(getClass().getSimpleName() + " is not DOUBLE"); }
	public long asLong() { throw new UnsupportedOperationException(getClass().getSimpleName() + " is not LONG"); }
	public String asString() { throw new UnsupportedOperationException(getClass().getSimpleName() + " is not STRING"); }
	public AnnValEnum asEnum() { throw new UnsupportedOperationException(getClass().getSimpleName() + " is not ENUM"); }
	public Type asClass() { throw new UnsupportedOperationException(getClass().getSimpleName() + " is not CLASS"); }
	public Annotation asAnnotation() { throw new UnsupportedOperationException(getClass().getSimpleName() + " is not ANNOTATION"); }
	public List<AnnVal> asArray() { throw new UnsupportedOperationException(getClass().getSimpleName() + " is not ARRAY"); }

	AnnVal() {}

	public static AnnVal parse(ConstantPool pool, DynByteBuf r) {
		int type = r.readUnsignedByte();

		switch (type) {
			case BOOLEAN: case BYTE: case SHORT: case CHAR: case INT:
			case DOUBLE: case FLOAT: case LONG: case STRING: case ANNOTATION_CLASS:
				Constant c = pool.get(r);
				switch (type) {
					case DOUBLE: return new AnnValDouble(((CstDouble) c).value);
					case FLOAT: return new AnnValFloat(((CstFloat) c).value);
					case LONG: return new AnnValLong(((CstLong) c).value);
					case STRING: return new AnnValString(((CstUTF) c).str());
					case ANNOTATION_CLASS: return new AnnValClass(((CstUTF) c).str());
					default: return new AnnValInt((char) type, ((CstInt) c).value);
				}
			case ENUM: return new AnnValEnum(((CstUTF) pool.get(r)).str(), ((CstUTF) pool.get(r)).str());
			case ANNOTATION: return new AnnValAnnotation(Annotation.parse(pool, r));
			case ARRAY:
				int len = r.readUnsignedShort();
				List<AnnVal> annos = new ArrayList<>(len);
				while (len-- > 0) annos.add(parse(pool, r));
				return new AnnValArray(annos);
		}
		throw new IllegalArgumentException("Unknown annotation value type '" + (char) type + "'");
	}

	public abstract byte type();

	public abstract void toByteArray(ConstantPool cp, DynByteBuf w);
	public abstract String toString();
	public String toRawString() { return toString(); }
}