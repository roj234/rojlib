package roj.crypt;

import roj.util.DynByteBuf;

import java.nio.ByteBuffer;
import java.security.DigestException;

/**
 * @author solo6975
 * @since 2022/2/13 17:44
 */
public interface MessageAuthenticCode {
	String getAlgorithm();

	int getDigestLength();

	default void init(byte[] key) {init(key,0,key.length);}
	void init(byte[] b, int off, int len);
	void reset();

	void update(byte b);
	default void update(byte[] b) {update(b, 0, b.length);}
	void update(byte[] b, int off, int len);
	void update(ByteBuffer buf);
	default void update(DynByteBuf b) {
		if (b.hasArray()) update(b.array(), b.arrayOffset() + b.rIndex, b.readableBytes());
		else update(b.nioBuffer());
	}

	byte[] digest();
	int digest(byte[] b, int off, int len) throws DigestException;
	default byte[] digestShared() {return digest();}
}
