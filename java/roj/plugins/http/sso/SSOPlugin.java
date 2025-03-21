package roj.plugins.http.sso;

import roj.collect.CollectionX;
import roj.collect.XHashSet;
import roj.config.Tokenizer;
import roj.config.data.CEntry;
import roj.config.serial.ToJson;
import roj.crypt.Base64;
import roj.crypt.MySaltedHash;
import roj.http.Cookie;
import roj.http.IllegalRequestException;
import roj.http.server.Request;
import roj.http.server.Response;
import roj.http.server.ResponseHeader;
import roj.http.server.ZipRouter;
import roj.http.server.auto.*;
import roj.io.IOUtil;
import roj.plugin.Plugin;
import roj.text.CharList;
import roj.text.DateParser;
import roj.text.Escape;
import roj.text.TextUtil;
import roj.ui.Argument;
import roj.ui.Terminal;
import roj.util.ByteList;
import roj.util.Helpers;
import roj.util.TypedKey;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static roj.http.IllegalRequestException.BAD_REQUEST;
import static roj.ui.CommandNode.argument;
import static roj.ui.CommandNode.literal;

/**
 * @author Roj234
 * @since 2024/7/9 0009 8:27
 */
public class SSOPlugin extends Plugin {
	private static final XHashSet.Shape<String, Group> shape = XHashSet.noCreation(Group.class, "name");
	public static final String COOKIE_ID = "xsso_token";

	private static final Pattern PERMISSION_NODE_PATTERN = Pattern.compile("(!)?(grant|revoke) ((?:[^/]+?/)*.+?)(?: (\\d+))?( noinherit)?");
	private final XHashSet<String, Group> groups = shape.create();
	private void loadGroups() {
		groups.clear();
		for (var entry : getConfig().getMap("groups").entrySet()) {
			var group = new Group(entry.getKey());
			Matcher m = PERMISSION_NODE_PATTERN.matcher("");
			for (CEntry item : entry.getValue().asList()) {
				if (!m.reset(item.asString()).matches()) {
					getLogger().warn("权限定义 {} 不符合规则 {}", item, PERMISSION_NODE_PATTERN);
				} else {
					boolean isDelete = m.group(2).equals("revoke");
					int value;
					if (m.group(4) == null) value = isDelete ? 0 : 1;
					else {
						value = Integer.parseInt(m.group(4));
						if (isDelete) value = ~value; // for bitset only ??? might be weird for users
					}
					group.add(m.group(3).equals("*") ? "" : m.group(3), value, m.group(1) != null, m.group(5) == null);
				}
			}
			groups.add(group);
		}
	}

	private UserManager users;

	private static final long LOGIN_TTL = 86400_000L * 7;
	private static final int ACCESS_TOKEN_TTL = 180000;

	private String sitePath, siteName;
	private boolean register;

	//目前来说，这两个key只是初始化为0，即便如此，也有足够的安全性
	//不过openIdKey在用到时（真的用得到么？）还是需要持久化的
	private final byte[] sessionKey = new byte[32], openIdKey = new byte[32];

	@Override
	protected void reloadConfig() {
		super.reloadConfig();

		var cfg = getConfig();
		sitePath = cfg.getString("path", "sso");
		//ensure directory match
		if (!sitePath.endsWith("/")) sitePath = sitePath.concat("/");
		siteName = cfg.getString("name");
		register = cfg.getBool("register");
		var skStr = cfg.getString("session_key", null);
		if (skStr != null) {
			byte[] sk = skStr.getBytes(StandardCharsets.UTF_8);
			System.arraycopy(sk, 0, sessionKey, 0, Math.min(sk.length, 32));
		}
		loadGroups();

		try {
			onDisable();
			users = cfg.getString("storage", "file").equals("file")
				? new JsonUserManager(new File(getDataFolder(), "users.json"))
				: new DbUserManager("users");
		} catch (Exception e) {
			throw new IllegalStateException("无法创建用户管理器", e);
		}
	}

	@Override
	protected void onLoad() {
		reloadConfig();

		var resource = new ZipRouter(getDescription().getArchive(), "web/");
		var router = new OKRouter().register(this).addPrefixDelegation("", resource);
		registerRoute(sitePath, router);
		registerInterceptor("PermissionManager", router.getInterceptor("PermissionManager"));
	}

