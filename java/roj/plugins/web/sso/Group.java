package roj.plugins.web.sso;

import roj.plugin.PermissionHolder;
import roj.util.PermissionSet;

/**
 * @author Roj234
 * @since 2024/7/22 0022 4:21
 */
final class Group extends PermissionSet implements PermissionHolder {
	final String name;
	String desc;

	private Group _next;

	public Group(String name) {this.name = name;}
	@Override
	public String toString() {return desc == null ? name : name+": "+desc;}

	boolean isAdmin() {return get("", 0) != 0;}

	//继承？
	@Override
	public int get(CharSequence node, int def) {
		return super.get(node, def);
	}

	@Override public int getId() {return -1;}
	@Override public String getName() {return "unknown";}
	@Override public String getGroupName() {return name;}
	@Override public int getPermissionFlags(String permission) {return get(permission, 0);}
}