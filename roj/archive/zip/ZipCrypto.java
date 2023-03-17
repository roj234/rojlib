package roj.archive.zip;

import roj.crypt.CRCAny;
import roj.crypt.CipheR;
import roj.util.DynByteBuf;

import javax.crypto.ShortBufferException;
import java.security.GeneralSecurityException;

/**
 * @implNote ZipCrypto has known vulnerability
 * @author Roj234
 * @since 2022/11/12 0012 1:45
 */
public final class ZipCrypto implements CipheR {
	int key0,key1,key2;
	public byte mode;

	@Override
	public int getMaxKeySize() {
		return 0;
	}

	@Override
	public void setKey(byte[] key, int flags) {
		int key0 = 0x12345678;
		int key1 = 0x23456789;
		int key2 = 0x34567890;

		for(byte b : key) {
			key0 = CRCAny.CRC_32.update(key0, b);
			key1 = key1 + (key0 & 0xFF);
			key1 = key1 * 134775813 + 1;
			key2 = CRCAny.CRC_32.update(key2, (byte) (key1 >>> 24));
		}

		this.key0 = key0;
		this.key1 = key1;
		this.key2 = key2;
		this.mode = (byte) (flags & CipheR.DECRYPT);
	}

	@Override
	public void crypt(DynByteBuf in, DynByteBuf out) throws GeneralSecurityException {
		if (out.writableBytes() < in.readableBytes()) throw new ShortBufferException();

		int key0 = this.key0;
		int key1 = this.key1;
		int key2 = this.key2;
		boolean encrypt = mode==0;

		while (in.isReadable()) {
			int inByte = in.readUnsignedByte();

			int tmp = key2 | 2;
			int outByte = inByte ^ (tmp * (tmp ^ 1) >>> 8);

			out.put((byte) outByte);

			// OFB ?
			key0 = CRCAny.CRC_32.update(key0, encrypt?inByte:outByte);
			key1 = key1 + (key0 & 0xFF);
			key1 = key1 * 134775813 + 1;
			key2 = CRCAny.CRC_32.update(key2, (byte) (key1 >>> 24));
		}

		this.key0 = key0;
		this.key1 = key1;
		this.key2 = key2;
	}

	public void copyState(int[] arr, boolean restore) {
		if (restore) {
			this.key0 = arr[0];
			this.key1 = arr[1];
			this.key2 = arr[2];
		} else {
			arr[0] = this.key0;
			arr[1] = this.key1;
			arr[2] = this.key2;
		}
	}
}
