package roj.archive.rangecoder;

import roj.io.CorruptedInputException;
import roj.io.PushbackInputStream;

import java.io.IOException;
import java.io.InputStream;

public final class RangeDecoderFromStream extends RangeDecoder {
	public final InputStream in;

	public RangeDecoderFromStream(InputStream in) throws IOException { this(in, false); }
	public RangeDecoderFromStream(InputStream in, boolean mustNotReadBeyond) throws IOException {
		super(4096, mustNotReadBeyond&&!(in instanceof PushbackInputStream));

		if (in.read() != 0x00) throw new CorruptedInputException();

		this.in = in;
		for (int i = 0; i < 4; i++) code = (code << 8) | in.read();
		if (code == 0xFFFFFFFF) throw new CorruptedInputException();
		range = 0xFFFFFFFF;
	}

	@Override
	public void finish() {
		if (pos < len && isFinished() && in instanceof PushbackInputStream pin) {
			byte[] buf = new byte[len-pos];
			System.arraycopy(this.buf, pos, buf, 0, len-pos);
			pin.setBuffer(buf, 0, buf.length);
		}

		super.finish();
	}

	@Override public boolean isFinished() { return code == 0; }
	@Override int doFill() throws IOException { return in.read(buf); }
}
