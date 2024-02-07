package roj.plugins.mychat;

import roj.collect.LRUCache;
import roj.io.IOUtil;
import roj.math.MutableInt;
import roj.sql.DBA;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author Roj233
 * @since 2022/3/11 20:28
 */
public class ChatDAO {
	private final LRUCache<String, User> cachedUser;

	public ChatDAO(File basePath) throws IOException {
		cachedUser = new LRUCache<>(1000, 10);
	}

	public Result register(String name, String pass) {
		if (getUserByName(name) != null) return Result.err("用户已存在");

		DBA dba = DBA.getInstance();
		try {
			dba.table("user").field("Name, Pass").insert(Arrays.asList(name, pass));
		} catch (SQLException e) {
			return Result.err("系统繁忙");
		}
		return Result.suc(dba.affected_id());
	}

	public User getUserByName(String name) {
		User cu = cachedUser.get(name);
		if (cu == null) {
			cu = new User();
			cu.name = name;
			cachedUser.put(name, cu);

			try (DBA dba = DBA.getInstance().table("user").field("Id,Pass").where("Name", name).limit(1).select()) {
				List<String> rs = dba.getone();
				if (rs != null) {
					cu.id = Integer.parseInt(rs.get(0));
					cu.pass = IOUtil.SharedCoder.get().decodeBase64(rs.get(1));
				}
			} catch (SQLException ignored) {}
		}
		return cu;
	}

	public Result changePassword(int uid, String oldPass, String newPass) {
		try {
			int cnt = DBA.getInstance().table("user").where(uid).andWhere("Pass", oldPass)
						 .field("Pass").update(Collections.singletonList(newPass));
			if (cnt <= 0) return Result.err("密码错误");
		} catch (SQLException e) {
			return Result.err("系统繁忙");
		}
		return Result.suc(uid);
	}

	public Result setUserState(int user, int state) {
		return Result.err("Not implemented");
	}

	public User getUser(int uid) {
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
