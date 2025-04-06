package roj.crypt;

import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.nio.ByteBuffer;
import java.security.DigestException;
import java.security.MessageDigest;

/**
 * @author solo6975
 * @since 2021/10/3 15:06
 */
public abstract class BufferedDigest extends MessageDigest implements Cloneable {
	protected final ByteList buf;
	protected long length;

	protected BufferedDigest(String algorithm, int cap) {
		super(algorithm);
		buf = ByteList.allocate(cap, cap);
	}

	protected abstract int engineGetDigestLength();

	protected final void engineUpdate(byte b) {
		length++;
		if (!buf.put(b).isWritable()) {
			engineUpdateBlock(buf);
			buf.clear();
		}
	}
	protected final void engineUpdate(byte[] b, int off, int len) {update(DynByteBuf.wrap(b, off, len));}
	protected final void engineUpdate(ByteBuffer b) {
		if (b.hasArray()) {
			super.engineUpdate(b);
			return;
		}
		DynByteBuf bb = DynByteBuf.nioRead(b);
		if (bb == null) {
			super.engineUpdate(b);
			return;
		}
		update(bb);
	}
	public final void update(DynByteBuf b) {
		length += b.readableBytes();

		ByteList L = buf;

		block: {
			if (L.wIndex() > 0) {
				int avl = L.writableBytes();
				if (b.readableBytes() < avl) break block;

				L.put(b, avl);
				engineUpdateBlock(L);
				L.clear();

				b.rIndex += avl;
			}

			while (b.readableBytes() >= L.capacity()) engineUpdateBlock(b);
		}

		L.put(b);
		b.rIndex = b.wIndex();
	}

	protected final byte[] engineDigest() {
		try {
			byte[] b = new byte[engineGetDigestLength()];
			engineDigest(b, 0, b.length);
			return b;
		} catch (DigestException e) {
			throw new IllegalStateException("Should not happen", e);
		}
	}
	protected final int engineDigest(byte[] b, int off, int len) throws DigestException {
		int dLen = engineGetDigestLength();
		if (len < dLen) throw new DigestException("partial digests not returned");
		if (b.length - off < dLen) throw new DigestException("insufficient space in the output buffer to store the digest");

		ByteList bb = DynByteBuf.wrap(b, off, len);
		bb.wIndex(0);
		digest(bb);
		return 16;
	}
	public final void digest(DynByteBuf b) {
		engineDigest(buf, b);
		engineReset();

		length = 0;
		buf.clear();
	}

	protected abstract void engineUpdateBlock(DynByteBuf b);
	protected abstract void engineDigest(ByteList in, DynByteBuf out);

	@Override
	public String toString() {
		return getAlgorithm() + " Message Digest from RojLib";
	}
}