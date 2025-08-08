package roj.plugins.bkcrack;

/**
 * @author Roj234
 * @since 2022/11/12 17:25
 */
final class Cipher {
	int x, y, z;

	Cipher() {}

	Cipher(byte[] key) {
		int x = 0x12345678;
		int y = 0x23456789;
		int z = 0x34567890;

		for (byte b : key) {
			x = CrcTable.crc32(x, b);
			y = (y + (x & 0xFF)) * MulTable.MULT + 1;
			z = CrcTable.crc32(z, y >>> 24);
		}

		this.x = x;this.y = y;this.z = z;
	}

	public Cipher set() {
		x = 0x12345678;
		y = 0x23456789;
		z = 0x34567890;
		return this;
	}

	public Cipher copy(Cipher c) {
		return c.set(x,y,z);
	}

	Cipher set(int a, int b, int c) {
		x = a; y = b; z = c;
		return this;
	}

	/// Forward: plain text
	void update(int p) {
		x = CrcTable.crc32(x, p);
		y = (y + (x & 0xFF)) * MulTable.MULT + 1;
		z = CrcTable.crc32(z, y >>> 24);
	}

	void update(byte[] b, int from, int to) {
		int x = this.x;
		int y = this.y;
		int z = this.z;

		while (from < to) {
			x = CrcTable.crc32(x, b[from++] ^ KeyTable.getByte(z));
			y = (y + (x & 0xFF)) * MulTable.MULT + 1;
			z = CrcTable.crc32(z, y >>> 24);
		}

		this.x = x;this.y = y;this.z = z;
	}

	void updateBackward(int p) {
		z = CrcTable.crc32inv(z, y >>> 24);
		y = (y - 1) * MulTable.MULTINV - (x & 0xFF);
		x = CrcTable.crc32inv(x, p ^ keystream());
	}

	void updateBackwardPlain(int p) {
		z = CrcTable.crc32inv(z, y >>> 24);
		y = (y - 1) * MulTable.MULTINV - (x & 0xFF);
		x = CrcTable.crc32inv(x, p);
	}

	void updateBackward(byte[] b, int from, int to) {
		int x = this.x;
		int y = this.y;
		int z = this.z;

		for (int i = from-1; i >= to; i--) {
			z = CrcTable.crc32inv(z, y >>> 24);
			y = (y - 1) * MulTable.MULTINV - (x & 0xFF);
			x = CrcTable.crc32inv(x, b[i] ^ KeyTable.getByte(z));
		}

		this.x = x; this.y = y; this.z = z;
	}

	byte keystream() { return KeyTable.getByte(z); }

	@Override
	public String toString() {
		return "ZCP{" + Integer.toHexString(x) + " " + Integer.toHexString(y) + " " + Integer.toHexString(z) + '}';
	}

	public int[] keys() {
		return new int[] {x,y,z};
	}
}