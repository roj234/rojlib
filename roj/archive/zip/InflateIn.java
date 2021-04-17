package roj.archive.zip;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

/**
 * @author Roj234
 * @since 2023/3/14 0014 0:42
 */
final class InflateIn extends InflaterInputStream {
	InflateIn(InputStream in) {
		super(in, new Inflater(true), 512);
	}

	public InflateIn reset(InputStream in) {
		myClosed = false;
		this.in = in;
		this.inf.reset();
		return this;
	}

	@Override
	public int available() {
		return myClosed ? 0 : 1;
	}

	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		len = super.read(b, off, len);
		if (len < 0) close();
		return len;
	}

	boolean myClosed;

	@Override
	public void close() throws IOException {
		if (!myClosed) {
			myClosed = true;
			List<InflateIn> infs = ZipArchive.inflaters.get();
			if (infs.size() < ZipArchive.MAX_INFLATER_SIZE) {
				infs.add(this);
				in.close();
			} else {
				super.close();
			}
		}
	}
}
