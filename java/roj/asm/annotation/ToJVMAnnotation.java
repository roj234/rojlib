package roj.asm.annotation;

import roj.asm.cp.ConstantPool;
import roj.asm.type.Type;
import roj.config.serial.CVisitor;
import roj.text.CharList;
import roj.util.DynByteBuf;

/**
 * JVM注解的流式序列化器.
 * 这个大概很轻量级吧，没啥必要缓存吧.
 * @author Roj234
 * @since 2025/3/13 0:09
 */
public final class ToJVMAnnotation implements CVisitor {
	private ConstantPool cp;
	private DynByteBuf ob;

	public ToJVMAnnotation() {}
	public ToJVMAnnotation init(ConstantPool cp, DynByteBuf buf) { ob = buf; this.cp = cp; return this; }

	public int getTypeId(String klass) {
		int id;
		if (klass.endsWith(";")) {
			id = cp.getUtfId(klass);
		} else {
			CharList sb = new CharList().append('L').append(klass).append(';');
			id = cp.getUtfId(sb);
			sb._free();
		}
		return id;
	}

	public final void value(boolean b) {ob.put(Type.BOOLEAN).putShort(cp.getIntId(b ?1:0));}
	public final void value(byte i) {ob.put(Type.BYTE).putShort(cp.getIntId(i));}
	public final void value(short i) {ob.put(Type.SHORT).putShort(cp.getIntId(i));}
	public final void value(char i) {ob.put(Type.CHAR).putShort(cp.getIntId(i));}
	public final void value(int i) {ob.put(Type.INT).putShort(cp.getIntId(i));}
	public final void value(long i) {ob.put(Type.LONG).putShort(cp.getLongId(i));}
	public final void value(float i) {ob.put(Type.FLOAT).putShort(cp.getFloatId(i));}
	public final void value(double i) {ob.put(Type.DOUBLE).putShort(cp.getDoubleId(i));}
	public final void value(String s) {ob.put(AnnVal.STRING).putShort(cp.getUtfId(s));}
	public final void valueClass(String klass) {ob.put(AnnVal.ANNOTATION_CLASS).putShort(cp.getUtfId(klass));}
	public final void valueEnum(String klass, String field) {ob.put(AnnVal.ENUM).putShort(cp.getUtfId(AnnVal.checkSemicolon(klass))).putShort(cp.getUtfId(field));}
	public final void valueAnnotation(String klass) {
		int id = getTypeId(klass);
		ob.put(AnnVal.ANNOTATION).putShort(id);
	}
	public final void valueNull() {throw new UnsupportedOperationException();}

	public final void key(String key) {ob.putShort(cp.getUtfId(key));}

	public final void valueList() {valueList(-1);}
	public final void valueList(int size) {
		if (size < 0) throw new UnsupportedOperationException("列表的大小必须已知");
		ob.put(Type.ARRAY).putShort(size);
	}

	public final void valueMap() {valueMap(-1);}
	public final void valueMap(int size) {
		if (size < 0) throw new UnsupportedOperationException("映射的大小必须已知");
		ob.putShort(size);
	}

	public final void pop() {}
	public final ToJVMAnnotation reset() {ob = null; cp = null; return this;}
}
