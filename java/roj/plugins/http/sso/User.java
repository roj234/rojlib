package roj.plugins.http.sso;

import roj.collect.MyHashSet;
import roj.config.auto.As;
import roj.config.auto.Optional;

import java.net.InetSocketAddress;
import java.util.Set;

/**
 * @author Roj234
 * @since 2024/7/8 0008 6:37
 */
public class User {
	public int id;

	public String name;
	String passHash;
	@Optional
	@As("base64")
	byte[] totpKey;

	transient byte loginAttempt;
	transient long suspendTimer;

	@Optional
	InetSocketAddress registerAddr, loginAddr;
	@Optional
	long registerTime, loginTime;

	public Set<String> permissions = new MyHashSet<>();
	public boolean isAdmin() {return permissions.contains("*");}
}