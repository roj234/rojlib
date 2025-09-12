package roj.plugins.web.sso;

import roj.collect.ArrayList;
import roj.collect.HashMap;
import roj.config.ConfigMaster;
import roj.config.mapper.ObjectMapperFactory;
import roj.io.IOUtil;
import roj.text.ParseException;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;

/**
 * @author Roj234
 * @since 2024/7/8 7:04
 */
class JsonUserManager implements UserManager {
	private final File json;

	private List<User> users;
	private Map<String, User> userByName = new HashMap<>();
	private boolean dirty;

	private static final ObjectMapperFactory SERIALIZER = ObjectMapperFactory.getInstance();
	static {
		SERIALIZER.registerAdapter(InetSocketAddress.class, new Object() {
			public InetSocketAddress readCallback(String addr) throws UnknownHostException {
				int i = addr.indexOf(':');
				return new InetSocketAddress(InetAddress.getByName(addr.substring(0, i)), Integer.parseInt(addr.substring(i+1)));
			}
			public String writeCallback(InetSocketAddress addr) {return addr.getAddress().getHostAddress()+":"+addr.getPort();}
		}).enableAsBase64();
	}

	public JsonUserManager(File json) throws IOException, ParseException {
		this.json = json;
		this.users = !json.isFile() ? new ArrayList<>() : ConfigMaster.JSON.readObject(SERIALIZER.listOf(User.class), json);
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
			dirty = true;
			return;
		}

		doSave();
	}

	public void save() {if (dirty) doSave();}

	private void doSave() {
		ArrayList<User> copy;
		synchronized (this) {
			if (!dirty) return;
			copy = new ArrayList<>(users);
			dirty = false;
		}

		try {
			IOUtil.writeFileEvenMoreSafe(json.getParentFile(), json.getName(), file -> {
				ConfigMaster.JSON.writeObject(SERIALIZER.listOf(User.class), copy, file);
			});
		} catch (Exception e) {
			e.printStackTrace();
			dirty = true;
		}
	}
}