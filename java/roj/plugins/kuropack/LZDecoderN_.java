package roj.plugins.kuropack;

import roj.archive.qz.xz.lz.LZDecoder;
import roj.asmx.injector.Final;
import roj.asmx.injector.Inject;
import roj.asmx.injector.Shadow;
import roj.asmx.injector.Weave;

@Weave(target = LZDecoder.class)
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