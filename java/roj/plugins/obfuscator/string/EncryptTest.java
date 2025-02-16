package roj.plugins.obfuscator.string;

/**
 * @author Roj234
 * @since 2025/3/18 0018 15:13
 */
public class EncryptTest {
	public static String 我是解密函数一号(String arg) {
		return "simple";
	}
	public static String 我是解密函数二号(String arg) {
		StackTraceElement[] trace = new Throwable().getStackTrace();
		return "simple"+trace[0];
	}
	public static String 我是解密函数三号(String arg, int other) {
		return "simple"+other;
	}

	public static void main(String[] args) {
		int v = 56789;
		System.out.println(我是解密函数一号("test"));
		System.out.println(我是解密函数二号("test"));
		System.out.println(我是解密函数一号(我是解密函数一号("test")));
		System.out.println(我是解密函数三号("test", 1234));
		System.out.println(我是解密函数三号("test", v));
	}
}
