package roj.plugins.http.sso;

import roj.config.Tokenizer;
import roj.config.serial.ToJson;
import roj.crypt.Base64;
import roj.crypt.HMAC;
import roj.io.IOUtil;
import roj.net.http.Cookie;
import roj.net.http.IllegalRequestException;
import roj.net.http.server.Request;
import roj.net.http.server.Response;
import roj.net.http.server.ResponseHeader;
import roj.net.http.server.auto.GET;
import roj.net.http.server.auto.Interceptor;
import roj.net.http.server.auto.Mime;
import roj.net.http.server.auto.POST;
import roj.text.CharList;
import roj.text.Escape;
import roj.text.TextUtil;
import roj.util.ByteList;

import java.net.InetSocketAddress;
import java.security.MessageDigest;
import java.util.Collections;

/**
 * @author Roj234
 * @since 2024/7/8 0008 4:50
 */
public final class SSO {
	private static final long TOKEN_EXPIRE = 86400_000L * 7;

	public String siteName;
	public boolean disableRegister;

	private final String pathRel;
	private final UserManager manager;
	//目前来说，这两个key只是初始化为0，即便如此，也有足够的安全性
	//不过openIdKey在用到时（真的用得到么？）还是需要持久化的
	private final byte[] randomKey = new byte[32], openIdKey = new byte[32];

	public SSO(String path, UserManager man) {pathRel = path;manager = man;}

	@Interceptor
	public Object loginRedirect(Request req) throws IllegalRequestException {
		var token = req.cookie().get("xsso_token");
		if (token != null) {
			if (verifyToken(req, token.value(), "USER_LOGIN") != null) return null;
			req.responseHeader().sendCookieToClient(Collections.singletonList(new Cookie("xsso_token").expires(-1)));
		}

		String string = "/"+req.absolutePath() + (!req.query().isEmpty() ? "?" + req.query() : "");
		req.responseHeader().put("Location", "/"+pathRel+"?return="+Escape.encodeURIComponent(string));
		throw new IllegalRequestException(302, Response.EMPTY);
	}

	@GET
	@Mime("application/json")
	@Interceptor("login")
	public String checkLogin(Request req) {
		User u = (User) req.localCtx().get("xsso:user");
		return "{\"ok\":true,\"name\":\""+Tokenizer.addSlashes(u.name)+"\",\"token\":\""+makeToken(u, "USER_ACCESS", 300000L, LocalData.get(req), IOUtil.SharedCoder.get())+"\"}";
	}

	@GET
	@Mime("application/json")
	public String commonJs(Request req) {req.responseHeader().put("cache-control", "max-age=86400");return "const siteName=\""+(siteName != null ? Tokenizer.addSlashes(siteName) : "X-SSO")+"\";";}

	@GET
	public void logout(Request req, ResponseHeader rh) {
		req.responseHeader().sendCookieToClient(Collections.singletonList(new Cookie("xsso_token").expires(-1)));
		rh.code(302).header("location", "/"+pathRel);
	}

	@POST
	@Mime("application/json")
	public Object login(Request req, String user, String pass, boolean is_token) throws IllegalRequestException {
		if (user.length() < 3 || user.length() > 31 || pass.length() < 6 || (!is_token && pass.length() > 31))
			throw new IllegalRequestException(400);

		var o = LocalData.get(req);
		User u = manager.getUserByName(user);
		block:
		if (u != null) {
			if (is_token) {
				if (verifyToken(req, pass, "USER_REFRESH") == null) break block;
				return loginSuccess(o, u, req, true);
			}

			long time = System.currentTimeMillis();

			// 一小时密码错误5次
			if (time > u.suspendTimer) u.loginAttempt = 0;
			else {
				u.suspendTimer = time + 3600_000;
				manager.setDirty(u, "suspendTimer");
			}
			if (++u.loginAttempt >= 5) return "{\"ok\":false,\"msg\":\"你的账户已被锁定\"}";

			if (o.hasher.compare(u.passHash, getUTFBytes(pass)))
				return loginSuccess(o, u, req, false);

			if (u.totpKey != null) {
				String totp = TOTP.makeTOTP(o.hmac, u.totpKey, time);
				if (totp.equals(pass)) return loginSuccess(o, u, req, false);
			}

			manager.setDirty(u, "loginAttempt");
		}

		return "{\"ok\":false,\"msg\":\"用户名或密码错误\"}";
	}

