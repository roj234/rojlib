package roj.config.serial;

import roj.util.DynByteBuf;

import java.io.IOException;

/**
 * @author Roj234
 * @since 2024/11/29 3:25
 */
public final class ToBEncode implements CVisitor {
	public ToBEncode() {}
	public ToBEncode(DynByteBuf buf) { this.ob = buf; }

	private DynByteBuf ob;
	public DynByteBuf buffer() { return ob; }
	public ToBEncode buffer(DynByteBuf buf) { ob = buf; return this; }

	private int depth;

	public final void value(boolean l) {value(l?1:0);}
	public final void value(int l) {ob.put('i').putAscii(Integer.toString(l)).put('e');}
	public final void value(long l) {ob.put('i').putAscii(Long.toString(l)).put('e');}
	public final void value(double l) {throw new UnsupportedOperationException("BEncode不支持浮点数");}
	public final void value(String l) {ob.putAscii(Integer.toString(DynByteBuf.byteCountUTF8(l))).put(':').putUTFData(l);}
	public final void valueNull() {throw new UnsupportedOperationException("BEncode不支持null");}
	public final boolean supportArray() {return true;}
	public final void value(byte[] ba) {ob.putAscii(Integer.toString(ba.length)).put(':').put(ba);}

	// *no state check*
	public final void key(String key) {value(key);}

	public final void valueList() {ob.write('l');depth++;}
	public final void valueMap() {ob.write('d');depth++;}
	public final void pop() {
		if (depth == 0) throw new IllegalStateException("Stack underflow");
		depth--;
		ob.write('e');
	}

	public final ToBEncode reset() {
		depth = 0;
		return this;
	}

	@Override
	public void close() throws IOException { if (ob != null) ob.close(); }
}