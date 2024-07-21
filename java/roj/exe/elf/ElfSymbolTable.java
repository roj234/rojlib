package roj.exe.elf;

import roj.collect.SimpleList;
import roj.util.ByteList;

import java.io.IOException;

/**
 * @author Roj233
 * @since 2022/5/30 21:50
 */
public class ElfSymbolTable implements ElfSegment {
	public static final byte SY_BIND_LOCAL = 0;
	public static final byte SY_BIND_GLOBAL = 1;
	public static final byte SY_BIND_WEAK = 2;

	public static final byte SY_TYPE_NONE = 0;
	public static final byte SY_TYPE_OBJECT = 1;
	public static final byte SY_TYPE_FUNC = 2;
	public static final byte SY_TYPE_SECTION = 3;
	public static final byte SY_TYPE_FILE = 4;

	public SimpleList<Symbol> symbols;
	public ElfSectionD delegate;

	public ElfSymbolTable() {
		symbols = new SimpleList<>();
	}

	public static final class Symbol implements ElfSegment {
		public static final int SIZE = 16;

		public int name;
		public int value, size;
		public byte bind, type;
		public char header_idx;

		@Override
		public void toByteArray(ElfFile owner, ByteList w) {
			w.putInt(name).putInt(value).putInt(size).put(bind).put(type).putShort(header_idx);
		}

		@Override
		public void fromByteArray(ElfFile owner, ByteList r) {
			name = r.readInt();
			value = r.readInt();
			size = r.readInt();
			bind = r.readByte();
			type = r.readByte();
			header_idx = r.readChar();
		}
	}

	@Override
	public void toByteArray(ElfFile owner, ByteList w) {
		delegate.toByteArray(owner, w);
	}

	@Override
	public void fromByteArray(ElfFile owner, ByteList r) throws IOException {
		if (r.readInt(4) != ElfSectionD.DT_DYNAMIC_SYMBOL) throw new IllegalArgumentException();
		if (delegate == null) delegate = new ElfSectionD();
		delegate.fromByteArray(owner, r);
		owner.read(delegate.offset, delegate.length);

		r = owner.getData();
		int len = r.wIndex() >>> 4;
		symbols.clear();
		symbols.ensureCapacity(len);
		while (len-- > 0) {
			Symbol sym = new Symbol();
			sym.fromByteArray(owner, r);
			symbols.add(sym);
		}
	}
}