	@POST
	@Mime("application/json")
	public Object register(Request req, String user, String pass) throws IllegalRequestException {
		if (disableRegister) return "{\"ok\":false,\"msg\":\"注册功能已关闭\"}";

		if (user.length() < 3 || user.length() > 31 || pass.length() < 6 || pass.length() > 31)
			throw new IllegalRequestException(400);

		User u = manager.createUser(user);
		u.passHash = LocalData.get(req).hasher.hash(getUTFBytes(pass));
		u.registerTime = System.currentTimeMillis();
		u.registerAddr = (InetSocketAddress) req.connection().remoteAddress();
		manager.setDirty(u, "passHash", "registerTime", "registerAddr");
		return "{\"ok\":true}";
	}

	@GET
	@Mime("application/json")
	@Interceptor("login")
	public String makeTotp(Request req) {
		User u = (User) req.localCtx().get("xsso:user");
		byte[] key = new byte[20];
		LocalData.get(req).srnd.nextBytes(key);
		return "{\"ok\":true,\"url\":\""+TOTP.makeURL(key, u.name, siteName != null ? siteName : req.host())+"\",\"secret\":\""+TextUtil.bytes2hex(key)+"\"}";
	}

	@POST
	@Mime("application/json")
	@Interceptor("login")
	public Object bindTotp(Request req, String secret, String code) throws IllegalRequestException {
		User u = (User) req.localCtx().get("xsso:user");
		if (u.totpKey != null) return "{\"ok\":false,\"msg\":\"参数错误\"}";

		try {
			byte[] key = TextUtil.hex2bytes(secret);
			if (key.length != 20) return "{\"ok\":false,\"msg\":\"参数错误\"}";
			String code1 = TOTP.makeTOTP(LocalData.get(req).hmac, key, System.currentTimeMillis());
			if (!code1.equals(code)) return "{\"ok\":false,\"msg\":\"验证码错误\"}";

			u.totpKey = key;
			manager.setDirty(u, "totpKey");
		} catch (Exception e) {
			throw new IllegalRequestException(400);
		}

		return "{\"ok\":true}";
	}

	@POST
	@Mime("application/json")
	@Interceptor("login")
	public String changePassword(Request req, String pass) throws IllegalRequestException {
		if (pass.length() < 6 || pass.length() > 31) throw new IllegalRequestException(400);

		User u = (User) req.localCtx().get("xsso:user");

		var v = req.cookie().get("xsso_token").value();
		int pos1 = v.indexOf('.');
		int pos2 = v.indexOf('.', pos1+1);

		long time = System.currentTimeMillis() + TOKEN_EXPIRE - Long.parseLong(v.substring(pos1 + 1, pos2));
		if (time < 180000) {
			u.passHash = LocalData.get(req).hasher.hash(getUTFBytes(pass));
			manager.setDirty(u, "passHash");
			return "{\"ok\":true,\"msg\":\"操作成功\"}";
		} else {
			return "{\"ok\":false,\"msg\":\"已登录"+time/1000+"秒\"}";
		}
	}

	// 返回 url和timeout
	@GET
	@Mime("application/json")
	public String qrcode(Request req) {return "{\"ok\":false,\"msg\":\"请在配置文件中开启该功能\"}";}

	@GET
	@Mime("application/json")
	public String loginCheck(Request req, String accessKey, String userToken) {
		User u = verifyToken(req, userToken, "USER_ACCESS");
		if (u == null) return "{\"ok\":false,\"msg\":\"userToken校验失败\"}";

		var sc = IOUtil.SharedCoder.get();
		var o = LocalData.get(req);
		o.hmac.setSignKey(openIdKey);
		sc.byteBuf.clear();
		o.hmac.update(sc.byteBuf.putInt(u.id));

		var ser = new ToJson();
		ser.valueMap();
		ser.key("ok");
		ser.value(true);
		ser.key("openid");
		ser.value(sc.wrap(o.hmac.digestShared()).hex());
		ser.key("name");
		ser.value(u.name);
		ser.key("permissions");
		ser.valueList();
		for (var permission : u.permissions) ser.value(permission);

		return ser.getValue().toStringAndFree();
	}

	@Interceptor("login")
	public String loginInterceptor(Request req) throws IllegalRequestException {
		var token = req.cookie().get("xsso_token");
		if (token != null) {
			User u = verifyToken(req, token.value(), "USER_LOGIN");
			req.localCtx().put("xsso:user", u);

			if (u != null) return null;
		}

		return "{\"ok\":false,\"msg\":\"未登录\"}";
	}

	@POST
	@Mime("application/json")
	@Interceptor("login")
	public String admin__unsuspend(Request req, String user) {
		User u = (User) req.localCtx().get("xsso:user");
		if (!u.isAdmin()) return "{\"ok\":false,\"msg\":\"权限不足\"}";

		u = manager.getUserByName(user);
		if (u != null) {
			u.loginTime = 0;
			return "{\"ok\":true,\"msg\":\"操作成功\"}";
		}

		return "{\"ok\":false,\"msg\":\"用户不存在\"}";
	}

