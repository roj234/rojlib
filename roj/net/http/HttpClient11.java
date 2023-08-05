package roj.net.http;

import roj.io.IOUtil;
import roj.net.ch.ChannelCtx;
import roj.net.ch.ChannelHandler;
import roj.net.ch.Event;
import roj.net.ch.handler.StreamCompress;
import roj.util.ByteList;
import roj.util.DynByteBuf;
import roj.util.Helpers;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.zip.CRC32;
import java.util.zip.Inflater;
import java.util.zip.ZipException;

import static java.util.zip.GZIPInputStream.GZIP_MAGIC;

public class HttpClient11 extends HttpRequest implements ChannelHandler {
	static final int SEND_HEAD=0,RECV_HEAD=1,PROCESSING=2,PROCESS_COMPRESS=3,RECV_BODY=4,RECV_CHECKSUM=5,IDLE=6;

	private HttpHead response;

	private boolean sendEof;
	private long len;

	private CRC32 crc;
	private int rcv;

	HttpClient11() {}

	@Override
	public void channelOpened(ChannelCtx ctx) throws IOException {
		state = SEND_HEAD;
		sendEof = false;

		crc = null;
		rcv = 0;
		response = null;

		setCompr(ctx, null);
		setChunk(ctx, 0);

		ByteList text = IOUtil.getSharedByteBuf().putAscii(method()).putAscii(" ");
		_appendPath(text).putAscii(" HTTP/1.1\r\n");
		Headers hdr = _getHeaders();
		hdr.encode(text);

		ctx.channelWrite(text.putShort(0x0D0A));

		_getBody();
		if (_body instanceof DynByteBuf) {
			DynByteBuf b = (DynByteBuf) _body;
			ctx.channelWrite(b.slice(b.rIndex, b.readableBytes()));
		} else {
			/*if ("deflate".equalsIgnoreCase(hdr.get("content-encoding")))
				setCompr(ctx, 1);*/
			if ("chunked".equalsIgnoreCase(hdr.get("transfer-encoding")))
				setChunk(ctx, 1);
		}

		state = RECV_HEAD;
	}

	@Override
	public void channelTick(ChannelCtx ctx) throws IOException {
		if (_body != null) {
			Object body1 = _write(ctx, _body);
			if (body1 == null) {
				ctx.postEvent(StreamCompress.OUT_EOF);
				ctx.postEvent(ChunkSplitter.CHUNK_OUT_EOF);

				if (_body instanceof AutoCloseable) {
					try {
						((AutoCloseable) _body).close();
					} catch (Exception e) {
						Helpers.athrow(e);
					}
				}
			}

			_body = body1;
		}
	}

	@Override
	public void onEvent(ChannelCtx ctx, Event event) throws IOException {
		switch (event.id.toString()) {
			case "null:input_eof": // MyChannel.IN_EOF
				sendEof(ctx, false);
				break;
			case "sc:in_eof": // StreamCompress.IN_EOF
				if (crc != null) {
					((StreamCompress) event.getData()).setInf(null);
					state = RECV_CHECKSUM;
				} else sendEof(ctx, true);
				break;
			case "cs:cin": // ChunkSplitter.CHUNK_IN_EOF
				if (response.getContentEncoding().equals("identity")) {
					sendEof(ctx, true);
				} else if (state == RECV_CHECKSUM) {
					sendEof(ctx, false);
					throw new ZipException("Unexpected gzip EOS: checksum missing");
				}
				break;
		}
	}

