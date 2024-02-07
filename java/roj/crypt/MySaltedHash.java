package roj.crypt;

import roj.text.CharList;
import roj.text.TextUtil;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.security.SecureRandom;
import java.util.List;

/**
 * @author Roj234
 * @since 2024/2/2 0002 3:59
 */
public class MySaltedHash {
	private final SecureRandom srnd;
	private final byte cost, length;
	private final Blake3 hasher = new Blake3(64);
	private final ByteList ctmp = new ByteList();

	public MySaltedHash(SecureRandom srnd, int cost, int length) {
		this.srnd = srnd;
		this.cost = (byte) cost;
		this.length = (byte) length;
	}

	public static MySaltedHash hasher(SecureRandom srnd, int cost, int length) {
		if (length < 16 || length > 64) throw new IllegalArgumentException("length should in [16,64]");
		if (cost < 0 || cost > 31) throw new IllegalArgumentException("cost should in [0,31]");
		return new MySaltedHash(srnd, cost, length);
	}
	public static boolean staticCompare(CharSequence str, byte[] pass) { return hasher(null, 0, 16).compare(str, pass); }

	public String hash(byte[] pass) {
		byte[] salt = srnd.generateSeed(32);

		ByteList tmp = rawHash(pass, salt, cost);

		CharList sb = new CharList().append("$b3$").append(cost);
		Base64.encode(ByteList.wrap(salt), sb.append('$'));
		Base64.encode(tmp.slice(length), sb.append('$'));

		tmp._free();
		return sb.toStringAndFree();
	}

	public boolean compare(CharSequence str, byte[] pass) {
		List<String> hash1 = TextUtil.split(str, '$');
		if (!hash1.get(0).equals("b3")) throw new IllegalArgumentException("unsupported MAC "+hash1.get(0));
		int cost = TextUtil.parseInt(hash1.get(2));

		ctmp.clear();
		byte[] salt = Base64.decode(hash1.get(3), ctmp).toByteArray();
		ByteList myHash = rawHash(pass, salt, cost);

		ctmp.clear();
		DynByteBuf dbHash = Base64.decode(hash1.get(4), ctmp);

		boolean equals = myHash.equals(dbHash);
		myHash._free();
		return equals;
	}

	private ByteList rawHash(byte[] pass, byte[] salt, int cost) {
		Blake3 b3 = hasher;
		ByteList tmp = b3.buf;

		b3.setSignKey(salt);
		b3.update(pass);
		b3.digest(tmp);
		tmp.wIndex(64);

		int rounds = 1 << cost;
		while (--rounds != 0) {
			b3.engineUpdateBlock(tmp);
			b3.engineDigest(ByteList.EMPTY, tmp);
			b3.engineReset();
		}

		return tmp;
	}
}