package roj.archive.rangecoder;

import roj.io.CorruptedInputException;
import roj.io.XDataInputStream;

import java.io.IOException;
import java.io.InputStream;

public final class RangeDecoderFromStream extends RangeDecoder {
	public final InputStream in;

	public RangeDecoderFromStream(InputStream in) throws IOException { this(in, false); }
	public RangeDecoderFromStream(InputStream in, boolean mustNotReadBeyond) throws IOException {
		super(4096, mustNotReadBeyond&&!(in instanceof XDataInputStream));

		if (in.read() != 0x00) throw new CorruptedInputException();

		this.in = in;
		for (int i = 0; i < 4; i++) code = (code << 8) | in.read();
		if (code == 0xFFFFFFFF) throw new CorruptedInputException();
		range = 0xFFFFFFFF;
	}

	@Override
	public void finish() {
		if (pos < len && isFinished() && in instanceof XDataInputStream pin) {
			pin.unread(this.buf, pos, len-pos);
		}

		super.finish();
	}

	@Override public boolean isFinished() { return code == 0; }
	@Override int doFill() throws IOException { return in.read(buf); }
}
