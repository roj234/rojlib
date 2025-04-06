package roj.plugins.web.sso;

import roj.collect.IntMap;
import roj.collect.MyHashMap;
import roj.config.data.CMap;
import roj.sql.DBA;
import roj.text.TextUtil;
import roj.util.Helpers;

import java.sql.SQLException;
import java.util.Map;

/**
 * @author Roj234
 * @since 2024/7/8 0008 7:04
 */
class DbUserManager implements UserManager {
	private final String userTable;
	private IntMap<User> users = new IntMap<>();
	private Map<String, User> userByName = new MyHashMap<>();

	public DbUserManager(CMap table) {userTable = table.getString("table");}

	@Override
	public User getUserById(int uid) {
		User cached = users.get(uid);
		if (cached != null) {
			try (var dba = DBA.getInstance()) {
				var data = dba.table(userTable).where("id", uid).select().nextMap();
				if (data == null) return null;

				return addFromDb(data);
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}

		return cached;
	}

	private User addFromDb(Map<String, String> data) {
		User user = new User();
		user.name = data.get("name");
		user.id = Integer.parseInt(data.get("id"));
		user.passHash = data.get("passHash");
		user.totpKey = TextUtil.hex2bytes(data.get("totpKey"));
		//user.permissions = data.get("");

		synchronized (this) {
			users.putInt(user.id, user);
			userByName.put(user.name, user);
		}

		return user;
	}

	@Override
	public User getUserByName(String user) {
		User cached = userByName.get(user);
		if (cached == null) {
			try (var dba = DBA.getInstance()) {
				var data = dba.table(userTable).where("name", user).select().nextMap();
				if (data == null) return null;
				return addFromDb(data);
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
		return cached;
	}

	@Override
	public User createUser(String name) {
		try (var dba = DBA.getInstance()) {
			var ok = dba.table(userTable).field("name").insert(name);
			if (!ok) throw new IllegalStateException("already exist?");

			int id = dba.affected_id();

			User user = new User();
			user.id = id;
			user.name = name;
			return user;
		} catch (SQLException e) {
			Helpers.athrow(e);
			return null;
		}
	}

	@Override
	public void setDirty(User user, String... field) {
		try (var dba = DBA.getInstance()) {
			dba.table(userTable).where("id", user.id).fields(field).update("value");
		} catch (SQLException e) {
			Helpers.athrow(e);
		}
	}
}