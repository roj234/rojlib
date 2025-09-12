package roj.config;

import roj.util.DynByteBuf;

import java.io.IOException;

/**
 * @author Roj234
 * @since 2024/11/29 3:25
 */
public final class BEncodeEncoder implements ValueEmitter {
	public BEncodeEncoder() {}
	public BEncodeEncoder(DynByteBuf buf) { this.out = buf; }

	private DynByteBuf out;
	public DynByteBuf buffer() { return out; }
	public BEncodeEncoder buffer(DynByteBuf buf) { out = buf; return this; }

	private int depth;

	public final void emit(boolean b) {emit(b?1:0);}
	public final void emit(int i) {out.put('i').putAscii(Integer.toString(i)).put('e');}
	public final void emit(long i) {out.put('i').putAscii(Long.toString(i)).put('e');}
	public final void emit(double i) {throw new UnsupportedOperationException("BEncode不支持浮点数");}
	public final void emit(String s) {out.putAscii(Integer.toString(DynByteBuf.byteCountUTF8(s))).put(':').putUTFData(s);}
	public final void emitNull() {throw new UnsupportedOperationException("BEncode不支持null");}
	public final boolean supportArray() {return true;}
	public final void emit(byte[] array) {out.putAscii(Integer.toString(array.length)).put(':').put(array);}

	// *no state check*
	public final void key(String key) {
		emit(key);}

	public final void emitList() {out.write('l');depth++;}
	public final void emitMap() {out.write('d');depth++;}
	public final void pop() {
		if (depth == 0) throw new IllegalStateException("Stack underflow");
		depth--;
		out.write('e');
	}

	public final BEncodeEncoder reset() {
		depth = 0;
		return this;
	}

	@Override
	public void close() throws IOException { if (out != null) out.close(); }
}