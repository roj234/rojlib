package roj.asmx.classpak;

import roj.asmx.nixim.Final;
import roj.asmx.nixim.Inject;
import roj.asmx.nixim.Nixim;
import roj.asmx.nixim.Shadow;

@Nixim("roj.archive.qz.xz.lz.LZDecoder")
abstract class LZDecoderN_ {
	@Shadow
	int pos,start;
	@Shadow
	@Final
	int bufSize;
	@Shadow
	@Final
	byte[] buf;

	@Inject("flush")
	public int flush(byte[] out, int outOff) {
		int copySize = pos - start;
		if (pos == bufSize) pos = 0;

		System.arraycopy(buf, start, out, outOff, copySize);
		start = pos;

		return copySize;
	}

	@Inject(at = Inject.At.REMOVE, value = "flush0")
	public abstract int flush0(Object out, long outOff);
}