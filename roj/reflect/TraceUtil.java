package roj.reflect;

public final class TraceUtil extends SecurityManager {
	public static final TraceUtil INSTANCE = new TraceUtil();
	public static Class<?> getCallerClass1() { return INSTANCE.getCallerClass(); }
	public Class<?> getCallerClass() {
		Class<?>[] ctx = super.getClassContext();
		return ctx.length < 3 ? null : ctx[2];
	}
	@Override
	public Class<?>[] getClassContext() { return super.getClassContext(); }
}