package roj.io;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.InputStream;

/**
 * 支持各种BOM的输入流
 *
 * @author Roj234
 * @since 2021/3/7 11:58
 */
public class BOMInputStream extends PushbackInputStream {
	String encoding;

	public BOMInputStream(InputStream in) {
		this(in, "UTF-8");
	}

	public BOMInputStream(InputStream in, String defaultEnc) {
		super(in);
		this.encoding = defaultEnc;
		this.buffer = new byte[4];
		this.bOff = -1;
		this.bLen = 4;
	}

	public BOMInputStream(InputStream in, String defaultEnc, boolean autoInit) throws IOException {
		this(in, defaultEnc);
		if (autoInit) init();
	}

	public String getEncoding() throws IOException {
		init();
		return encoding;
	}

	@Override
	public int available() throws IOException {
		init();
		return super.available();
	}

	/**
	 * Skip BOM bytes
	 */
	protected void init() throws IOException {
		if (bOff != -1) {
			return;
		}

		byte[] bom = buffer;
		int n = bLen = in.read(bom, 0, 4);

		int rev = 0;

		switch (bom[0] & 0xFF) {
			case 0x00:
				if ((bom[1] == (byte) 0x00) && (bom[2] == (byte) 0xFE) && (bom[3] == (byte) 0xFF)) {
					encoding = "UTF-32BE";
					rev = 4;
				}
				break;

			case 0xFF:
				if (bom[1] == (byte) 0xFE) {
					if ((bom[2] == (byte) 0x00) && (bom[3] == (byte) 0x00)) {
						encoding = "UTF-32LE";
						rev = 4;
					} else {
						encoding = "UTF-16LE";
						rev = 2;
					}
				}
				break;

			case 0xEF:
				if ((bom[1] == (byte) 0xBB) && (bom[2] == (byte) 0xBF)) {
					encoding = "UTF-8";
					rev = 3;
				}
				break;

			case 0xFE:
				if ((bom[1] == (byte) 0xFF)) {
					encoding = "UTF-16BE";
					rev = 2;
				}
				break;
		}

		bOff = rev;
	}

	@Override
	public int read(@Nonnull byte[] b, int off, int len) throws IOException {
		init();
		return super.read(b, off, len);
	}

	@Override
	public int read() throws IOException {
		init();
		return super.read();
	}
}