	@Override
	protected void onEnable() throws Exception {
		var userNameList = users instanceof JsonUserManager jum ? Argument.suggest(CollectionX.toMap(jum.getUserSet().keySet())) : Argument.string();

		var c = literal("easysso");
		c.then(literal("setpasswd").then(argument("用户名", userNameList).then(argument("密码", Argument.string()).executes(ctx -> {
			 var name = ctx.argument("用户名", String.class);
			 var pass = ctx.argument("密码", String.class);
			 if (name.length() < 2 || name.length() > 31) {Terminal.warning("用户名长度要在2-31个字符之间");return;}
			 if (pass.length() < 6 || pass.length() > 99) {Terminal.warning("密码长度要在6-99个字符之间");return;}
			 var u = users.getUserByName(name);
			 boolean create = u == null;
			 if (create) u = users.createUser(name);

			 u.passHash = MySaltedHash.hasher(new SecureRandom()).hash(pass.getBytes(StandardCharsets.UTF_8));
			 users.setDirty(u, "passHash");
			 System.out.println(create?"已创建新用户":"密码已更新");
		 }))))
		 .then(literal("setgroup").then(argument("用户名", userNameList).then(argument("用户组", Argument.string()).executes(ctx -> {
			 var u = users.getUserByName(ctx.argument("用户名", String.class));
			 if (u == null) {Terminal.warning("账户不存在");return;}

			 var g = groups.get(ctx.argument("用户组", String.class));
			 if (g == null) {Terminal.warning("用户组不存在");return;}

			 u.groupName = g.name;
			 u.group = g;

			 users.setDirty(u, "group");
			 System.out.println("用户组已更新");
		 }))))
		 .then(literal("reload").executes(ctx -> {
			 reloadConfig();
			 System.out.println("配置文件已重载");
		 }))
		 .then(literal("userinfo").then(argument("用户名", userNameList).executes(ctx -> {
			 User u = users.getUserByName(ctx.argument("用户名", String.class));
			 if (u == null) {Terminal.warning("账户不存在");return;}

			 System.out.println("用户ID: "+u.id);
			 System.out.println("已绑定OTP: "+(u.totpKey != null));
			 System.out.println("用户组: "+u.groupName);
			 System.out.println("密码错误次数: "+u.loginAttempt);
			 System.out.println("上次登录: "+DateParser.toLocalTimeString(u.loginTime));
			 System.out.println("登录IP: "+u.loginAddr);
			 System.out.println("注册时间: "+DateParser.toLocalTimeString(u.registerTime));
			 System.out.println("注册IP: "+u.registerAddr);
		 })))
		 .then(literal("suspend").then(argument("用户名", userNameList).then(argument("timeout", Argument.number(30, Integer.MAX_VALUE)).executes(ctx -> {
			 User u = users.getUserByName(ctx.argument("用户名", String.class));
			 if (u == null) {Terminal.warning("账户不存在");return;}

			 u.suspendTimer = System.currentTimeMillis() + ctx.argument("timeout", Integer.class)*1000L;
			 u.loginAttempt = 99;
			 u.tokenSeq++;
			 System.out.println("账户已锁定至"+DateParser.toLocalTimeString(u.suspendTimer));
		 }))))
		 .then(literal("unsuspend").then(argument("用户名", userNameList).executes(ctx -> {
			 User u = users.getUserByName(ctx.argument("用户名", String.class));
			 if (u == null) {Terminal.warning("账户不存在");return;}

			 u.suspendTimer = 0;
			 System.out.println("账户已解锁");
		 })))
		 .then(literal("unbindotp").then(argument("用户名", userNameList).executes(ctx -> {
			 User u = users.getUserByName(ctx.argument("用户名", String.class));
			 if (u == null) {Terminal.warning("账户不存在");return;}

			 u.totpKey = null;
			 users.setDirty(u, "totpKey");
		 })))
		 .then(literal("genotp").then(argument("用户名", userNameList).executes(ctx -> {
			 User u = users.getUserByName(ctx.argument("用户名", String.class));
			 if (u == null) {Terminal.warning("账户不存在");return;}

			 u.tempOtp = TextUtil.bytes2hex(new SecureRandom().generateSeed(8));
			 u.tokenSeq++;
			 System.out.println("一次有效的临时密码:"+u.tempOtp);
		 })))
		 .then(literal("expirepass").then(argument("用户名", userNameList).executes(ctx -> {
			 User u = users.getUserByName(ctx.argument("用户名", String.class));
			 if (u == null) {Terminal.warning("账户不存在");return;}

			 u.tempOtp = "\0PASSWORDEXPIRED\0";
			 System.out.println("密码已过期(该状态服务器重启后失效)");
		 })))
		 .then(literal("maketoken").then(argument("用户名", userNameList).then(argument("过期时间", Argument.number(60, 86400)).executes(ctx -> {
			 User u = users.getUserByName(ctx.argument("用户名", String.class));
			 if (u == null) {Terminal.warning("账户不存在");return;}

			 System.out.print("[L]ogin or [A]ccess: ");
			 char c1 = Terminal.readChar("LA");

			 System.out.println(makeToken(u, c1, ctx.argument("过期时间", Integer.class) * 1000L, new LocalData(), IOUtil.SharedCoder.get()));
		 }))))
		 .then(literal("save").executes(ctx -> {
			 users.save();
		 }));
		registerCommand(c);
	}

