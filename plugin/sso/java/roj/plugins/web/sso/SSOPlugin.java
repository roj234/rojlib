package roj.plugins.web.sso;

import roj.collect.CollectionX;
import roj.collect.XashMap;
import roj.config.JsonSerializer;
import roj.config.node.ConfigValue;
import roj.crypt.Base64;
import roj.crypt.MySaltedHash;
import roj.http.Cookie;
import roj.http.server.*;
import roj.http.server.auto.*;
import roj.io.IOUtil;
import roj.plugin.Plugin;
import roj.text.*;
import roj.ui.Argument;
import roj.ui.TUI;
import roj.ui.Tty;
import roj.util.ByteList;
import roj.util.Helpers;
import roj.util.TypedKey;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static roj.http.server.IllegalRequestException.BAD_REQUEST;
import static roj.ui.CommandNode.argument;
import static roj.ui.CommandNode.literal;

/**
 * @author Roj234
 * @since 2024/7/9 8:27
 */
@Mime("application/json")
public class SSOPlugin extends Plugin {
	private static final XashMap.Builder<String, Group> BUILDER = XashMap.noCreation(Group.class, "name");
	public static final String COOKIE_ID = "xsso_token";

	private static final Pattern PERMISSION_NODE_PATTERN = Pattern.compile("(!)?(grant|revoke) ((?:[^/]+?/)*.+?)(?: (\\d+))?( noinherit)?");
	private final XashMap<String, Group> groups = BUILDER.create();
	private void loadGroups() {
		groups.clear();
		for (var entry : getConfig().getMap("groups").entrySet()) {
			var group = new Group(entry.getKey());
			Matcher m = PERMISSION_NODE_PATTERN.matcher("");
			for (ConfigValue item : entry.getValue().asList()) {
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

	private String sitePath, siteName;

	//目前来说，这两个key只是初始化为0，即便如此，也有足够的安全性
	//不过openIdKey在用到时（真的用得到么？）还是需要持久化的
	private final byte[] sessionKey = new byte[32], openIdKey = new byte[32];

	private final long tempKey = System.nanoTime() ^ ((long) System.identityHashCode(this) << 32);
	// 注意，这个单位是毫秒
	private static final int QRCODE_TTL = 120000;

	private static long getUnixSecond() {return System.currentTimeMillis() / 1000;}
	private static final int OPENAPI_TOKEN_TTL = 300; // 5 min
	private int accessTokenTTL = 86400 * 7;
	private int refreshTokenTTL = 86400 * 31;
	private boolean accessTokenNoStore;

	@Override
	protected void reloadConfig() {
		super.reloadConfig();

		var cfg = getConfig();
		cfg.dot(true);

		sitePath = cfg.getString("site.path", "sso");
		//ensure directory match
		if (!sitePath.endsWith("/")) sitePath = sitePath.concat("/");
		siteName = cfg.getString("site.name");

		var secret = cfg.getString("accessToken.secret", null);
		if (secret != null) {
			byte[] sk = secret.getBytes(StandardCharsets.UTF_8);
			System.arraycopy(sk, 0, sessionKey, 0, Math.min(sk.length, 32));
		}
		loadGroups();

		accessTokenTTL = cfg.getInt("accessToken.TTL", 86400*7);
		accessTokenNoStore = cfg.getBool("accessToken.sessionOnly", true);

		refreshTokenTTL = cfg.getInt("refreshToken.TTL", 86400*31);

		try {
			onDisable();
			users = cfg.getString("user.storage", "file").equals("file")
				? new JsonUserManager(new File(getDataFolder(), "users.json"))
				: new DbUserManager(cfg.getMap("user"));
		} catch (Exception e) {
			throw new IllegalStateException("无法创建用户管理器", e);
		}
	}

	@Override
	protected void onLoad() {
		reloadConfig();

		var resource = new ZipRouter(getDescription().getArchive(), "web/");
		var router = new OKRouter().register(this).addPrefixDelegation("/", resource);
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
			 if (name.length() < 2 || name.length() > 31) {
				 Tty.warning("用户名长度要在2-31个字符之间");return;}
			 if (pass.length() < 6 || pass.length() > 99) {
				 Tty.warning("密码长度要在6-99个字符之间");return;}
			 var u = users.getUserByName(name);
			 boolean create = u == null;
			 if (create) u = users.createUser(name);

			 u.passHash = MySaltedHash.hasher(new SecureRandom()).hash(pass.getBytes(StandardCharsets.UTF_8));
			 users.setDirty(u, "passHash");
			 System.out.println(create?"已创建新用户":"密码已更新");
		 }))))
		 .then(literal("setgroup").then(argument("用户名", userNameList).then(argument("用户组", Argument.string()).executes(ctx -> {
			 var u = users.getUserByName(ctx.argument("用户名", String.class));
			 if (u == null) {
				 Tty.warning("账户不存在");return;}

			 var g = groups.get(ctx.argument("用户组", String.class));
			 if (g == null) {
				 Tty.warning("用户组不存在");return;}

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
			 if (u == null) {
				 Tty.warning("账户不存在");return;}

			 System.out.println("用户ID: "+u.id);
			 System.out.println("已绑定OTP: "+(u.totpKey != null));
			 System.out.println("用户组: "+u.groupName);
			 System.out.println("密码错误次数: "+u.loginAttempt);
			 System.out.println("上次登录: "+ DateFormat.toLocalDateTime(u.loginTime));
			 System.out.println("登录IP: "+u.loginAddr);
			 System.out.println("注册时间: "+ DateFormat.toLocalDateTime(u.registerTime));
			 System.out.println("注册IP: "+u.registerAddr);
		 })))
		 .then(literal("suspend").then(argument("用户名", userNameList).then(argument("timeout", Argument.number(30, Integer.MAX_VALUE)).executes(ctx -> {
			 User u = users.getUserByName(ctx.argument("用户名", String.class));
			 if (u == null) {
				 Tty.warning("账户不存在");return;}

			 u.suspendTimer = System.currentTimeMillis() + ctx.argument("timeout", Integer.class)*1000L;
			 u.loginAttempt = 99;
			 u.tokenSeq++;
			 System.out.println("账户已锁定至"+ DateFormat.toLocalDateTime(u.suspendTimer));
		 }))))
		 .then(literal("unsuspend").then(argument("用户名", userNameList).executes(ctx -> {
			 User u = users.getUserByName(ctx.argument("用户名", String.class));
			 if (u == null) {
				 Tty.warning("账户不存在");return;}

			 u.suspendTimer = 0;
			 System.out.println("账户已解锁");
		 })))
		 .then(literal("unbindotp").then(argument("用户名", userNameList).executes(ctx -> {
			 User u = users.getUserByName(ctx.argument("用户名", String.class));
			 if (u == null) {
				 Tty.warning("账户不存在");return;}

			 u.totpKey = null;
			 users.setDirty(u, "totpKey");
		 })))
		 .then(literal("genotp").then(argument("用户名", userNameList).executes(ctx -> {
			 User u = users.getUserByName(ctx.argument("用户名", String.class));
			 if (u == null) {
				 Tty.warning("账户不存在");return;}

			 u.tempOtp = TextUtil.bytes2hex(new SecureRandom().generateSeed(8));
			 u.tokenSeq++;
			 System.out.println("一次有效的临时密码:"+u.tempOtp);
		 })))
		 .then(literal("expirepass").then(argument("用户名", userNameList).executes(ctx -> {
			 User u = users.getUserByName(ctx.argument("用户名", String.class));
			 if (u == null) {
				 Tty.warning("账户不存在");return;}

			 u.tempOtp = "\0PASSWORDEXPIRED\0";
			 System.out.println("密码已过期(该状态服务器重启后失效)");
		 })))
		 .then(literal("maketoken").then(argument("用户名", userNameList).then(argument("过期时间", Argument.number(60, 86400)).executes(ctx -> {
			 User u = users.getUserByName(ctx.argument("用户名", String.class));
			 if (u == null) {
				 Tty.warning("账户不存在");return;}

			 System.out.print("[L]ogin or [A]ccess: ");
			 char c1 = TUI.key("LA");

			 System.out.println(makeToken(u, c1, ctx.argument("过期时间", Integer.class) * 1000L, new LocalData()));
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
				yield getUserFromRequest(req);
			}
		});
	}

	private User getUserFromRequest(Request req) {
		var token = req.bearerAuthorization();
		if (token != null) return verifyToken(req, token, 'L');

		Cookie cookie = null;
		try {
			cookie = req.cookie().get(COOKIE_ID);
		} catch (IllegalRequestException e) {
			Helpers.athrow(e);
		}

		if (cookie != null) {
			User user = verifyToken(req, cookie.value(), 'L');
			if (user != null) return user;

			cookie.path("/").expires(-1);
		}

		return null;
	}

	@Interceptor(value = "PermissionManager", global = true)
	public Content checkURLPermission(Request req) {
		String postfix = "";
		String permissionNode = "web/".concat(req.absolutePath());
		var user = getUserFromRequest(req);
		if (user != null) {
			var group = user.group;
			if (group == null) return req.server().code(500).cast(Content.internalError("用户组配置错误"));
			if (group.isAdmin() || group.has(permissionNode)) return null;

			postfix = "#denied";
		}

		var group = groups.get("guest");
		if (group != null && group.has(permissionNode)) return null;

		return Content.redirect(req, "/"+sitePath+"?return="+ URICoder.encodeURIComponent(req.getURL())+postfix);
	}

	//region Request handler
	@GET
	@Interceptor("login")
	public String checkLogin(Request req) {
		User u = (User) req.threadLocal().get("xsso:user");
		return "{\"ok\":true,\"name\":\""+Tokenizer.escape(u.name)+"\",\"token\":\""+makeToken(u, 'A', OPENAPI_TOKEN_TTL, LocalData.get(req))+"\"}";
	}

	@GET
	@Mime("text/javascript")
	public String commonJs(Request req) {req.responseHeader().put("cache-control", "max-age=86400");return "const siteName=\""+(siteName != null ? Tokenizer.escape(siteName) : "X-SSO")+"\";";}

	@GET
	public void logout(Request req, ResponseHeader rh) {
		req.sendCookieToClient(Collections.singletonList(new Cookie(COOKIE_ID).path("/").expires(-1)));
		rh.code(302).header("location", "/"+sitePath);
	}

	@POST
	public String login(Request req, String user, String pass, boolean is_token) throws IllegalRequestException {
		if (user.length() < 2 || user.length() > 31 || pass.length() < 6 || pass.length() > 99) throw BAD_REQUEST;

		var o = LocalData.get(req);
		if (is_token) {
			User u = verifyToken(req, pass, 'R');
			if (u != null) {
				getLogger().info("用户{}在{}刷新令牌登录成功", user, req.proxyRemoteAddress());
				return loginSuccess(o, u, req, true);
			}
			if (pass.length() > 39) return "{\"ok\":false,\"msg\":\"登录已过期，请重新输入密码\"}";
		}

		User u = users.getUserByName(user);
		if (u != null) {
			long time = System.currentTimeMillis();

			// 10分钟内密码最多错误5次
			if (time > u.suspendTimer) u.loginAttempt = 0;
			else {
				u.suspendTimer = time + 600000;
				users.setDirty(u, "suspendTimer");
			}
			if (++u.loginAttempt >= 5) return "{\"ok\":false,\"msg\":\"你的账户已被锁定\"}";

			if (pass.equals(u.tempOtp) || o.hasher.compare(u.passHash, IOUtil.encodeUTF8(pass))) {
				getLogger().info("用户{}在{}密码登录成功", user, req.proxyRemoteAddress());
				return loginSuccess(o, u, req, false);
			}

			if (u.totpKey != null) {
				String totp = TOTP.makeTOTP(o.hmac, u.totpKey, time);
				if (totp.equals(pass)) {
					getLogger().info("用户{}在{}验证码登录成功", user, req.proxyRemoteAddress());
					return loginSuccess(o, u, req, false);
				}
			}

			users.setDirty(u, "loginAttempt");
			getLogger().info("用户{}在{}登录失败", user, req.proxyRemoteAddress());
		}

		return "{\"ok\":false,\"msg\":\"用户名或密码错误\"}";
	}

	@POST
	public String register(Request req, String user, String pass) throws IllegalRequestException {
		if (!getConfig().getBool("register")) return "{\"ok\":false,\"msg\":\"注册功能已关闭\"}";

		if (user.length() < 2 || user.length() > 31 || pass.length() < 6 || pass.length() > 39) throw BAD_REQUEST;

		User u = users.createUser(user);
		u.passHash = LocalData.get(req).hasher.hash(IOUtil.encodeUTF8(pass));
		u.registerTime = System.currentTimeMillis();
		u.registerAddr = req.proxyRemoteAddress();
		users.setDirty(u, "passHash", "registerTime", "registerAddr");
		return "{\"ok\":true}";
	}

	@GET
	@Interceptor("login")
	public String makeTotp(Request req) {
		User u = (User) req.threadLocal().get("xsso:user");
		byte[] key = new byte[20];
		LocalData.get(req).srnd.nextBytes(key);
		return "{\"ok\":true,\"url\":\""+TOTP.makeURL(key, u.name, siteName != null ? siteName : req.host())+"\",\"secret\":\""+TextUtil.bytes2hex(key)+"\"}";
	}

	@POST
	@Interceptor("login")
	public String bindTotp(Request req, String secret, String code) throws IllegalRequestException {
		User u = (User) req.threadLocal().get("xsso:user");
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
	@Interceptor("login")
	public String changePassword(Request req, String pass) throws IllegalRequestException {
		if (pass.length() < 6 || pass.length() > 39) throw BAD_REQUEST;

		var token = req.cookie().get(COOKIE_ID).value();

		ByteList buf = IOUtil.getSharedByteBuf();
		Base64.decode(token, buf, Base64.B64_URL_SAFE_REV);

		buf.readVUInt();
		long expire = buf.readVULong();

		long time = getUnixSecond() + accessTokenTTL - expire;
		if (time < 180) {
			var u = (User) req.threadLocal().get("xsso:user");
			u.passHash = LocalData.get(req).hasher.hash(IOUtil.encodeUTF8(pass));
			users.setDirty(u, "passHash");
			return "{\"ok\":true,\"msg\":\"操作成功\"}";
		} else {
			return "{\"ok\":false,\"msg\":\"您登录超过3分钟("+time+"秒)\"}";
		}
	}

	private final ConcurrentHashMap<String, QRLogin> qrLoginTemp = new ConcurrentHashMap<>();
	private final class QRLogin implements RequestFinishHandler {
		final String id;
		final String address;
		final long expire = System.currentTimeMillis() + QRCODE_TTL;

		ResponseHeader rh;
		String loginToken, accessToken;

		public QRLogin(String id, String address) {
			this.id = id;
			this.address = address;
		}

		public synchronized void setScanned() {

		}

		public synchronized void setCompleted(String loginToken, String accessToken) {
			this.loginToken = loginToken;
			this.accessToken = accessToken;
			if (this.rh != null) sendHeader();
		}

		private void sendHeader() {
			var req = rh.request();
			setCookie(req, loginToken);
			try {
				rh.body(Content.json("{\"ok\":true,\"token\":\""+accessToken+"\"}"));
			} catch (IOException ignored) {
				// might be closed by endpoint
			}
		}

		public synchronized boolean await(ResponseHeader rh) {
			if (this.rh != null) return false;

			if (loginToken == null) {
				this.rh = rh;
				rh.enableAsyncResponse(QRCODE_TTL).onFinish(this);
				return true;
			}

			sendHeader();
			return true;
		}

		@Override public boolean onRequestFinish(ResponseHeader rh, boolean success) {
			if (!success) qrLoginTemp.remove(id);
			return false;
		}
		@Override public boolean onResponseTimeout(ResponseHeader rh) throws IllegalRequestException {
			qrLoginTemp.remove(id);
			throw new IllegalRequestException(200, Content.json("{\"ok\":false,\"msg\":\"信号灯已到达\"}"));
		}
	}

	@GET
	public String qrcode(Request req) {
		if (!getConfig().getBool("qrcode")) return "{\"ok\":false,\"msg\":\"未配置二维码登录\"}";

		long time = System.nanoTime();
		long rand = ThreadLocalRandom.current().nextLong();
		String id;
		while (true) {
			long magicId = (time ^ rand) * (rand ^ tempKey);

			id = IOUtil.getSharedByteBuf().putLong(magicId).base64UrlSafe();

			var obj = qrLoginTemp.get(id);
			if (obj == null || System.currentTimeMillis() >= obj.expire) break;

			rand = ThreadLocalRandom.current().nextLong();
		}

		qrLoginTemp.put(id, new QRLogin(id, "Unknown("+req.proxyRemoteAddress()+")的"+req.get("user-agent")));

		return "{\"ok\":true,\"url\":\""+(req.isSecure()?"https":"http")+"://"+req.host()+"/"+sitePath+id+"/qrcode/apply\",\"id\":\""+id+"\",\"timeout\":"+ QRCODE_TTL +"}";
	}

	@GET("qrcode/:id")
	public Content qrcode_get(Request req) {
		String id = req.argument("id");
		var obj = qrLoginTemp.get(id);
		if (obj != null) {
			if (System.currentTimeMillis() >= obj.expire) {
				qrLoginTemp.remove(id);
			} else {
				if (obj.await(req.server()))
					return null;
			}
		}
		return Content.json("{\"ok\":false,\"msg\":\"二维码已过期\"}");
	}

	@GET("qrcode/:id/apply")
	public Content qrcode_apply(Request req) throws IllegalRequestException {
		String userAgent = req.get("user-agent");
		if (!userAgent.contains("asdf")) {
			// 检测APP环境，或者跳转到下载页

		}

		String id = req.argument("id");
		var obj = qrLoginTemp.get(id);
		if (obj != null) {
			if (System.currentTimeMillis() >= obj.expire) {
				qrLoginTemp.remove(id);
			} else {
				var user = ipc(new TypedKey<User>("getUser"), req);
				if (user != null) {
					var confirm = req.queryParam().get("confirm");
					if (confirm != null) {
						long expire = accessTokenTTL - 180;
						var o = LocalData.get(req);

						var login_token = makeToken(user, 'L', expire, o);
						var access_token = makeToken(user, 'A', OPENAPI_TOKEN_TTL, o);
						obj.setCompleted(login_token, access_token);

						qrLoginTemp.remove(id);

						getLogger().info("用户{}在{}扫码登录{}成功", user, req.proxyRemoteAddress(), obj.address);
						return Content.json("{\"ok\":true,\"msg\":\"操作成功\"}");
					} else {
						obj.setScanned();
						return Content.json("{\"ok\":true,\"address\":\""+obj.address+"\"}");
					}
				}
			}
		}
		return Content.json("{\"ok\":false,\"msg\":\"二维码已过期\"}");
	}

	@GET
	public Content loginCheck(Request req, String accessKey, String userToken) {
		User u = verifyToken(req, userToken, 'A');
		if (u == null) return Content.json("{\"ok\":false,\"msg\":\"token校验失败\"}");

		if (!u.accessNonceUsed.add(Long.hashCode(LocalData.get(req).localNonce)))
			return Content.json("{\"ok\":false,\"msg\":\"token已被使用\"}");
		long time = getUnixSecond();
		if (time - u.accessNonceTime > OPENAPI_TOKEN_TTL) {
			u.accessNonceUsed.clear();
			u.accessNonceTime = time;
		}

		var o = LocalData.get(req);
		o.hmac.init(openIdKey);
		o.hmac.update(IOUtil.getSharedByteBuf().putInt(u.id));

		var ser = new JsonSerializer();
		ser.emitMap();
		ser.key("ok");
		ser.emit(true);
		ser.key("openid");
		ser.emit(IOUtil.encodeHex(o.hmac.digestShared()));
		ser.key("name");
		ser.emit(u.name);
		return Content.json(ser.getValue());
	}

	@Interceptor("login")
	public String loginInterceptor(Request req) throws IllegalRequestException {
		var token = req.cookie().get(COOKIE_ID);
		if (token != null) {
			User u = verifyToken(req, token.value(), 'L');
			req.threadLocal().put("xsso:user", u);
			if (u != null) return null;
			token.path("/").expires(-1);
		}

		return "{\"ok\":false,\"msg\":\"未登录\"}";
	}

	@POST
	@Interceptor("login")
	public String admin__unsuspend(Request req, String user) {
		User u = (User) req.threadLocal().get("xsso:user");
		if (!u.isAdmin()) return "{\"ok\":false,\"msg\":\"权限不足\"}";

		u = users.getUserByName(user);
		if (u != null) {
			u.loginTime = 0;
			return "{\"ok\":true,\"msg\":\"操作成功\"}";
		}

		return "{\"ok\":false,\"msg\":\"用户不存在\"}";
	}

	@POST
	@Interceptor("login")
	public String admin__changePassword(Request req, String user, String pass) {
		User u = (User) req.threadLocal().get("xsso:user");
		if (!u.isAdmin()) return "{\"ok\":false,\"msg\":\"权限不足\"}";

		u = users.getUserByName(user);
		if (u != null) {
			if (u.isAdmin()) return "{\"ok\":false,\"msg\":\"权限不足\"}";

			u.passHash = LocalData.get(req).hasher.hash(IOUtil.encodeUTF8(pass));
			users.setDirty(u, "passHash");
			return "{\"ok\":true,\"msg\":\"密码已重置\"}";
		}

		return "{\"ok\":false,\"msg\":\"用户不存在\"}";
	}

	@POST
	@Interceptor("login")
	public String admin__deleteTotp(Request req, String user, String pass) {
		User u = (User) req.threadLocal().get("xsso:user");
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
	//endregion

	private String loginSuccess(LocalData o, User u, Request req, boolean is_refresh) {
		var expired = "\0PASSWORDEXPIRED\0".equals(u.tempOtp);
		u.tempOtp = null;
		u.loginAttempt = 0;
		u.loginTime = System.currentTimeMillis();
		u.loginAddr = req.proxyRemoteAddress();

		//这个功能暂时不开放给前端
		//u.tokenNonce = o.srnd.nextLong();
		//让之前所有的refresh token失效(多设备登录怎么处理？？先只保留冻结功能好了)

		users.setDirty(u, "loginAttempt", "loginTime", "loginAddr", "refreshNonce");
		long expire = accessTokenTTL + (is_refresh ? -180 : 0);

		var loginToken = makeToken(u, 'L', expire, o);
		setCookie(req, loginToken);

		var refreshToken = makeToken(u, 'R', refreshTokenTTL, o);
		var accessToken = makeToken(u, 'A', OPENAPI_TOKEN_TTL, o);

		var sb = new CharList().append("{\"ok\":true,\"msg\":\"登录成功\",\"token\":\"").append(accessToken).append("\",\"refreshToken\":\"").append(refreshToken).append('"');
		if (u.totpKey != null) sb.append(",\"hasTotp\":true");
		if (expired) sb.append(",\"expired\":true");
		return sb.append('}').toStringAndFree();
	}
	private void setCookie(Request req, String loginToken) {
		req.sendCookieToClient(Collections.singletonList(new Cookie(COOKIE_ID, loginToken).path("/").secure(req.isSecure()).expires(accessTokenNoStore ? 0 : accessTokenTTL).httpOnly(true).sameSite("Strict")));
	}

	private String makeToken(User u, char usage, long expire, LocalData o) {
		ByteList buf = IOUtil.getSharedByteBuf();

		byte[] nonce = o.tmpNonce;
		o.srnd.nextBytes(nonce);

		expire += getUnixSecond();
		var hmac = o.hmac;
		hmac.init(sessionKey);
		hmac.update(buf.putInt(u.id).putLong(expire).put(nonce)
					  .put(usage).putLong(u.tokenSeq)
					  .putAscii(u.passHash));

		buf.clear();
		return buf.putVUInt(u.id).putVULong(expire).put(nonce).put(hmac.digestShared()).base64UrlSafe();
	}

	private User verifyToken(Request req, String v, char usage) {
		// for future SHA-256 checksum
		if (v.length() < 40 || v.length() > 72) return null;

		try {
			var buf = IOUtil.getSharedByteBuf();
			Base64.decode(v, buf, Base64.B64_URL_SAFE_REV);

			int uid = buf.readVUInt();
			long expire = buf.readVULong();
			var nonce = buf.readLong();
			byte[] hash = buf.readBytes(buf.readableBytes());

			if (getUnixSecond() < expire) {
				User u = users.getUserById(uid);
				if (u != null) {
					var o = LocalData.get(req);

					var hmac = o.hmac;
					hmac.init(sessionKey);
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