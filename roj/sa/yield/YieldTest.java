package roj.sa.yield;

/**
 * @author Roj234
 * @since 2023/4/20 0020 16:41
 */
public class YieldTest {
	public static Generator<String> looper(int int1, double double1, String string1) {
		for (int i = 0; i < 233; i++) {
			if ((i & 1) == 0) {
				$$$YIELD(i+"是偶数, 共找到"+int1+"个偶数");
			} else {
				$$$YIELD(i+"是奇数, 共找到"+ double1++ +"个奇数");
			}
		}
		return null;
	}

/*
	public  Generator<List<String>> looper2() {
		Generator<String> g = looper();
		while (g.hasNext()) {
			String str = g.next();
			if (str.endsWith("114")) break;

			$$$YIELD(Collections.singletonList(str));
		}
		return null;
	}*/

	private static void $$$YIELD(Object s) {}
}
