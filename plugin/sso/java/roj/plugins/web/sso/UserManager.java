package roj.plugins.web.sso;

/**
 * @author Roj234
 * @since 2024/7/8 7:04
 */
public interface UserManager {
	 User getUserById(int uid);
	 User getUserByName(String user);
	 User createUser(String name);
	 void setDirty(User user, String... field);
	 default void save() {}
}