package roj.archive.qz;

import roj.text.TextUtil;
import roj.util.DynByteBuf;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Roj234
 * @since 2023/3/15 16:04
 */
final class Unknown extends QZCoder {
	private final byte[] id;
	private byte[] options;
	Unknown(byte[] id) {this.id = id;}

	public byte[] id() {return id;}
	public InputStream decode(InputStream in, byte[] password, long uncompressedSize, AtomicInteger memoryLimit) throws IOException {throw new IOException("不支持的解码器"+TextUtil.bytes2hex(id)+":"+TextUtil.bytes2hex(options));}

	public void readOptions(DynByteBuf buf, int length) {options = buf.readBytes(length);}
}