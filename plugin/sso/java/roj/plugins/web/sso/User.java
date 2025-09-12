package roj.plugins.web.sso;

import roj.collect.IntSet;
import roj.config.mapper.As;
import roj.config.mapper.Name;
import roj.config.mapper.Optional;
import roj.plugin.PermissionHolder;

import java.net.InetSocketAddress;

/**
 * @author Roj234
 * @since 2024/7/8 6:37
 */
final class User implements PermissionHolder {
	int id;

	String name;
	// 一次有效的临时密码
	transient String tempOtp;
	String passHash;
	@Optional @As("base64")byte[] totpKey;

	transient byte loginAttempt;
	transient long suspendTimer;

	//在需要的时候让所有token失效而无需修改密码（“紧急冻结”）
	//未来应该是需要落盘的
	transient long tokenSeq;
	transient final IntSet accessNonceUsed = new IntSet();
	transient long accessNonceTime;

	@Optional InetSocketAddress registerAddr, loginAddr;
	@Optional long registerTime, loginTime;

	@Optional @Name("group") String groupName = "default";
	transient Group group;

	boolean isAdmin() {return id == 0 || group.isAdmin();}

	@Override public int getId() {return id;}
	@Override public String getName() {return name;}
	@Override public String getGroupName() {return groupName;}
	@Override public boolean hasPermission(String permission) {return group.has(permission);}
	@Override public int getPermissionFlags(String permission) {return group.getBits(permission);}
}