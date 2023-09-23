package roj.dev.hr;

import roj.util.DynByteBuf;

/**
 * @author Roj234
 * @since 2023/9/24 0024 1:55
 */
public class HRPersistContext {
	public static final HRPersistContext INSTANCE = new HRPersistContext();
	private HRContext context;

	public HRPersistContext() {}

	public void update(DynByteBuf code) {

	}
	public void commit() {

	}

	public Class<?> forName(String name) throws Throwable {
		return null;
	}
}