	@Override
	protected void onDisable() {
		if (users != null) users.save();
	}

	@Override
	public <T> T ipc(TypedKey<T> key, Object parameter) {
		return key.cast(switch (key.name) {
			default -> super.ipc(key, parameter);

			case "getDefaultPermissions" -> groups.get("guest");
			case "authenticateUser" -> verifyToken(null, parameter.toString(), 'A');
			case "getUser" -> {
				var req = (Request) parameter;

				Cookie token = null;
				try {
					token = req.cookie().get(COOKIE_ID);
				} catch (IllegalRequestException e) {
					Helpers.athrow(e);
				}

				if (token != null) {
					User user = verifyToken(req, token.value(), 'L');
					if (user == null) {
						req.responseHeader().sendCookieToClient(Collections.singletonList(new Cookie(COOKIE_ID).expires(-1)));
					}

					yield user;
				}

				yield null;
			}
		});
	}

	@Interceptor("PermissionManager")
	public Response ssoPermissionManager(Request req) throws IllegalRequestException {
		String postfix = "";
		var token = req.cookie().get(COOKIE_ID);

		String permissionNode = "web/".concat(req.absolutePath());

		checkLogin:
		if (token != null) {
			User user = verifyToken(req, token.value(), 'L');
			if (user != null) {
				var group = user.group;
				if (group == null) throw new IllegalRequestException(500, Response.internalError("用户组配置错误"));
				if (group.isAdmin() || group.has(permissionNode)) return null;

				postfix = "#denied";
				break checkLogin;
			}
			req.responseHeader().sendCookieToClient(Collections.singletonList(new Cookie(COOKIE_ID).expires(-1)));
		}

		var group = groups.get("guest");
		if (group != null && group.has(permissionNode)) return null;

		var url = IOUtil.getSharedCharBuf().append('/').append(req.absolutePath()).append(req.query().isEmpty() ? "" : "?"+req.query());
		req.responseHeader().put("Location", "/"+sitePath+"?return="+Escape.encodeURIComponent(url)+postfix);
		throw new IllegalRequestException(302);
	}

	@GET
	@Mime("application/json")
	@Interceptor("login")
	public String checkLogin(Request req) {
		User u = (User) req.localCtx().get("xsso:user");
		return "{\"ok\":true,\"name\":\""+Tokenizer.addSlashes(u.name)+"\",\"token\":\""+makeToken(u, 'A', ACCESS_TOKEN_TTL, LocalData.get(req), IOUtil.SharedCoder.get())+"\"}";
	}

	@GET
	@Mime("text/javascript")
	public String commonJs(Request req) {req.responseHeader().put("cache-control", "max-age=86400");return "const siteName=\""+(siteName != null ? Tokenizer.addSlashes(siteName) : "X-SSO")+"\";";}

	@GET
	public void logout(Request req, ResponseHeader rh) {
		req.responseHeader().sendCookieToClient(Collections.singletonList(new Cookie(COOKIE_ID).path("/").expires(-1)));
		rh.code(302).header("location", "/"+sitePath);
	}

