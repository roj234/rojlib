package roj.net.http;

import roj.io.IOUtil;
import roj.net.URIUtil;
import roj.net.ch.ChannelCtx;
import roj.net.ch.ChannelHandler;
import roj.net.ch.Event;
import roj.net.ch.handler.StreamCompress;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.zip.CRC32;
import java.util.zip.Inflater;
import java.util.zip.ZipException;

import static java.util.zip.GZIPInputStream.GZIP_MAGIC;

public class HttpClient11 extends IHttpClient implements ChannelHandler {
	static final int SEND_HEAD=0,RECV_HEAD=1,PROCESS_COMPRESS=2,RECV_BODY=3,RECV_CHECKSUM=4,IDLE=5;

	HttpHead response;

	boolean sendEof;
	long len;

	CRC32 crc;
	int rcv;

	public HttpClient11() {}

	@Override
	public void channelOpened(ChannelCtx ctx) throws IOException {
		state = SEND_HEAD;
		sendEof = false;

		crc = null;

		setCompr(ctx, null);
		setChunk(ctx, 0);

		if (body instanceof DynByteBuf)
			header.put("content-length", Integer.toString(((DynByteBuf) body).readableBytes()));
		else if (body instanceof String) {
			header.put("transfer-encoding", "chunked");
		} else header.put("content-length", "0");

		header.putIfAbsent("accept", "text/html, image/gif, image/jpeg, *; q=.2, */*; q=.2");
		header.putIfAbsent("connection", "keep-alive");
		header.putIfAbsent("user-agent", "Java-Http-Client/2.0.0");
		header.putIfAbsent("accept-encoding", "gzip, deflate");

		ByteList text = IOUtil.getSharedByteBuf();
		text.putAscii(action).putAscii(" ");
		if (urlPreEncoded) {
			text.putAscii(url.getPath().isEmpty()?"/": url.getPath());
			if (url.getQuery() != null) text.put((byte) '?').putAscii(url.getQuery());
		} else {
			ByteList tmp = (ByteList) ctx.allocate(false, Math.max(url.getPath().length(), (url.getQuery() == null ? 0 : url.getQuery().length())) * 5);
			try {
				if (url.getPath().isEmpty()) text.putAscii("/");
				else URIUtil.encodeURI(url.getPath(), tmp, text, URIUtil.URI_SAFE);
				if (url.getQuery() != null) URIUtil.encodeURI(url.getQuery(), tmp, text.put((byte) '?'), URIUtil.URI_SAFE);
			} finally {
				ctx.reserve(tmp);
			}
		}

		header.encode(text.putAscii(" HTTP/1.1\r\n"));

		ctx.channelWrite(text.putShort(0x0D0A));

		if (body instanceof DynByteBuf) {
			ctx.channelWrite(body);
			body = null;
		} else if (body instanceof String) {
			setChunk(ctx, 1);
		}

		response = null;
		state = RECV_HEAD;
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
			case "hc:body_eof": // BODY_EOF
				if (body instanceof String) {
					ctx.postEvent(StreamCompress.OUT_EOF);
					ctx.postEvent(ChunkSplitter.CHUNK_OUT_EOF);
					body = null;
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
				ctx.channelOpened();

				if (action.equals("HEAD")) {
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
						if (buf.readableBytes() < 4) return;

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

	@Override
	public void channelWrite(ChannelCtx ctx, Object msg) throws IOException {
		if (body instanceof String) ctx.channelWrite(msg);
		else throw new UnsupportedOperationException();
	}

	private void sendEof(ChannelCtx ctx, boolean success) throws IOException {
		if (!sendEof) {
			sendEof = true;

			Event event = new Event(DOWNLOAD_EOF);
			event.setData(success);
			ctx.postEvent(event);

			synchronized (this) { notifyAll(); }

			if (response != null && response.getHeaderField("connection").equalsIgnoreCase("close")) {
				ctx.close();
			}
		}
		state = IDLE;
	}

	@Override
	public void channelClosed(ChannelCtx ctx) throws IOException {
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
	private final static int FTEXT = 1;    // Extra text
	private final static int FHCRC = 2;    // Header CRC
	private final static int FEXTRA = 4;    // Extra field
	private final static int FNAME = 8;    // File name
	private final static int FCOMMENT = 16;   // File comment
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
	public HttpHead response() {
		return response;
	}

	@Override
	public ChannelHandler asChannelHandler() {
		return this;
	}

	@Override
	public void waitFor() throws InterruptedException {
		synchronized (this) {
			if (sendEof) {
				notifyAll();
			}
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
}
