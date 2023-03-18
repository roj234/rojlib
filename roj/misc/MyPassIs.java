package roj.misc;

import roj.collect.MyBitSet;
import roj.config.VinaryParser;
import roj.config.data.CEntry;
import roj.config.data.CMapping;
import roj.config.exch.TByteArray;
import roj.crypt.*;
import roj.io.IOUtil;
import roj.text.CharList;
import roj.ui.CmdUtil;
import roj.ui.UIUtil;
import roj.util.ByteList;

import javax.crypto.Cipher;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.List;
import java.util.Locale;

public class MyPassIs {
	public static final int MASTER_KEY_LEN = 64;
	static byte[] MasterKey;
	static List<String> stored;

	public static void main(String[] args) throws Exception {
		HMAC mac = new HMAC(MessageDigest.getInstance("SHA-256"));

		System.out.print("请输入保护主密钥的主密码(不会显示):");
		byte[] pass = IOUtil.SharedCoder.get().encode(new CharList(UIUtil.readPassword()));
		pass = HMAC.HKDF_expand(mac, pass, 32);

		CmdUtil.clearScreen();

		ByteList buf = IOUtil.getSharedByteBuf();

		CMapping data;

		RCipherSpi cipher = new FeedbackCipher(new AES(), FeedbackCipher.MODE_CTR);
		IvParameterSpecNC iv = new IvParameterSpecNC(new byte[16]);
		cipher.init(Cipher.ENCRYPT_MODE, pass, iv, null);
		byte[] master_key;

		File masterKey = new File("key.bjson");
		if (!masterKey.isFile() || masterKey.length() == 0) {
			data = new CMapping();

			master_key = new byte[MASTER_KEY_LEN];
			new SecureRandom().nextBytes(master_key);

			data.put("key", new TByteArray(master_key));
			data.put("record", new CMapping());
		} else {
			cipher.init(Cipher.DECRYPT_MODE, pass, iv, null);

			data = new VinaryParser().asArray().parseRaw(buf.readStreamFully(new CipherInputStream(new FileInputStream(masterKey), cipher))).asMap();
			buf.clear();
			master_key = (byte[]) data.get("key").unwrap();
		}
		cipher.init(Cipher.ENCRYPT_MODE, pass, iv, null);


		main:
		while (true) {
			String category = UIUtil.userInput("[L列出,Q获取,其它网站名]:");

			CmdUtil.clearScreen();

			if ("L".equals(category)) {
				System.out.println(data.query("record").toYAMLb());
				continue;
			} else if ("Q".equals(category)) {
				System.out.println("WIP.");
				continue;
			}

			CEntry prev = data.query("record."+category);

			char[] charset;
			int length;

			if (prev == null) {
				CMapping m = new CMapping();
				while (true) {
					try {
						String cs = UIUtil.userInput("字符集[1数字a小写字母A大写字母@特殊符号]:");
						length = Integer.parseInt(UIUtil.userInput("长度:"));
						charset = buildCharset(cs);

						m.put("c", cs);
						m.put("l", length);
						data.getOrCreateMap("record").put(category, m);
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

			String account = UIUtil.userInput("账号:");
			CMapping accounts = prev.asMap().getOrCreateMap("account");

			int cnt = accounts.getInteger(account);
			while (true) {
				buf.clear();
				byte[] gen_pass = HMAC.HKDF_expand(mac, master_key, buf.putUTFData(account).putInt(cnt), length);

				CharList sb = IOUtil.getSharedCharBuf();
				for (int i = 0; i < gen_pass.length; i++) {
					sb.append(charset[(gen_pass[i]&0xFF) % charset.length]);
				}

				System.out.println("您的密码是:");
				System.out.println(sb);

				if (accounts.containsKey(account)) continue main;
				String s = UIUtil.userInput("喜欢吗?回车选择,或任意字符再来一个:");
				if (s.isEmpty()) break;

				CmdUtil.clearScreen();
				cnt++;
			}

			accounts.put(account, cnt);

			try (FileOutputStream out = new FileOutputStream(masterKey)) {
				buf.clear();
				new VinaryParser().serialize(data, buf);
				cipher.cryptInline(buf,buf.readableBytes());
				buf.writeToStream(out);
			}
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