	@Override
	@SuppressWarnings("fallthrough")
	public void channelRead(ChannelCtx ctx, Object msg) throws IOException {
		DynByteBuf buf = (DynByteBuf) msg;

		switch (state) {
			case RECV_BODY:
				if (crc != null) {
					rcv += buf.readableBytes();
					if (buf.hasArray()) {
						crc.update(buf.array(), buf.arrayOffset()+buf.rIndex, buf.readableBytes());
					} else {
						crc.update(buf.nioBuffer());
					}
				} else {
					if (len >= 0) {
						len -= buf.readableBytes();
						if (len < 0) throw new IOException("Trailer data: " + buf);
					}
				}

				ctx.channelRead(buf);
				buf.rIndex = buf.wIndex();

				if (len == 0) sendEof(ctx, true);
				return;
			case RECV_CHECKSUM:
				if (buf.readableBytes() < 8) return;

				int crc32 = buf.readIntLE();
				int rcvByte = buf.readIntLE();
				if (crc32 != (int) crc.getValue() || rcvByte != rcv) {
					throw new IOException("gzip数据校验错误:" +
						("crc="+Integer.toHexString(crc32)+"|"+Integer.toHexString((int) crc.getValue())) + "\n" +
					    ("len="+rcv+"|"+rcvByte));
				}
				sendEof(ctx, true);
				crc = null;

				if (buf.isReadable()) throw new IOException("多余的数据: " + buf);
				return;
			case RECV_HEAD:
				if (response == null) {
					if ((response = HttpHead.parseFirst(buf)) == null) return;
				}
				// maybe a limit?
				if (!response.parseHead(buf, IOUtil.getSharedByteBuf())) return;

				len = response.getContentLengthLong();
				state = PROCESSING;

				boolean is1xx = response.getCode() >= 100 && response.getCode() < 200;

				ctx.channelOpened();
				if (state != PROCESSING) return;

				if (is1xx) {
					response = null;
					state = RECV_HEAD;
					return;
				}

				if (method().equals("HEAD")) {
					state = IDLE;
					return;
				}

				state = PROCESS_COMPRESS;

				if ("chunked".equalsIgnoreCase(response.get("transfer-encoding"))) {
					setChunk(ctx, -1);
					ChannelCtx c = ctx.channel().handler("h11@chunk");
					// noinspection all
					c.handler().channelRead(c, buf);
					return;
				}

			case PROCESS_COMPRESS:
				String CE = response.getContentEncoding();
				switch (CE) {
					case "gzip": {
						if (buf.readableBytes() < 10) return;

						int pos = buf.rIndex;
						try {
							checkGZHeader(buf);
						} catch (IndexOutOfBoundsException i) {
							buf.rIndex = pos;
							return;
						}
						crc = new CRC32();

						ctx = setCompr(ctx, new Inflater(true));
					}
					break;
					case "deflate":
						if (buf.readableBytes() < 2) return;
						ctx = setCompr(ctx, checkWrap(buf.readUnsignedShort(buf.rIndex)));
					break;
					case "identity": break;
					case "compress":
					case "br":
					default: throw new UnsupportedEncodingException(CE);
				}

				state = RECV_BODY;
				ctx.handler().channelRead(ctx, buf);
				return;
			case IDLE: if (buf.isReadable()) throw new IOException("Trailer data: " + buf.dump());
		}
	}

	private void sendEof(ChannelCtx ctx, boolean success) throws IOException {
		if (!sendEof) {
			sendEof = true;

			Event event = new Event(DOWNLOAD_EOF).capture();
			event.setData(success);
			ctx.postEvent(event);

			synchronized (this) { notifyAll(); }

			if (response != null && response.getField("connection").equalsIgnoreCase("close")) {
				ctx.channel().closeGracefully();
			}
		}
		state = IDLE;
	}

	@Override
	public void channelClosed(ChannelCtx ctx) throws IOException {
		_close();
		if (!sendEof) {
			sendEof = true;
			synchronized (this) { notifyAll(); }
		}
	}

	@Override
	public void handlerRemoved(ChannelCtx ctx) {
		ctx.channel().remove("h11@compr");
		ctx.channel().remove("h11@chunk");
	}

