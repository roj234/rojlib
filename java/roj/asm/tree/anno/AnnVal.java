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

	public static final char STRING = 's', ENUM = 'e', ANNOTATION_CLASS = 'c', ANNOTATION = '@';

	public int asInt() { throw new UnsupportedOperationException(getClass().getSimpleName()+" is not INT"); }
	public float asFloat() { throw new UnsupportedOperationException(getClass().getSimpleName()+" is not FLOAT"); }
	public double asDouble() { throw new UnsupportedOperationException(getClass().getSimpleName()+" is not DOUBLE"); }
	public long asLong() { throw new UnsupportedOperationException(getClass().getSimpleName()+" is not LONG"); }
	public String asString() { throw new UnsupportedOperationException(getClass().getSimpleName()+" is not STRING"); }
	public AnnValEnum asEnum() { throw new UnsupportedOperationException(getClass().getSimpleName()+" is not ENUM"); }
	public Type asClass() { throw new UnsupportedOperationException(getClass().getSimpleName()+" is not CLASS"); }
	public Annotation asAnnotation() { throw new UnsupportedOperationException(getClass().getSimpleName()+" is not ANNOTATION"); }
	public List<AnnVal> asArray() { throw new UnsupportedOperationException(getClass().getSimpleName()+" is not ARRAY"); }

	protected AnnVal() {}

	public static AnnVal parse(ConstantPool pool, DynByteBuf r) {
		int type = r.readUnsignedByte();

		switch (type) {
			case BOOLEAN, BYTE, SHORT, CHAR, INT:
			case DOUBLE, FLOAT, LONG, STRING, ANNOTATION_CLASS:
				Constant c = pool.get(r);
				return switch (type) {
					case DOUBLE -> valueOf(((CstDouble) c).value);
					case FLOAT -> valueOf(((CstFloat) c).value);
					case LONG -> valueOf(((CstLong) c).value);
					case STRING -> valueOf(((CstUTF) c).str());
					case ANNOTATION_CLASS -> new AnnValClass(((CstUTF) c).str());
					default -> new AnnValInt((char) type, ((CstInt) c).value);
				};
			case ENUM: return new AnnValEnum(checkSemicolon(((CstUTF) pool.get(r)).str()), ((CstUTF) pool.get(r)).str());
			case ANNOTATION: return new AnnValAnnotation(Annotation.parse(pool, r));
			case ARRAY:
				int len = r.readUnsignedShort();
				List<AnnVal> annos = new ArrayList<>(len);
				while (len-- > 0) annos.add(parse(pool, r));
				return new AnnValArray(annos);
		}
		throw new IllegalArgumentException("Unknown annotation value type '"+(char) type+"'");
	}

	private static String checkSemicolon(String str) {
		if (!str.endsWith(";")) throw new IllegalArgumentException("无效的枚举类型:"+str);
		return str;
	}

	public abstract byte type();

	public abstract void toByteArray(ConstantPool cp, DynByteBuf w);
	public abstract String toString();
	public String toRawString() { return toString(); }
}