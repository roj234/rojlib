import *

public class Test {
	static void println(String x) {System.out.println(x);}

	public static void main(String[] args) {
		tryWithResource(1);
		tryWithResource(0);
		enclosing(2);
		enclosing(1);
		enclosing(0);
	}

	static class C implements AutoCloseable {
		public void close() {}
	}

	private static void tryWithResource(int flag) {
		try (var closeable = C()) {
			if (flag == 0) throw new AssertionError();
			println(closeable.toString());
		} catch (Exception n) {
			n.printStackTrace();
		}
	}
	private static void tryCatchWithResourceFinally(int flag) {
		try (var closeable = C()) {
			if (flag == 0) throw new AssertionError();
			println(closeable.toString());
		} catch (Exception n) {
			n.printStackTrace();
		} finally {
			println("objk");
		}
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
