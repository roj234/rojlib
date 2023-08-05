package roj.archive.qz.xz.rangecoder;

import java.io.IOException;
import java.io.OutputStream;

public final class RangeEncoderToStream extends RangeEncoder {
	public final OutputStream out;

	public RangeEncoderToStream(OutputStream out) {
		super(4096);
		this.out = out;
	}

	@Override
	final void flushNow() throws IOException { out.write(buf); }
	@Override
	public final int finish() throws IOException {
		super.finish();
		out.write(buf, 0, bufPos);
		return 0;
	}
}
