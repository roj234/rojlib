package roj.misc;

import roj.collect.MyBitSet;
import roj.io.IOUtil;
import roj.math.MathUtils;
import roj.text.BitMapper;
import roj.text.CharList;
import roj.ui.CLIUtil;

import java.io.File;
import java.io.IOException;

/**
 * This is not the end.
 * 		#root > div > div[class^='css-']:last-child
 *
 * @author Roj234
 * @since 2022/9/4 0004 20:30
 */
public class TinyCoder {
	public static void main(String[] args) throws IOException {
		if (args.length < 2) {
			System.out.println("TinyCoder <模式：D/E/F/B> <词表> [输入]");
			System.out.println("词表数量必须为2的n次方如2 4 8 16");
			System.out.println("D=Decode,E=Encode,F=File,B=Debug");
			System.out.println("文件必须为UTF-8编码");
			return;
		}

		int st = collectSetting(args[0]);

		String data = IOUtil.readUTF(new File(args[1]));

		CharList tmp = IOUtil.getSharedCharBuf();
		MyBitSet set = new MyBitSet();
		for (int i = 0; i < data.length(); i++) {
			char c = data.charAt(i);
			if (!set.add(c) || Character.isHighSurrogate(c) || Character.isLowSurrogate(c)) {
				if ((st & DEBUG) != 0) System.err.print(c);
			} else {
				tmp.append(c);
			}
		}

		if ((st & DEBUG) != 0 && tmp.length() < data.length()) System.err.println("\n上述词重复,建议删去");

		int len = MathUtils.getMin2PowerOf(tmp.length());
		if (len > tmp.length()) {
			len >>>= 1;

			if ((st & DEBUG) != 0) System.err.println("词表数量应为2的n次方如, 现有 " + tmp.length() + " 降至 " + len);
			tmp.setLength(len);
		}

		BitMapper c = new BitMapper(tmp.toString());

		if ((st & DEBUG) != 0) System.err.println("模式" + ((st & ENCODE) != 0 ? "编码" : "解码"));
		if (args.length > 2) {
			if ((st & FILE) == 0) {
				args[0] = args[1] = "";
				String code = String.join(" ", args).substring(2);
				code = (st & ENCODE) != 0 ? c.encode(code) : c.decode(code);
				System.out.println(code);
			} else {
				String code = IOUtil.readUTF(new File(args[2]));
				code = (st & ENCODE) != 0 ? c.encode(code) : c.decode(code);
				System.out.println(code);
			}
		} else {
			while (true) {
				String in = CLIUtil.in.readLine();
				if (in == null) return;

				in = (st & ENCODE) != 0 ? c.encode(in) : c.decode(in);
				System.out.println(in);
			}
		}
	}

	static final int DECODE = 0, ENCODE = 2, FILE = 4, DEBUG = 8;

	private static int collectSetting(String arg) {
		int state = 0;
		for (int i = 0; i < arg.length(); i++) {
			switch (arg.charAt(i)) {
				case 'D':
					state |= DECODE;
					break;
				case 'E':
					state |= ENCODE;
					break;
				case 'F':
					state |= FILE;
					break;
				case 'B':
					state |= DEBUG;
					break;
			}
		}
		return state;
	}

}