	@POST
	@Mime("application/json")
	public String login(Request req, String user, String pass, boolean is_token) throws IllegalRequestException {
		if (user.length() < 2 || user.length() > 31 || pass.length() < 6 || pass.length() > 99) throw BAD_REQUEST;

		var o = LocalData.get(req);
		User u = users.getUserByName(user);
		if (u != null) {
			if (is_token) {
				if (verifyToken(req, pass, 'R') == null)
					return "{\"ok\":false,\"msg\":\"登录已过期，请重新输入密码\"}";
				getLogger().info("用户{}在{}使用{}登录成功", user, req.proxyRemoteAddress(), "refresh_token");
				return loginSuccess(o, u, req, true);
			}

			long time = System.currentTimeMillis();

			// 一小时密码错误5次
			if (time > u.suspendTimer) u.loginAttempt = 0;
			else {
				u.suspendTimer = time + 3600_000;
				users.setDirty(u, "suspendTimer");
			}
			if (++u.loginAttempt >= 5) return "{\"ok\":false,\"msg\":\"你的账户已被锁定\"}";

			if (pass.equals(u.tempOtp) || o.hasher.compare(u.passHash, getUTFBytes(pass))) {
				getLogger().info("用户{}在{}使用{}登录成功", user, req.proxyRemoteAddress(), "password");
				return loginSuccess(o, u, req, false);
			}

			if (u.totpKey != null) {
				String totp = TOTP.makeTOTP(o.hmac, u.totpKey, time);
				if (totp.equals(pass)) {
					getLogger().info("用户{}在{}使用{}登录成功", user, req.proxyRemoteAddress(), "totp");
					return loginSuccess(o, u, req, false);
				}
			}

			users.setDirty(u, "loginAttempt");
			getLogger().info("用户{}在{}登录失败", user, req.proxyRemoteAddress());
		}

		return "{\"ok\":false,\"msg\":\"用户名或密码错误\"}";
	}

