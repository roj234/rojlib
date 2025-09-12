package roj.plugins;

import roj.collect.BitSet;
import roj.collect.HashMap;
import roj.config.ConfigMaster;
import roj.config.NbtParser;
import roj.config.node.ByteArrayValue;
import roj.config.node.ConfigValue;
import roj.config.node.MapValue;
import roj.crypt.*;
import roj.io.IOUtil;
import roj.plugin.Plugin;
import roj.text.CharList;
import roj.ui.*;
import roj.util.ByteList;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Locale;

import static roj.ui.CommandNode.argument;
import static roj.ui.CommandNode.literal;

public class MyPassIs extends Plugin {
	public static final int MASTER_KEY_LEN = 64;

	private File keyFile;
	private HMAC mac;
	private RCipher cipher;
	private MapValue data;
	private byte[] pass;

	private final Shell c = new Shell("");
	private final HashMap<String, String> hints = new HashMap<>();

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
				Tty.success("清除内存中的密码");
			}))
			.then(literal("unregister").then(argument("site", Argument.oneOf(hints)).executes(ctx -> {
				login();
				if (data == null) return;

				c.setPrompt("\u001b[;97m再次键入网站名并按回车以删除 > ");
				c.setInputEcho(true);

				String site = c.readSync(Argument.rest());
				String site1 = ctx.argument("site", String.class);
				if (site1.equals(site)){
					data.getMap("record").remove(site1);
					hints.remove(site1);
					save();

					Tty.warning("永久的删除了网站"+site1+"的生成器数据");
				} else {
					Tty.warning("两次键入的名称不同");
				}
			})))
			.then(literal("upgrade").then(argument("site", Argument.oneOf(hints)).executes(ctx -> {
				login();
				if (data == null) return;

				c.setPrompt("\u001b[;97m请确定已复制完旧版密码,再次键入网站名并按回车 > ");
				c.setInputEcho(true);

				String site = c.readSync(Argument.rest());
				String site1 = ctx.argument("site", String.class);
				if (site1.equals(site)) {
					data.getMap("record").getMap(site1).put("v", 2);
					save();

					Tty.warning("已升级到v2生成器，您可以重新获取密码");
				} else {
					Tty.warning("两次键入的名称不同");
				}
			})))
			.then(argument("site", Argument.suggest(hints)).executes(ctx -> {
				login();
				if (data == null) return;

				site(ctx.argument("site", String.class));
			}))
		);

		getDataFolder().mkdirs();
		keyFile = new File(getDataFolder(), "key.nbe");
	}

	private void login() throws Exception {
		if (data != null) return;

		c.setPrompt("\u001b[;97m请输入密码 > ");
		c.setInputEcho(false);
		String passStr = c.readSync(Argument.rest());
		c.setInputEcho(true);

		this.mac = new HMAC(MessageDigest.getInstance("SHA-256"));
		this.pass = CryptoFactory.HKDF_expand(mac, IOUtil.encodeUTF8(passStr), 32);
		this.cipher = new FeedbackCipher(CryptoFactory.AES(), FeedbackCipher.MODE_CTR);

		File plaintextKey = new File(getDataFolder(), "key.yml");
		MapValue data;
		if (plaintextKey.isFile()) {
			if (keyFile.isFile()) throw new IllegalStateException("明文和加密的数据同时存在");
			data = ConfigMaster.fromExtension(plaintextKey).parse(plaintextKey).asMap();
			data.put("key", new ByteArrayValue(data.get("key").asList().toByteArray()));
			Tty.warning("数据已导入");
		} else if (keyFile.length() == 0) {
			data = new MapValue();

			byte[] master_key = new byte[MASTER_KEY_LEN];
			new SecureRandom().nextBytes(master_key);

			data.put("key", new ByteArrayValue(master_key));
			data.put("record", new MapValue());

			Tty.warning("新的密钥已生成，创建任意账号来保存");
		} else {
			try (InputStream in = new FileInputStream(keyFile)) {
				byte[] iv = new byte[16];
				IOUtil.readFully(in, iv);
				cipher.init(RCipher.DECRYPT_MODE, pass, new IvParameterSpecNC(iv), null);

				data = new NbtParser().parse(new CipherInputStream(in, cipher)).asMap();
			} catch (Exception e) {
				Tty.error("密码错误:"+e.getMessage());
				return;
			}
		}

		this.data = data;
		this.hints.clear();
		for (String name : data.getMap("record").keySet())
			hints.put(name, name);

		Tty.success("登录成功");
		if (plaintextKey.isFile()) save();
	}

	private void site(String site) {
		ConfigValue prev = data.query("record."+site);

		char[] charset;
		int length;

		if (prev == null) {
			MapValue m = new MapValue();
			while (true) {
				try {
					c.setPrompt("字符集[1数字a小写字母A大写字母@特殊符号] > ");
					String cs = c.readSync(Argument.string());
					charset = buildCharset(cs);
					m.put("c", cs);

					c.setPrompt("密码长度 > ");
					length = c.readSync(Argument.number(6, 60));
					m.put("l", length);

					data.getOrCreateMap("record").put(site, m);
				break;
				} catch (Exception e) {
					if (e instanceof NullPointerException) {
						Tty.warning("用户取消操作");
						return;
					}
					System.out.println("输入错误");
				}
			}
			m.put("v", 2);
			System.out.println("Mpi Generator V2!");
			prev = m;
		} else {
			MapValue m = prev.asMap();
			charset = buildCharset(m.getString("c"));
			length = m.getInt("l");
		}

		MapValue accounts = prev.asMap().getOrCreateMap("account");

		HashMap<String, String> hints = new HashMap<>();
		for (String s : accounts.keySet()) hints.put(s, s);
		c.setPrompt("账号 > ");
		String account = c.readSync(Argument.suggest(hints));
		if (account == null) {
			Tty.warning("用户取消操作");
			return;
		}

		byte[] keys = (byte[]) data.get("key").unwrap();
		int iter = accounts.getInt(account);

		while (true) {
			ByteList b = IOUtil.getSharedByteBuf();
			if (prev.asMap().getInt("v") == 2) b.putUTFData(site).put(0);

			byte[] gen_pass = CryptoFactory.HKDF_expand(mac, keys, b.putUTFData(account).putInt(iter), length);

			CharList sb = new CharList();
			for (int i = 0; i < gen_pass.length; i++) {
				sb.append(charset[(gen_pass[i]&0xFF) % charset.length]);
				gen_pass[i] = 0;
			}

			if (accounts.containsKey(account)) {
				String tip = "您的密码是[(c)opy/Enter] > ";
				sb.insert(0, tip);

				char ce = TUI.key(BitSet.from("c\n"), sb);
				try {
					if (ce == 'c') {
						ClipboardUtil.setClipboardText(sb.substring(tip.length(), sb.length()));
						Tty.success("密码已复制到剪贴板");
					}
				} catch (Exception ignored) {}
				sb._secureFree();
				return;
			}
			sb.insert(0, "您的密码是[\u001b[;92m(a)ccept\u001b[;0m,\u001b[;91m(c)ancel\u001b[;96m,\u001b[;93m(r)andom\u001b[;0m] > ");
			Tty.renderBottomLine(sb, true, Tty.getStringWidth(sb)+1);
			char acr = TUI.key("acr");
			Tty.removeBottomLine(sb);
			sb._secureFree();
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

			cipher.init(RCipher.ENCRYPT_MODE, pass, new IvParameterSpecNC(iv1), null);

			ByteList buf = IOUtil.getSharedByteBuf();
			ConfigMaster.NBT.toBytes(data, buf);
			cipher.cryptInline(buf,buf.readableBytes());

			buf.writeToStream(out);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static char[] buildCharset(String cs) {
		// 1数字a小写字母A大写字母@特殊符号
		BitSet set = BitSet.from(cs);
		CharList sb = IOUtil.getSharedCharBuf();
		if (set.contains('1')) sb.append("0123456789");
		if (set.contains('a')) sb.append("ABCDEFGHIJKLMNOPQRSTUVWXYZ".toLowerCase(Locale.ROOT));
		if (set.contains('A')) sb.append("ABCDEFGHIJKLMNOPQRSTUVWXYZ");
		if (set.contains('@')) sb.append("!@#$%^&*()_+-=[]{}\\|,.<>/?`~");
		return sb.toCharArray();
	}
}