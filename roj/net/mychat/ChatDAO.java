package roj.net.mychat;

import roj.collect.IntMap;
import roj.collect.LinkedMyHashMap;
import roj.crypt.Base64;
import roj.crypt.SM3;
import roj.io.BoxFile;
import roj.io.IOUtil;
import roj.math.MutableInt;
import roj.net.http.srv.HttpServer11;
import roj.text.UTFCoder;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Collections;
import java.util.List;

/**
 * @author Roj233
 * @since 2022/3/11 20:28
 */
public class ChatDAO {
	private final BoxFile users;
	private final BoxFile space;
	private final IntMap<BoxFile> userHistory;

	private final RandomAccessFile userIdx;
	private final LinkedMyHashMap<String, StUser> userIdxMap;

	public ChatDAO(File basePath) throws IOException {
		space = new BoxFile(new File(basePath, "space.bin"));
		users = new BoxFile(new File(basePath, "user.bin"));
		userIdx = new RandomAccessFile(new File(basePath, "userIndex.bin"), "rw");
		userIdxMap = new LinkedMyHashMap<>();
		loadUserIndex();
		userHistory = new IntMap<>(100);
	}

	private void loadUserIndex() throws IOException {
		RandomAccessFile r = userIdx;
		if (r.length() == 0) {
			r.setLength(8);
			r.seek(0);
			r.writeInt(ByteList.FOURCC("UIDX"));
			r.writeInt(0);
		}
		r.seek(0);
		if (r.readInt() != ByteList.FOURCC("UIDX")) {
			throw new IOException("UserIndex corrupt");
		}

		UTFCoder uc = IOUtil.SharedCoder.get();
		uc.byteBuf.ensureCapacity(64);
		byte[] tmp = uc.byteBuf.list;

		int i = 0, len = r.readInt();
		userIdxMap.ensureCapacity(len);
		while (i++ < len) {
			r.readFully(tmp, 0, 64);
			StUser u = new StUser();
			System.arraycopy(tmp, 48, u.pass, 0, 16);
			u.state = tmp[47];
			u.id = i;

			int j = 0;
			while (j < 47 && tmp[j] != 0) j++;
			uc.byteBuf.wIndex(j);

			String name = uc.decode();
			userIdxMap.put(name, u);
		}
	}

	private static final class StUser {
		byte state;
		int id;
		byte[] pass = new byte[16];
	}

	public Result register(String name, String pass) {
		if (userIdxMap.containsKey(name)) return Result.err("用户已存在");
		if (DynByteBuf.byteCountUTF8(name) > 47) return Result.err("用户名过长(超过47字节)");

		UTFCoder uc = IOUtil.SharedCoder.get();

		ByteList bb = uc.byteBuf;
		bb.ensureCapacity(64);
		uc.encodeR(name);

		int i = bb.wIndex();
		while (i < 48) {
			bb.list[i++] = 0;
		}
		bb.wIndex(48);

		Base64.decode(pass, 0, pass.length(), bb, Base64.B64_URL_SAFE_REV);

		if (bb.wIndex() != 64) return Result.err("数据格式有误");

		StUser u = new StUser();
		System.arraycopy(bb.list, 48, u.pass, 0, 16);

		synchronized (userIdxMap) {
			if (null != userIdxMap.putIfAbsent(name, u)) {
				return Result.err("用户已存在");
			}

			try {
				userIdx.seek(4);
				int count = userIdx.readInt();
				// 几乎不可能
				if (count == Integer.MAX_VALUE) return Result.err("用户数量超出上限");

				u.id = userIdxMap.size();
				userIdx.seek(userIdx.length());
				userIdx.write(bb.list, 0, 64);

				userIdx.seek(4);
				userIdx.writeInt(count + 1);
			} catch (IOException e) {
				return Result.err("磁盘空间不足");
			}
			return Result.suc(u.id);
		}
	}

