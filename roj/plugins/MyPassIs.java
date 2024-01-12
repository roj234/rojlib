package roj.plugins;

import roj.collect.MyBitSet;
import roj.collect.MyHashMap;
import roj.config.ConfigMaster;
import roj.config.VinaryParser;
import roj.config.data.CEntry;
import roj.config.data.CMapping;
import roj.config.exch.TByteArray;
import roj.crypt.*;
import roj.io.IOUtil;
import roj.platform.Plugin;
import roj.text.CharList;
import roj.ui.CLIUtil;
import roj.ui.Console;
import roj.ui.terminal.Argument;
import roj.ui.terminal.CommandConsole;
import roj.util.ByteList;

import javax.crypto.Cipher;
import java.awt.*;
import java.awt.datatransfer.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Locale;

import static roj.ui.terminal.CommandNode.argument;
import static roj.ui.terminal.CommandNode.literal;

public class MyPassIs extends Plugin {
	public static final int MASTER_KEY_LEN = 64;

	private File keyFile;
	private HMAC mac;
	private RCipherSpi cipher;
	private CMapping data;
	private byte[] pass;

	private final CommandConsole c = new CommandConsole("");
	private final MyHashMap<String, String> hints = new MyHashMap<>();

	@Override
	protected void onEnable() {
		c.setCommandEcho(false);
		c.setInputHistory(false);

		registerCommand(literal("mpi")
			.then(literal("login").executes(ctx -> login()))
			.then(literal("logout").executes(ctx -> {
				if (data == null) return;

				Arrays.fill(pass, (byte) 0);
				data = null;
				cipher = null;
				mac = null;

				System.gc();
				CLIUtil.success("清除内存中的密码");
			}))
			.then(literal("unregister").then(argument("site", Argument.oneOf(hints)).executes(ctx -> {
				c.setPrompt("\u001b[;97m再次键入网站名并按回车以删除 > ");
				c.setInputEcho(true);

				String site = CLIUtil.awaitCommand(c, Argument.rest());
				String site1 = ctx.argument("site", String.class);
				if (site1.equals(site)){
					data.getMap("record").remove(site1);
					hints.remove(site1);
					save();

					CLIUtil.warning("永久的删除了网站"+site1+"的生成器数据");
				} else {
					CLIUtil.warning("两次键入的名称不同");
				}
			})))
			.then(argument("site", Argument.suggest(hints)).executes(ctx -> {
				login();

				Console prev = CLIUtil.getConsole();
				CLIUtil.setConsole(null);
				try {
					site(ctx.argument("site", String.class));
				} finally {
					CLIUtil.setConsole(prev);
				}
			}))
		);

		getDataFolder().mkdirs();
		keyFile = new File(getDataFolder(), "key.bjson");
	}

	@Override
	protected void onDisable() { unregisterCommand("mpi"); }

	private void login() throws Exception {
		if (data != null) return;

		c.setPrompt("\u001b[;97m请输入密码 > ");
		c.setInputEcho(false);
		String passStr = CLIUtil.awaitCommand(c, Argument.rest());
		c.setInputEcho(true);

		this.mac = new HMAC(MessageDigest.getInstance("SHA-256"));
		this.pass = HMAC.HKDF_expand(mac, IOUtil.SharedCoder.get().encode(passStr), 32);
		this.cipher = new FeedbackCipher(new AES(), FeedbackCipher.MODE_CTR);

		File file = new File(getDataFolder(), "key.yml");
		CMapping data;
		if (file.isFile()) {
			data = ConfigMaster.parse(file).asMap();
			data.put("key", new TByteArray(data.get("key").asList().toByteArray()));
			CLIUtil.warning("数据已导入, 使用save保存");
		} else if (keyFile.length() == 0) {
			data = new CMapping();

			byte[] master_key = new byte[MASTER_KEY_LEN];
			new SecureRandom().nextBytes(master_key);

			data.put("key", new TByteArray(master_key));
			data.put("record", new CMapping());

			CLIUtil.warning("新的密钥已生成，创建任意账号来保存");
		} else {
			try (InputStream in = new FileInputStream(keyFile)) {
				byte[] iv = new byte[16];
				IOUtil.readFully(in, iv);
				cipher.init(Cipher.DECRYPT_MODE, pass, new IvParameterSpecNC(iv), null);

				data = new VinaryParser().asArray().parseRaw(new CipherInputStream(in, cipher)).asMap();
			} catch (Exception e) {
				CLIUtil.error("密码错误:"+e.getMessage());
				return;
			}
		}

		this.data = data;
		this.hints.clear();
		for (String name : data.getMap("record").keySet())
			hints.put(name, name);

		CLIUtil.success("登录成功");
		save();
	}

