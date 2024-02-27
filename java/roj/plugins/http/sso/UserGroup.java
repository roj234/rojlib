package roj.plugins.http.sso;

import roj.collect.TrieTreeSet;

/**
 * @author Roj234
 * @since 2024/7/22 0022 4:21
 */
public final class UserGroup {
	public final String name;
	public TrieTreeSet permissions = new TrieTreeSet();
	private UserGroup _next;

	public UserGroup(String name) {this.name = name;}

	public boolean isAdmin() {return permissions.contains("*");}
}