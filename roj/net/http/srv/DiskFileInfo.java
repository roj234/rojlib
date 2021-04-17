package roj.net.http.srv;

import roj.io.IOUtil;
import roj.io.source.FileSource;
import roj.net.URIUtil;
import roj.net.ch.ChannelCtx;
import roj.net.ch.ChannelHandler;
import roj.net.http.Headers;
import roj.util.DirectByteList;
import roj.util.DynByteBuf;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * @author solo6975
 * @since 2022/3/8 0:21
 */
public class DiskFileInfo implements FileInfo, ChannelHandler {
	private final File file;
	private long time;

	private DirectByteList cc;
	private byte[] uc;

	private volatile int state;

	public DiskFileInfo(File file) {
		this.file = file;
		this.time = file.lastModified();
	}

	public DiskFileInfo compressCached() {
		if (state == 0) state = 1;
		return this;
	}
	public DiskFileInfo dontCompress() {
		state = -1;
		cc = null;
		return this;
	}
	public DiskFileInfo cached() throws IOException {
		if (file.length() >= Integer.MAX_VALUE-24)
			throw new IllegalArgumentException("File too large");
		if (uc == null) {
			uc = IOUtil.read(file);
			time = file.lastModified();
		}
		return this;
	}

	@Override
	public int stats() {
		if (uc == null && (System.currentTimeMillis()&511) == 0)
			time = file.lastModified();

		int stat = FILE_RA;
		int v = state;
		if (v >= 0) {
			stat |= FILE_WANT_DEFLATE;
			if (v == 3) stat |= FILE_DEFLATED| FILE_RA_DEFLATE;
		}
		return stat;
	}

	@Override
	public long length(boolean deflated) {
		if (deflated) return cc.readableBytes();
		return uc == null ? file.length() : uc.length;
	}

	@Override
	public InputStream get(boolean deflated, long offset) throws IOException {
		if (deflated) return cc.slice((int) offset, cc.readableBytes() - (int) offset).asInputStream();
		return uc == null ? new FileSource(file, offset).asInputStream() : new ByteArrayInputStream(uc);
	}

	@Override
	public long lastModified() {
		return time;
	}

	public Response response(Request req, ResponseHeader rh) {
		return FileResponse.response(req, this);
	}

	@Override
	public void prepare(ResponseHeader srv, Headers h) {
		if (state == 1) {
			synchronized (this) {
				if (state == 1) {
					state = 2;
					srv.ch().channel().addBefore("h11@compr", "compress-capture", this);
					cc = DirectByteList.allocateDirect();
				}
			}
		}

		String ext = file.getName();
		String type = FileResponse.getMimeType(ext);

		h.putIfAbsent("Content-Disposition", "attachment; filename=\"" + URIUtil.encodeURIComponent(file.getName()) + '"');
		h.putIfAbsent("Content-Type", type);
	}

	@Override
	public void channelWrite(ChannelCtx ctx, Object msg) throws IOException {
		DynByteBuf in = (DynByteBuf) msg;
		cc.put(in);
		ctx.channelWrite(in);
	}

	@Override
	public void release(ChannelCtx ctx) {
		ChannelHandler h = ctx.channel().remove("compress-capture");
		if (h != null) state = 3;
	}
}
