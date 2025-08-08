import *

public class Test {
	static void println(String x) {System.out.println(x);}

	public static void main(String[] args) {

	}

	private static void tryCatch(int flag) {
		block:
		try {
			println("enter");
			if (flag == 0) throw new AssertionError();
			else if (flag == 1) break block;
			else if (flag == 2) return;
			println("exit");
		} catch (Exception e) {
			println("catch");
		}
	}
	private static void tryFinally(int flag) {
		block:
		try {
			println("enter");
			if (flag == 0) throw new AssertionError();
			else if (flag == 1) break block;
			else if (flag == 2) return;
			println("exit");
		} finally {
			println("finally");
		}
	}
	private static void tryCatchFinally(int flag) {
		block:
		try {
			println("enter");
			if (flag == 0) throw new AssertionError();
			else if (flag == 1) break block;
			else if (flag == 2) return;
			println("exit");
		} catch (Exception e) {
			println("catch");
		} finally {
			println("finally");
		}
	}
	private static void tryWithResource(int flag) {

	}
	private static void tryCatchWithResource(int flag) {

	}
	private static void tryCatchWithResourceFinally(int flag) {

	}
	private static void enclosing(int flag) {
		block: {
			try {
				try {
					println("code");
					if (flag == 0) throw new AssertionError();

					else if (flag == 1) break block;

					else if (flag == 2) return;
				} finally {
					println("inner finally");
				}

				// will never reach here
				// System.out.println("outer execute");
			} finally {
				println("outer finally");
			}

		}
	}
}
