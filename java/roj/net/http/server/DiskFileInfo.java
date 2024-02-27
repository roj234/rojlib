package roj.net.http.server;

import roj.io.IOUtil;
import roj.net.ch.ChannelCtx;
import roj.net.ch.ChannelHandler;
import roj.net.http.Headers;
import roj.reflect.ReflectionUtils;
import roj.text.Escape;
import roj.util.DirectByteList;
import roj.util.DynByteBuf;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.OpenOption;
import java.nio.file.StandardOpenOption;

/**
 * @author solo6975
 * @since 2022/3/8 0:21
 */
public class DiskFileInfo implements FileInfo, ChannelHandler {
	private final File file;
	private long time;
	private final boolean download;

	private DirectByteList cc;
	private byte[] uc;

	private static final long STATE_OFFSET = ReflectionUtils.fieldOffset(DiskFileInfo.class, "state");
	private volatile int state;

	public DiskFileInfo(File file) {this(file, false);}
	public DiskFileInfo(File file, boolean download) {
		this.file = file;
		this.time = file.lastModified();
		this.download = download;
	}

	public DiskFileInfo compressed() {if (state < 1) state = 1;return this;}
	public DiskFileInfo compressCached() {if (state < 2) state = 2;return this;}
	public DiskFileInfo cached() throws IOException {
		if (file.length() >= Integer.MAX_VALUE-24) throw new IllegalArgumentException("File too large");
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
		if (v == 0) v = MimeType.get(file.getName()).zip ? 1 : -1;
		if (v > 0) {
			stat |= FILE_CAN_DEFLATE;
			if (v == 4) stat |= FILE_DEFLATED;
		}
		return stat;
	}

	@Override
	public long length(boolean deflated) {
		if (deflated) return cc.readableBytes();
		return uc == null ? file.length() : uc.length;
	}

	private static final OpenOption[] READ = {StandardOpenOption.READ};
	@Override
	public FileChannel getSendFile(boolean deflated) throws IOException {return deflated ? null : FileChannel.open(file.toPath(), READ);}

	@Override
	public InputStream get(boolean deflated, long offset) throws IOException {
		if (deflated) return cc.slice((int) offset, cc.readableBytes() - (int) offset).asInputStream();

		var in = uc == null ? new FileInputStream(file) : new ByteArrayInputStream(uc);
		IOUtil.skipFully(in, offset);
		return in;
	}

	@Override
	public long lastModified() {return time;}

	@Override
	public void prepare(ResponseHeader rh, Headers h) {
		if (ReflectionUtils.u.compareAndSwapInt(this, STATE_OFFSET, 2, 3)) {
			cc = DynByteBuf.allocateDirect();
			rh.ch().addBefore("h11@compr", "compress-capture", this);
		}

		if (download) h.putIfAbsent("Content-Disposition", "attachment; filename=\""+Escape.encodeURIComponent(file.getName())+'"');
		h.putIfAbsent("Content-Type", MimeType.getMimeType(file.getName()));
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
		if (h != null) state = 4;
	}
}