package roj.plugins.http.sso;

import roj.collect.IntSet;
import roj.config.auto.As;
import roj.config.auto.Optional;

import java.net.InetSocketAddress;

/**
 * @author Roj234
 * @since 2024/7/8 0008 6:37
 */
public class User {
	public int id;

	public String name;
	// 一次有效的临时密码
	transient String tempOtp;
	String passHash;
	@Optional
	@As("base64")
	byte[] totpKey;

	transient byte loginAttempt;
	transient long suspendTimer;

	//在需要的时候让所有token失效而无需修改密码（“紧急冻结”）
	//未来应该是需要落盘的
	transient long tokenSeq;
	transient final IntSet accessNonceUsed = new IntSet();
	transient long accessNonceTime;

	@Optional
	InetSocketAddress registerAddr, loginAddr;
	@Optional
	long registerTime, loginTime;

	@Optional
	public String group = "default";
	public transient UserGroup groupInst;
	public boolean isAdmin() {return id == 0 || groupInst.isAdmin();}
}