	private void site(String site) {
		CEntry prev = data.query("record."+site);

		char[] charset;
		int length;

		if (prev == null) {
			CMapping m = new CMapping();
			while (true) {
				try {
					c.setPrompt("字符集[1数字a小写字母A大写字母@特殊符号] > ");
					String cs = CLIUtil.awaitCommand(c, Argument.string());
					charset = buildCharset(cs);
					m.put("c", cs);

					c.setPrompt("密码长度 > ");
					length = CLIUtil.awaitCommand(c, Argument.number(6, 60));
					m.put("l", length);

					data.getOrCreateMap("record").put(site, m);
				break;
				} catch (Exception e) {
					System.out.println("输入错误");
				}
			}
			prev = m;
		} else {
			CMapping m = prev.asMap();
			charset = buildCharset(m.getString("c"));
			length = m.getInteger("l");
		}

		CMapping accounts = prev.asMap().getOrCreateMap("account");

		MyHashMap<String, String> hints = new MyHashMap<>();
		for (String s : accounts.keySet()) hints.put(s, s);
		c.setPrompt("账号 > ");
		String account = CLIUtil.awaitCommand(c, Argument.suggest(hints));

		byte[] keys = (byte[]) data.get("key").unwrap();
		int iter = accounts.getInteger(account);

		while (true) {
			byte[] gen_pass = HMAC.HKDF_expand(mac, keys, IOUtil.getSharedByteBuf().putUTFData(account).putInt(iter), length);

			CharList sb = new CharList();
			for (int i = 0; i < gen_pass.length; i++) {
				sb.append(charset[(gen_pass[i]&0xFF) % charset.length]);
			}

			if (accounts.containsKey(account)) {
				sb.insert(0, "您的密码是[(c)opy/Enter] > ");
				CLIUtil.renderBottomLine(sb, true, CLIUtil.getStringWidth(sb)+1);
				char ce = CLIUtil.awaitCharacter(MyBitSet.from("c\n"));
				CLIUtil.removeBottomLine(sb, true);
				try {
					if (ce == 'c') {
						Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(sb.toString()), null);
						CLIUtil.success("密码已复制到剪贴板");
					}
				} catch (Exception ignored) {}
				return;
			}
			sb.insert(0, "您的密码是[\u001b[;92m(a)ccept\u001b[;0m,\u001b[;91m(c)ancel\u001b[;96m,\u001b[;93m(r)andom\u001b[;0m] > ");
			CLIUtil.renderBottomLine(sb, true, CLIUtil.getStringWidth(sb)+1);
			char acr = CLIUtil.awaitCharacter(MyBitSet.from("acr"));
			CLIUtil.removeBottomLine(sb, true);
			switch (acr) {
				case 'a':
					accounts.put(account, iter);
					save();
				return;
				default:
				case 'c':
					if (accounts.size() == 0) data.getMap("record").remove(site);
				return;
				case 'r':
					iter++;
				break;
			}
		}

	}

	private void save() {
		try (FileOutputStream out = new FileOutputStream(keyFile)) {
			byte[] iv1 = new SecureRandom().generateSeed(16);
			out.write(iv1);

			cipher.init(Cipher.ENCRYPT_MODE, pass, new IvParameterSpecNC(iv1), null);

			ByteList buf = IOUtil.getSharedByteBuf();
			new VinaryParser().serialize(data, buf);
			cipher.cryptInline(buf,buf.readableBytes());

			buf.writeToStream(out);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static char[] buildCharset(String cs) {
		// 1数字a小写字母A大写字母@特殊符号
		MyBitSet set = MyBitSet.from(cs);
		CharList sb = IOUtil.getSharedCharBuf();
		if (set.contains('1')) sb.append("0123456789");
		if (set.contains('a')) sb.append("ABCDEFGHIJKLMNOPQRSTUVWXYZ".toLowerCase(Locale.ROOT));
		if (set.contains('A')) sb.append("ABCDEFGHIJKLMNOPQRSTUVWXYZ");
		if (set.contains('@')) sb.append("!@#$%^&*()_+-=[]{}\\|,.<>/?`~");
		return sb.toCharArray();
	}
}