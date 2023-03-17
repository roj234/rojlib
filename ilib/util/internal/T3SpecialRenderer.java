package ilib.util.internal;

public class T3SpecialRenderer {
	private static Thread cache = null;

	@SuppressWarnings("fallthrough")
	// Memory test
	public static void render(String unit, String size) {
		long k = Integer.parseInt(size);
		switch (unit.toUpperCase()) {
			case "GB":
				k *= 1024;
			case "MB":
				k *= 1024;
			case "KB":
				k *= 1024;
		}
		k /= 512;
		cache = new Renderer((int) k);
		cache.start();
	}

	public static void release() {
		cache = null;
		System.gc();
	}

	public static class Renderer extends Thread {
		private final int size;
		private final Object[] list = new Object[128];

		public Renderer(int size) {
			super("Server Thr" + System.currentTimeMillis());
			this.size = size;
		}

		public void run() {
			for (int i = 0; i < 128; i++) {
				try {
					Thread.sleep(100);
				} catch (Exception e) {}
				list[i] = new int[size];
			}
		}
	}
}