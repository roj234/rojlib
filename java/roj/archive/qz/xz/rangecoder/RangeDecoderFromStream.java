package roj.archive.qz.xz.rangecoder;

import roj.io.CorruptedInputException;
import roj.io.PushbackInputStream;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

public final class RangeDecoderFromStream extends RangeDecoder {
	public final InputStream in;

	public RangeDecoderFromStream(InputStream in) throws IOException { this(in, false); }
	public RangeDecoderFromStream(InputStream in, boolean mustNotReadBeyond) throws IOException {
		super(4096, mustNotReadBeyond&&!(in instanceof PushbackInputStream));

		if (in.read() != 0x00) throw new CorruptedInputException();

		this.in = in;
		code = new DataInputStream(in).readInt();
		range = 0xFFFFFFFF;
	}

	public void pushback() {
		assert isFinished();
		if (pos == len) return;

		PushbackInputStream pin = (PushbackInputStream) in;
		byte[] buf = new byte[len-pos];
		System.arraycopy(this.buf, pos, buf, 0, len-pos);
		pin.setBuffer(buf, 0, buf.length);
	}

	@Override
	public boolean isFinished() { return code == 0; }
	@Override
	int doFill() throws IOException { return in.read(buf); }
}