	public Result login(String name, String pass, String challenge) {
		StUser user = userIdxMap.get(name);
		if (user == null) return Result.err("用户名或密码错误");

		UTFCoder uc = IOUtil.SharedCoder.get();
		ByteList bb = uc.byteBuf;

		SM3 sm3 = (SM3) HttpServer11.TSO.get().ctx.get("SM3");
		sm3.update(user.pass);
		sm3.update(uc.decodeBase64R(challenge, Base64.B64_URL_SAFE_REV).list, 0, bb.wIndex());

		byte[] a = sm3.digest();
		byte[] b = uc.decodeBase64R(pass).list;
		if (bb.wIndex() != a.length) return Result.err("数据格式错误");

		int diff = 0;
		for (int i = 0; i < a.length; i++) {
			diff |= a[i] ^ b[i];
		}

		if (diff != 0) return Result.err("用户名或密码错误");
		if (user.state != 0) return Result.err("用户被禁用");
		return Result.suc(user.id);
	}

	public Result changePassword(int uid, String oldPass, String newPass) {
		StUser user = userIdxMap.valueAt(uid - 1);
		if (user == null) return Result.err("用户不存在");
		if (oldPass != null) {
			int diff = comparePassword(user, oldPass);
			if (diff != 0) return Result.err("密码错误");
		}
		if (newPass != null) {
			UTFCoder uc = IOUtil.SharedCoder.get();

			ByteList bb = uc.wrap(user.pass);
			bb.clear();
			Base64.decode(newPass, 0, newPass.length(), bb, Base64.B64_URL_SAFE_REV);
			if (bb.wIndex() < 16) return Result.err("新密码格式有误");

			try {
				userIdx.seek(-56L + 64 * user.id + 48);
				userIdx.write(bb.list, 0, 16);
			} catch (IOException e) {
				return Result.err("磁盘空间不足");
			}
		}
		return Result.suc(user.id);
	}

	public Result setUserState(int user, int state) {
		return Result.err("Not implemented");
	}

	private static int comparePassword(StUser user, String pass) {
		if (pass.isEmpty()) return 1;

		UTFCoder uc = IOUtil.SharedCoder.get();
		ByteList bb = uc.decodeBase64R(pass, Base64.B64_URL_SAFE_REV);
		if (bb.length() != 16) return 1;

		// '常数时间'
		int diff = 0;
		for (int i = 0; i < 16; i++) {
			diff |= user.pass[i] ^ bb.list[i];
		}
		return diff;
	}

	public User obtainUserData(int uid) {
		throw new UnsupportedOperationException("Not implemented yet");
	}

	public Result setUserData(User user) {
		return Result.err("Not implemented");
	}

	public Group obtainGroupData(int uid) {
		throw new UnsupportedOperationException("Not implemented yet");
	}

	public Result setGroupData(Group group) {
		return Result.err("Not implemented");
	}

	public Integer getHistoryCount(int uid) {
		return 0;
	}

	public List<Message> getHistory(int uid, CharSequence filter, int off, int len, MutableInt total) {
		return Collections.emptyList();
	}

	public Result addHistory(int uid, Message msg) {
		return Result.err("Not implemented");
	}

	public Result delHistory(int uid) {
		return Result.err("Not implemented");
	}

	public Result delHistory(int uid, int targetId) {
		return Result.err("Not implemented");
	}

	public Integer getSpaceCount() {
		return 0;
	}

	public Integer getSpaceCount(int uid) {
		return 0;
	}

	public List<SpaceEntry> getSpace(Integer uid, int off, int len) {
		return Collections.emptyList();
	}

	public Result addSpace(int uid, CharSequence msg) {
		return Result.err("Not implemented");
	}

	public static final class Result {
		public int uid;
		public String error;

		public static Result err(String s) {
			Result r = new Result();
			r.error = s;
			return r;
		}

		public static Result suc(int id) {
			Result r = new Result();
			r.uid = id;
			return r;
		}
	}
}