	@POST
	@Mime("application/json")
	@Interceptor("login")
	public String admin__changePassword(Request req, String user, String pass) {
		User u = (User) req.localCtx().get("xsso:user");
		if (!u.isAdmin()) return "{\"ok\":false,\"msg\":\"权限不足\"}";

		u = manager.getUserByName(user);
		if (u != null) {
			if (u.isAdmin()) return "{\"ok\":false,\"msg\":\"权限不足\"}";

			u.passHash = LocalData.get(req).hasher.hash(getUTFBytes(pass));
			manager.setDirty(u, "passHash");
			return "{\"ok\":true,\"msg\":\"密码已重置\"}";
		}

		return "{\"ok\":false,\"msg\":\"用户不存在\"}";
	}

	@POST
	@Mime("application/json")
	@Interceptor("login")
	public String admin__deleteTotp(Request req, String user, String pass) {
		User u = (User) req.localCtx().get("xsso:user");
		if (!u.isAdmin()) return "{\"ok\":false,\"msg\":\"权限不足\"}";

		u = manager.getUserByName(user);
		if (u != null) {
			if (u.isAdmin()) return "{\"ok\":false,\"msg\":\"权限不足\"}";

			u.totpKey = null;
			manager.setDirty(u, "totpKey");
			return "{\"ok\":true,\"msg\":\"OTP已解绑\"}";
		}

		return "{\"ok\":false,\"msg\":\"用户不存在\"}";
	}

	private static byte[] getUTFBytes(String pass) {return IOUtil.getSharedByteBuf().putUTFData(pass).toByteArray();}

	private String loginSuccess(LocalData o, User u, Request req, boolean is_refresh) {
		var sc = IOUtil.SharedCoder.get();
		ByteList bb = sc.byteBuf; bb.clear();

		u.loginAttempt = 0;
		u.loginTime = System.currentTimeMillis();
		u.loginAddr = (InetSocketAddress) req.connection().remoteAddress();
		manager.setDirty(u, "loginAttempt", "loginTime", "loginAddr");
		long expire = TOKEN_EXPIRE + (is_refresh ? -180000 : 0);

		var login_token = makeToken(u, "USER_LOGIN", expire, o, sc);
		req.responseHeader().sendCookieToClient(Collections.singletonList(new Cookie("xsso_token", login_token).expires(0).httpOnly(true).sameSite("Strict")));

		var refresh_token = makeToken(u, "USER_REFRESH", 86400_000L * 30, o, sc);
		var access_token = makeToken(u, "USER_ACCESS", 300000L, o, sc);

		return "{\"ok\":true,\"msg\":\"登录成功\",\"token\":\""+access_token+"\",\"refresh_token\":\""+refresh_token+"\",\"hasTotp\":"+(u.totpKey!=null)+"}";
	}
	private String makeToken(User u, String usage, long expire, LocalData o, IOUtil sc) {
		ByteList bb = sc.byteBuf; bb.clear();

		byte[] nonce = new byte[8];
		o.srnd.nextBytes(nonce);

		expire += System.currentTimeMillis();
		HMAC hmac = o.hmac;
		hmac.setSignKey(randomKey);
		hmac.update(bb.put(nonce).putAscii(usage).putInt(u.id).putLong(expire).putAscii(u.passHash));

		bb.clear();
		bb.putVUInt(u.id).putVULong(expire).put(nonce).put(hmac.digestShared());

		CharList sb = sc.charBuf; sb.clear();
		return Base64.encode(bb, sb, Base64.B64_URL_SAFE).toString();
	}

	private User verifyToken(Request req, String v, String usage) {
		// for future SHA-256 checksum
		if (v.length() < 40 || v.length() > 72) return null;

		try {
			var sc = IOUtil.SharedCoder.get();
			ByteList buf = sc.byteBuf; buf.clear();
			Base64.decode(v, buf, Base64.B64_URL_SAFE_REV);

			int uid = buf.readVUInt();
			long expire = buf.readVULong();
			byte[] nonce = buf.readBytes(8);
			byte[] hash = buf.readBytes(buf.readableBytes());

			if (System.currentTimeMillis() < expire) {
				User u = manager.getUserById(uid);
				if (u != null) {
					var o = LocalData.get(req);

					HMAC hmac = o.hmac;
					hmac.setSignKey(randomKey);
					hmac.update(buf.put(nonce).putAscii(usage).putInt(u.id).putLong(expire).putAscii(u.passHash));

					if (MessageDigest.isEqual(hmac.digestShared(), hash)) return u;
				}
			}
		} catch (Exception ignored) {}
		return null;
	}
}