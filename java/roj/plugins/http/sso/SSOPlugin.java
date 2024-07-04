package roj.plugins.http.sso;

import roj.config.data.CMap;
import roj.crypt.MySaltedHash;
import roj.net.http.server.ZipRouter;
import roj.net.http.server.auto.OKRouter;
import roj.platform.Plugin;
import roj.text.ACalendar;
import roj.ui.CLIUtil;
import roj.ui.terminal.Argument;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

import static roj.ui.terminal.CommandNode.argument;
import static roj.ui.terminal.CommandNode.literal;

/**
 * @author Roj234
 * @since 2024/7/9 0009 8:27
 */
public class SSOPlugin extends Plugin {

	@Override
	protected void onEnable() throws Exception {
		CMap cfg = getConfig();
		UserManager man;
		if (cfg.getString("storage", "file").equals("file")) {
			man = new JsonUserManager(new File(getDataFolder(), "users.json"));
		} else {
			man = new DbUserManager("users");
		}

		SSO sso = new SSO("sso/", man);
		sso.disableRegister = cfg.getBool("disableRegister");
		sso.siteName = cfg.getString("siteName");

		ZipRouter resource = new ZipRouter(getDescription().getArchive());
		var router = new OKRouter().register(sso).addPrefixDelegation("", resource);
		registerRoute("sso", router);
		registerInterceptor("loginRedirect", router.getInterceptor("loginRedirect"));

		var c = literal("easysso");
		c.then(literal("setpasswd").then(argument("user", Argument.string()).then(argument("pass", Argument.string()).executes(ctx -> {
			 String name = ctx.argument("user", String.class);
			 User u = man.getUserByName(name);
			 if (u == null) u = man.createUser(name);

			 u.passHash = MySaltedHash.hasher(new SecureRandom()).hash(ctx.argument("pass", String.class).getBytes(StandardCharsets.UTF_8));
			 man.setDirty(u, "passHash");
		 }))))
		 .then(literal("userinfo").then(argument("user", Argument.string()).executes(ctx -> {
			 User u = man.getUserByName(ctx.argument("user", String.class));
			 if (u == null) {CLIUtil.warning("账户不存在");return;}

			 System.out.println("用户ID: "+u.id);
			 System.out.println("已绑定OTP: "+(u.totpKey != null));
			 System.out.println("用户权限: "+u.permissions);
			 System.out.println("密码错误次数: "+u.loginAttempt);
			 System.out.println("上次登录: "+ACalendar.toLocalTimeString(u.loginTime));
			 System.out.println("登录IP: "+u.loginAddr);
			 System.out.println("注册时间: "+ACalendar.toLocalTimeString(u.registerTime));
			 System.out.println("注册IP: "+u.registerAddr);
		 })))
		 .then(literal("suspend").then(argument("user", Argument.string()).then(argument("timeout", Argument.number(30, Integer.MAX_VALUE)).executes(ctx -> {
			 User u = man.getUserByName(ctx.argument("user", String.class));
			 if (u == null) {CLIUtil.warning("账户不存在");return;}

			 u.suspendTimer = System.currentTimeMillis() + ctx.argument("timeout", Integer.class)*1000L;
			 u.loginAttempt = 99;
			 System.out.println("账户已锁定至"+ACalendar.toLocalTimeString(u.suspendTimer));
		 }))))
		 .then(literal("unsuspend").then(argument("user", Argument.string()).executes(ctx -> {
			 User u = man.getUserByName(ctx.argument("user", String.class));
			 if (u == null) {CLIUtil.warning("账户不存在");return;}

			 u.suspendTimer = 0;
			 System.out.println("账户已解锁");
		 })))
		 .then(literal("unbindotp").then(argument("user", Argument.string()).executes(ctx -> {
			 User u = man.getUserByName(ctx.argument("user", String.class));
			 if (u == null) {CLIUtil.warning("账户不存在");return;}

			 u.totpKey = null;
			 man.setDirty(u, "totpKey");
		 })));
		registerCommand(c);
	}
}