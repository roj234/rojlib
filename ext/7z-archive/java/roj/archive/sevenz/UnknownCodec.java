package roj.archive.sevenz;

import roj.text.TextUtil;
import roj.util.DynByteBuf;

import java.io.InputStream;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Roj234
 * @since 2023/3/15 16:04
 */
final class UnknownCodec extends SevenZCodec {
	private final byte[] id, options;
	UnknownCodec(byte[] id, DynByteBuf props) {
		this.id = id;
		options = props.readBytes(props.readableBytes());
	}

	public byte[] id() {return id;}
	public InputStream decode(InputStream in, byte[] password, long uncompressedSize, AtomicInteger memoryLimit) {
		throw new UnsupportedOperationException("不支持的解码器"+TextUtil.bytes2hex(id)+":"+TextUtil.bytes2hex(options));
	}
}