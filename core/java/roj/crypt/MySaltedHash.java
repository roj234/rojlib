package roj.crypt;

import roj.text.CharList;
import roj.text.TextUtil;
import roj.util.ByteList;
import roj.util.DirectByteList;
import roj.util.DynByteBuf;

import java.security.SecureRandom;
import java.util.List;
import java.util.Objects;

/**
 * @author Roj234
 * @since 2024/2/2 3:59
 */
public final class MySaltedHash {
	private final SecureRandom srnd;
	private final byte cost, length, saltLength;
	private final Blake3 hasher = new Blake3(64);
	private final ByteList ctmp = new ByteList(64);

	private MySaltedHash(SecureRandom srnd, int cost, int length, int saltLength) {
		this.srnd = srnd;
		this.cost = (byte) cost;
		this.length = (byte) length;
		this.saltLength = (byte) saltLength;
	}

	public static MySaltedHash hasher(SecureRandom srnd) {return new MySaltedHash(Objects.requireNonNull(srnd), 16, 32, 16);}
	public static MySaltedHash hasher(SecureRandom srnd, int cost, int length, int saltLength) {
		if (length < 16 || length > 64) throw new IllegalArgumentException("length not in [16,64]");
		if (cost < 0 || cost > 20) throw new IllegalArgumentException("cost not in [0,20]");
		if (saltLength < 4 || saltLength > 32) throw new IllegalArgumentException("saltLength not in [4,32]");
		return new MySaltedHash(Objects.requireNonNull(srnd), cost, length, saltLength);
	}
	public static boolean staticCompare(CharSequence str, byte[] pass) { return new MySaltedHash(null, 0, 0, 0).compare(str, pass); }

	public String hash(byte[] pass) {
		byte[] salt = new byte[32];
		srnd.nextBytes(salt);
		for (int i = saltLength; i < 32; i++) salt[i] = 0;

		var tmp = rawHash(pass, salt, cost, 20-cost);

		CharList sb = new CharList().append("$b3$").append(cost);
		Base64.encode(ByteList.wrap(salt, 0, saltLength), sb.append('$'), Base64.B64_URL_SAFE);
		Base64.encode(tmp.slice(length), sb.append('$'), Base64.B64_URL_SAFE);

		tmp.release();
		return sb.toStringAndFree();
	}

	public boolean compare(CharSequence str, byte[] pass) {
		List<String> hash1 = TextUtil.split(str, '$');
		if (hash1.size() != 5 || !hash1.get(1).equals("b3")) throw new IllegalArgumentException("unsupported MAC "+hash1.get(1));
		int cost = TextUtil.parseInt(hash1.get(2));
		if (cost < 0 || cost > 20) return false;

		var tmp = ctmp; tmp.clear();
		Base64.decode(hash1.get(3), tmp, Base64.B64_URL_SAFE_REV).putZero(32 - tmp.wIndex());
		var myHash = rawHash(pass, tmp.toByteArray(), cost, 20-cost);

		tmp.clear();
		var dbHash = Base64.decode(hash1.get(4), tmp, Base64.B64_URL_SAFE_REV);

		myHash.wIndex(length);
		boolean equals = myHash.equals(dbHash);
		myHash.release();
		return equals;
	}

	// may use Argon2id
	private DynByteBuf rawHash(byte[] pass, byte[] salt, int cpuCost, int memCost) {
		Blake3 b3 = hasher;
		b3.init(salt);

		var tmp = DynByteBuf.allocateDirect(64 + (memCost <<= 8));
		int rounds = cpuCost << 8;
		while (--rounds != 0) {
			b3.update(pass);
			b3.update(tmp);
			tmp.clear();
			b3.engineDigest(b3.buf, tmp);
			b3.getMoreDigest(tmp, memCost);
			b3.engineReset();
		}

		return tmp;
	}
}