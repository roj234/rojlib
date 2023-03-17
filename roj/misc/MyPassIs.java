package roj.misc;

import roj.collect.SimpleList;
import roj.crypt.HMAC;
import roj.crypt.SM3;
import roj.io.IOUtil;
import roj.text.LineReader;
import roj.text.UTFCoder;
import roj.ui.UIUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.List;

public class MyPassIs {
	static byte[] key;
	static List<String> stored;

	public static void main(String[] args) throws IOException {
		File publicKey = new File("key.bin");
		if (!publicKey.isFile()) {
			System.out.println("没有公钥，我们现在要生成一个！");
			try (FileOutputStream fos = new FileOutputStream(publicKey)) {
				byte[] data = key = new byte[64];
				new SecureRandom().nextBytes(data);
				fos.write(data);
			}
		} else {
			key = IOUtil.getSharedByteBuf().readStreamFully(new FileInputStream(publicKey)).toByteArray();
		}

		System.out.println("请输入主密码");
		char[] pass = UIUtil.readPassword();

		File passDb = new File("pass.bin");
		UTFCoder uc = IOUtil.SharedCoder.get();
		if (passDb.isFile()) {
			IOUtil.getSharedByteBuf().readStreamFully(new FileInputStream(passDb));
			stored = LineReader.slrParserV2(uc.charBuf, true);
		} else {
			System.out.println("新的密码已设置，请牢记");
			stored = new SimpleList<>();
		}

		HMAC enc = new HMAC(new SM3(), 32);
		enc.setSignKey(key);

		System.out.println("助记名(例如QQ,VX):");
		String category = UIUtil.userInput("");
		enc.update(category.getBytes(StandardCharsets.UTF_8));
		enc.update((byte) 0);
		if (!stored.contains(category)) {
			FileOutputStream fos = new FileOutputStream(passDb, true);
			uc.encodeTo(category, fos);
			fos.write('\n');
			fos.close();
		}

		System.out.println("账号(不要缩写,区分大小写):");
		String account = UIUtil.userInput("");
		enc.update(account.getBytes(StandardCharsets.UTF_8));
		enc.update((byte) 0);

		byte[] data = enc.digestShared();
		long hash = 0L;
		for (byte b : data) {
			hash = hash * 31 + (b & 0xFF);
		}

		System.out.println("您的密码是:");
		System.out.println(Long.toString(hash, 36));
	}
}