	/*
	 * File header flags.
	 */
	private final static int FTEXT = 1;     // Extra text
	private final static int FHCRC = 2;     // Header CRC
	private final static int FEXTRA = 4;    // Extra field
	private final static int FNAME = 8;     // File name
	private final static int FCOMMENT = 16; // File comment
	private static int checkGZHeader(DynByteBuf buf) throws IOException {
		int rPos = buf.rIndex;

		// Check header magic
		if (buf.readUShortLE() != GZIP_MAGIC) {
			throw new ZipException("Not in GZIP format");
		}
		// Check compression method
		if (buf.readUnsignedByte() != 8) {
			throw new ZipException("Unsupported compression method");
		}
		// Read flags
		int flg = buf.readUnsignedByte();
		// Skip MTIME, XFL, and OS fields
		buf.rIndex += 6;

		int n = 2 + 2 + 6;

		// extra
		if ((flg & FEXTRA) == FEXTRA) {
			int m = buf.readUShortLE();
			buf.rIndex += m;
			n += m + 2;
		}

		// file name
		if ((flg & FNAME) == FNAME) {
			do {
				n++;
			} while (buf.readByte() != 0);
		}

		// comment
		if ((flg & FCOMMENT) == FCOMMENT) {
			do {
				n++;
			} while (buf.readByte() != 0);
		}

		// header CRC
		if ((flg & FHCRC) == FHCRC) {
			CRC32 crc = new CRC32();
			ByteBuffer nio = buf.nioBuffer();
			nio.limit(buf.rIndex).position(rPos);
			crc.update(nio);
			int v = (int) crc.getValue() & 0xffff;
			if (buf.readUShortLE() != v) {
				throw new ZipException("Corrupt GZIP header");
			}
			n += 2;
		}

		return n;
	}

	public static Inflater checkWrap(int first2) {
		int flag0 = first2 >>> 8;

		// 检测Zlib Header
		boolean wrap = false;
		// CM
		if ((flag0 & 0xF) == 0x8) {
			// CINFO
			if ((flag0 >>> 4 & 0xF) <= 7) {
				int flag1 = first2&0xFF;
				// FCHECK
				if (0 == ((flag0 << 8) | flag1) % 31) {
					wrap = true;
				}
			}
		}
		return new Inflater(wrap);
	}

	@Override
	public HttpHead response() { return response; }

	@Override
	public void waitFor() throws InterruptedException {
		synchronized (this) {
			while (!sendEof) { wait(); }
		}
	}

	private static ChannelCtx setCompr(ChannelCtx ctx, Inflater inf) {
		StreamCompress sc;

		ChannelCtx hwarp = ctx.channel().handler("h11@compr");
		if (hwarp == null) {
			if (inf == null) return ctx;

			ctx.channel().addBefore(ctx, "h11@compr", sc = new StreamCompress(1024));
			hwarp = ctx.channel().handler("h11@compr");
		} else {
			sc = (StreamCompress) hwarp.handler();
		}

		if (inf == null) {
			Inflater inf1 = sc.getInf();
			if (inf1 != null) inf1.end();
		}
		sc.setInf(inf);
		return hwarp;
	}
	public static ChannelCtx setChunk(ChannelCtx ctx, int op) {
		ChunkSplitter cs;

		ChannelCtx hwarp = ctx.channel().handler("h11@chunk");
		if (hwarp == null) {
			// close
			if (op == 0) return ctx;

			ChannelCtx c1 = ctx.channel().handler("h11@compr");
			if (c1 != null) ctx = c1;

			ctx.channel().addBefore(ctx, "h11@chunk", cs = new ChunkSplitter());
			hwarp = ctx.channel().handler("h11@chunk");
		} else {
			cs = (ChunkSplitter) hwarp.handler();

			if (op == 0) {
				cs.reset();
				return ctx;
			}
		}

		if (op == -1) cs.enableIn();
		else cs.enableOut();
		return hwarp;
	}

	@Override
	public HttpRequest clone() {
		return _copyTo(new HttpClient11());
	}
}