	@POST
	@Mime("application/json")
	public String register(Request req, String user, String pass) throws IllegalRequestException {
		if (!register) return "{\"ok\":false,\"msg\":\"注册功能已关闭\"}";

		if (user.length() < 2 || user.length() > 31 || pass.length() < 6 || pass.length() > 99) throw BAD_REQUEST;

		User u = users.createUser(user);
		u.passHash = LocalData.get(req).hasher.hash(getUTFBytes(pass));
		u.registerTime = System.currentTimeMillis();
		u.registerAddr = req.proxyRemoteAddress();
		users.setDirty(u, "passHash", "registerTime", "registerAddr");
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
	public String bindTotp(Request req, String secret, String code) throws IllegalRequestException {
		User u = (User) req.localCtx().get("xsso:user");
		if (u.totpKey != null) throw BAD_REQUEST;

		try {
			byte[] key = TextUtil.hex2bytes(secret);
			if (key.length != 20) throw BAD_REQUEST;
			String code1 = TOTP.makeTOTP(LocalData.get(req).hmac, key, System.currentTimeMillis());
			if (!code1.equals(code)) return "{\"ok\":false,\"msg\":\"验证码错误\"}";

			u.totpKey = key;
			users.setDirty(u, "totpKey");
		} catch (Exception e) {
			throw BAD_REQUEST;
		}

		return "{\"ok\":true}";
	}

	@POST
	@Mime("application/json")
	@Interceptor("login")
	public String changePassword(Request req, String pass) throws IllegalRequestException {
		if (pass.length() < 6 || pass.length() > 99) throw BAD_REQUEST;

		var token = req.cookie().get(COOKIE_ID).value();

		ByteList buf = IOUtil.getSharedByteBuf();
		Base64.decode(token, buf, Base64.B64_URL_SAFE_REV);

		buf.readVUInt();
		long expire = buf.readVULong();

		long time = System.currentTimeMillis() + LOGIN_TTL - expire;
		if (time < 180000) {
			var u = (User) req.localCtx().get("xsso:user");
			u.passHash = LocalData.get(req).hasher.hash(getUTFBytes(pass));
			users.setDirty(u, "passHash");
			return "{\"ok\":true,\"msg\":\"操作成功\"}";
		} else {
			return "{\"ok\":false,\"msg\":\"您登录超过3分钟("+time/1000+"秒)\"}";
		}
	}

	// 返回 url和timeout
	@GET
	@Mime("application/json")
	public String qrcode(Request req) {return "{\"ok\":false,\"msg\":\"请在配置文件中开启该功能\"}";}

	@GET
	public Response loginCheck(Request req, String accessKey, String userToken) {
		User u = verifyToken(req, userToken, 'A');
		if (u == null) return Response.json("{\"ok\":false,\"msg\":\"token校验失败\"}");

		if (!u.accessNonceUsed.add(Long.hashCode(LocalData.get(req).localNonce)))
			return Response.json("{\"ok\":false,\"msg\":\"token已被使用\"}");
		long time = System.currentTimeMillis();
		if (time - u.accessNonceTime > ACCESS_TOKEN_TTL) {
			u.accessNonceUsed.clear();
			u.accessNonceTime = time;
		}

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
		return Response.json(ser.getValue());
	}

	@Interceptor("login")
	public String loginInterceptor(Request req) throws IllegalRequestException {
		var token = req.cookie().get(COOKIE_ID);
		if (token != null) {
			User u = verifyToken(req, token.value(), 'L');
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

		u = users.getUserByName(user);
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

		u = users.getUserByName(user);
		if (u != null) {
			if (u.isAdmin()) return "{\"ok\":false,\"msg\":\"权限不足\"}";

			u.passHash = LocalData.get(req).hasher.hash(getUTFBytes(pass));
			users.setDirty(u, "passHash");
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

		u = users.getUserByName(user);
		if (u != null) {
			if (u.isAdmin()) return "{\"ok\":false,\"msg\":\"权限不足\"}";

			u.totpKey = null;
			users.setDirty(u, "totpKey");
			return "{\"ok\":true,\"msg\":\"OTP已解绑\"}";
		}

		return "{\"ok\":false,\"msg\":\"用户不存在\"}";
	}

	private static byte[] getUTFBytes(String pass) {return IOUtil.getSharedByteBuf().putUTFData(pass).toByteArray();}

	private String loginSuccess(LocalData o, User u, Request req, boolean is_refresh) {
		var sc = IOUtil.SharedCoder.get();
		ByteList bb = sc.byteBuf; bb.clear();

		var expired = "\0PASSWORDEXPIRED\0".equals(u.tempOtp);
		u.tempOtp = null;
		u.loginAttempt = 0;
		u.loginTime = System.currentTimeMillis();
		u.loginAddr = req.proxyRemoteAddress();

		//这个功能暂时不开放给前端
		//u.tokenNonce = o.srnd.nextLong();
		//让之前所有的refresh token失效(多设备登录怎么处理？？先只保留冻结功能好了)

		users.setDirty(u, "loginAttempt", "loginTime", "loginAddr", "refreshNonce");
		long expire = LOGIN_TTL + (is_refresh ? -180000 : 0);

		var login_token = makeToken(u, 'L', expire, o, sc);
		req.responseHeader().sendCookieToClient(Collections.singletonList(new Cookie(COOKIE_ID, login_token).path("/").secure(req.isSecure()).expires(0).httpOnly(true).sameSite("Strict")));

		var refresh_token = makeToken(u, 'R', 86400_000L * 30, o, sc);
		var access_token = makeToken(u, 'A', ACCESS_TOKEN_TTL, o, sc);

		var sb = new CharList().append("{\"ok\":true,\"msg\":\"登录成功\",\"token\":\"").append(access_token).append("\",\"refreshToken\":\"").append(refresh_token).append('"');
		if (u.totpKey != null) sb.append(",\"hasTotp\":true");
		if (expired) sb.append(",\"expired\":true");
		return sb.append('}').toStringAndFree();
	}
	private String makeToken(User u, char usage, long expire, LocalData o, IOUtil sc) {
		ByteList buf = sc.byteBuf; buf.clear();

		byte[] nonce = o.tmpNonce;
		o.srnd.nextBytes(nonce);

		expire += System.currentTimeMillis();
		var hmac = o.hmac;
		hmac.setSignKey(sessionKey);
		hmac.update(buf.putInt(u.id).putLong(expire).put(nonce)
					  .put(usage).putLong(u.tokenSeq)
					  .putAscii(u.passHash));

		buf.clear();
		buf.putVUInt(u.id).putVULong(expire).put(nonce).put(hmac.digestShared());

		CharList sb = sc.charBuf; sb.clear();
		return Base64.encode(buf, sb, Base64.B64_URL_SAFE).toString();
	}

	public User verifyToken(Request req, String v, char usage) {
		// for future SHA-256 checksum
		if (v.length() < 40 || v.length() > 72) return null;

		try {
			var buf = IOUtil.getSharedByteBuf();
			Base64.decode(v, buf, Base64.B64_URL_SAFE_REV);

			int uid = buf.readVUInt();
			long expire = buf.readVULong();
			var nonce = buf.readLong();
			byte[] hash = buf.readBytes(buf.readableBytes());

			if (System.currentTimeMillis() < expire) {
				User u = users.getUserById(uid);
				if (u != null) {
					var o = LocalData.get(req);

					var hmac = o.hmac;
					hmac.setSignKey(sessionKey);
					hmac.update(buf.putInt(u.id).putLong(expire).putLong(nonce)
								   .put(usage).putLong(u.tokenSeq)
								   .putAscii(u.passHash));
					if (usage == 'A') o.localNonce = nonce;

					if (MessageDigest.isEqual(hmac.digestShared(), hash)) {
						if (u.group == null) {
							var group = u.group = groups.get(u.groupName);
							if (group == null) getLogger().warn("找不到用户组{}", u.groupName);
						}

						return u;
					}
				}
			}
		} catch (Exception ignored) {}
		return null;
	}
}