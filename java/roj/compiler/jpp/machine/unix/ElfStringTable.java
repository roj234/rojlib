package roj.compiler.jpp.machine.unix;

import roj.collect.IntMap;
import roj.util.ByteList;

import java.io.IOException;

/**
 * @author Roj233
 * @since 2022/5/30 21:50
 */
public class ElfStringTable implements ElfSegment {
	private final IntMap<String> cache;

	public ByteList data;
	public ElfSectionD delegate;

	public ElfStringTable() {
		cache = new IntMap<>();
		data = new ByteList();
	}

	public String getAt(int idx) {
		String name = cache.get(idx);
		if (name == null) {
			data.rIndex = idx;
			int len = data.readZeroTerminate(0);
			name = data.readAscii(len);
			cache.put(idx, name);
		}
		return name;
	}

	public void invalidateCache() {
		cache.clear();
	}

	@Override
	public void toByteArray(ElfFile owner, ByteList w) {
		delegate.toByteArray(owner, w);
	}

	@Override
	public void fromByteArray(ElfFile owner, ByteList r) throws IOException {
		if (r.readInt(4) != ElfSectionD.DT_STRING_TABLE) throw new IllegalArgumentException();
		if (delegate == null) delegate = new ElfSectionD();
		delegate.fromByteArray(owner, r);
		owner.read(delegate.offset, delegate.length);

		data.setArray(r.toByteArray());
		invalidateCache();
	}
}