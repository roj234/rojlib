package roj.plugins.http.sso;

import roj.collect.MyHashMap;
import roj.collect.SimpleList;
import roj.config.ConfigMaster;
import roj.config.ParseException;
import roj.config.auto.SerializerFactory;
import roj.config.auto.Serializers;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;

/**
 * @author Roj234
 * @since 2024/7/8 0008 7:04
 */
public class JsonUserManager implements UserManager {
	private final File json;

	private List<User> users;
	private Map<String, User> userByName = new MyHashMap<>();

	private static final SerializerFactory SERIALIZER = SerializerFactory.getInstance();
	static {
		SERIALIZER.add(InetSocketAddress.class, new Object() {
			public InetSocketAddress readCallback(String addr) throws UnknownHostException {
				int i = addr.indexOf(':');
				return new InetSocketAddress(InetAddress.getByName(addr.substring(0, i)), Integer.parseInt(addr.substring(i+1)));
			}
			public String writeCallback(InetSocketAddress addr) {return addr.getAddress().getHostAddress()+":"+addr.getPort();}
		});
		Serializers.registerAsBase64(SERIALIZER);
	}

	public JsonUserManager(File json) throws IOException, ParseException {
		this.json = json;
		this.users = json.length() == 0 ? new SimpleList<>() : ConfigMaster.JSON.readObject(SERIALIZER.listOf(User.class), json);
		for (User user : users) userByName.put(user.name, user);
	}

	@Override public User getUserById(int uid) {return uid < 0 || uid >= users.size() ? null : users.get(uid);}
	@Override public User getUserByName(String user) {return userByName.get(user);}
	public Map<String, User> getUserSet() {return userByName;}

	@Override
	public User createUser(String name) {
		User user = new User();
		user.id = users.size();
		user.name = name;

		users.add(user);
		userByName.put(name, user);
		return user;
	}

	@Override
	public void setDirty(User user, String... field) {
		block: {
			for (String s : field) {
				if (s.equals("passHash") || s.equals("totpKey") || s.equals("group")) break block;
			}
			return;
		}

		try {
			ConfigMaster.JSON.writeObject(SERIALIZER.listOf(User.class), users, json);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}