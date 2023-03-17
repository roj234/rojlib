package roj.net.http.srv;

import roj.net.http.Headers;
import roj.net.http.IllegalRequestException;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.nio.charset.StandardCharsets;

/**
 * @author Roj233
 * @since 2022/3/13 15:15
 */
public abstract class MultipartFormHandler implements HPostHandler {
	private byte[] boundary;
	private int state;

	private final ByteList tmp = new ByteList(32);
	private final Headers hdr = new Headers();

	protected boolean hasArray;

	public MultipartFormHandler() {}
	public MultipartFormHandler(Request req) {
		init(req.header("Content-Type"));
	}

	public void init(String contentType) {
		if (contentType.startsWith("multipart")) {
			String strBound = Headers.decodeOneValue(contentType, "boundary");
			if (strBound == null) throw new IllegalArgumentException("Not found boundary in Content-Type header: " + contentType);
			boundary = strBound.getBytes(StandardCharsets.UTF_8);
			state = 2;
		} else state = 0;
		tmp.clear();
	}

	@Override
	public final void onData(DynByteBuf buf) throws IllegalRequestException {
		hasArray = buf.hasArray();
		loop:
		while (true) {
			switch (state) {
				// begin: find name
				case 0:
					while (buf.isReadable()) {
						byte b = buf.get();
						if (b == '=') {
							onKey(tmp.readAscii(tmp.wIndex()));
							tmp.clear();
							state = 1;
							continue loop;
						}
						tmp.put(b);
					}
					return;
				// put data until find & [delim]
				case 1:
					int i = buf.rIndex;
					while (buf.isReadable()) {
						byte b = buf.get();
						if (b == '&') {
							int p = buf.rIndex - 1;
							int l = buf.wIndex();

							buf.rIndex(i);
							buf.wIndex(p);
							onValue(buf);

							buf.wIndex(l);

							state = 0;
							continue loop;
						}
					}
					buf.rIndex(i);
					onValue(buf);
					break;

				// first: find multipart begin
				case 2:
					int L = 4 + boundary.length;
					int prev = buf.rIndex;
					while (buf.readableBytes() > L) {
						if (buf.get() == 0x2D) {
							int pos = buf.rIndex;
							check:
							// --
							if (buf.get() == 0x2D) {
								byte[] boundary = this.boundary;
								for (byte b : boundary) {
									if (b != buf.get()) {
										buf.rIndex = pos;
										break check;
									}
								}

								int p = buf.rIndex - boundary.length - 2;
								if (p > prev) {
									int lim = buf.wIndex();
									buf.wIndex(p);
									buf.rIndex(prev);
									onValue(buf);
									buf.wIndex(lim);
									buf.rIndex(p + boundary.length + 2);
								}

								// \r\n
								char v = buf.readChar();
								if (v != 0x0D0A) {
									state = -1;
									// EOF
									if (v == 0x2D2D && buf.readChar() == 0x0D0A) {
										return;
									}
									throw new IllegalArgumentException("Invalid multipart format");
								}

								hdr.clear();
								state = 3;
								continue loop;
							}
						}
					}
					buf.rIndex(prev);
					if (buf.readableBytes() < L) return;
					onValue(buf);
					buf.rIndex(buf.wIndex());
					break;
				// then, read header
				case 3:
					if (!hdr.parseHead(buf)) return;
					onMPKey(hdr);
					state = 2;
					break;
				case -1:
					return;
			}
		}
	}

	public static final class FormData {
		public Headers h = new Headers();
		public ByteList data;

		@Override
		public String toString() {
			return "FormData{" + "h=" + h + ", data=" + data + '}';
		}
	}

	protected void onMPKey(Headers hdr) throws IllegalRequestException {
		String cd = hdr.get("content-disposition");
		if (cd == null) throw new NullPointerException("override onMPKey() to support abnormal multipart header");
		String name = Headers.decodeOneValue(cd, "name");
		if (name == null) throw new NullPointerException("override onMPKey() to support abnormal multipart header");
		onKey(name);
	}

	protected abstract void onKey(CharSequence key) throws IllegalRequestException;
	protected abstract void onValue(DynByteBuf buf) throws IllegalRequestException;
}
