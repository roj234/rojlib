package roj.asm.annotation;

import roj.asm.cp.ConstantPool;
import roj.asm.type.Type;
import roj.config.ValueEmitter;
import roj.text.CharList;
import roj.util.DynByteBuf;

/**
 * JVM注解的流式序列化器.
 * 这个大概很轻量级吧，没啥必要缓存吧.
 * @author Roj234
 * @since 2025/3/13 0:09
 */
public final class AnnotationEncoder implements ValueEmitter {
	private ConstantPool cp;
	private DynByteBuf out;

	public AnnotationEncoder() {}
	public AnnotationEncoder init(ConstantPool cp, DynByteBuf buf) { out = buf; this.cp = cp; return this; }

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

	public final void emit(boolean b) {out.put(Type.BOOLEAN).putShort(cp.getIntId(b ?1:0));}
	public final void emit(byte i) {out.put(Type.BYTE).putShort(cp.getIntId(i));}
	public final void emit(short i) {out.put(Type.SHORT).putShort(cp.getIntId(i));}
	public final void emit(char i) {out.put(Type.CHAR).putShort(cp.getIntId(i));}
	public final void emit(int i) {out.put(Type.INT).putShort(cp.getIntId(i));}
	public final void emit(long i) {out.put(Type.LONG).putShort(cp.getLongId(i));}
	public final void emit(float i) {out.put(Type.FLOAT).putShort(cp.getFloatId(i));}
	public final void emit(double i) {out.put(Type.DOUBLE).putShort(cp.getDoubleId(i));}
	public final void emit(String s) {out.put(AnnVal.STRING).putShort(cp.getUtfId(s));}
	public final void valueClass(String klass) {out.put(AnnVal.ANNOTATION_CLASS).putShort(cp.getUtfId(klass));}
	public final void valueEnum(String klass, String field) {out.put(AnnVal.ENUM).putShort(cp.getUtfId(AnnVal.checkSemicolon(klass))).putShort(cp.getUtfId(field));}
	public final void valueAnnotation(String klass) {
		int id = getTypeId(klass);
		out.put(AnnVal.ANNOTATION).putShort(id);
	}
	public final void emitNull() {throw new UnsupportedOperationException();}

	public final void key(String key) {out.putShort(cp.getUtfId(key));}

	public final void emitList() {emitList(-1);}
	public final void emitList(int size) {
		if (size < 0) throw new UnsupportedOperationException("列表的大小必须已知");
		out.put(Type.ARRAY).putShort(size);
	}

	public final void emitMap() {emitMap(-1);}
	public final void emitMap(int size) {
		if (size < 0) throw new UnsupportedOperationException("映射的大小必须已知");
		out.putShort(size);
	}

	public final void pop() {}
	public final AnnotationEncoder reset() {out = null; cp = null; return this;}
}
