package roj.reflect;

public final class TraceUtil extends SecurityManager {
	public static final TraceUtil INSTANCE = new TraceUtil();
	static final boolean ok;

	static {
		boolean a;
		try {
			H.JLA.getClass();
			a = true;
		} catch (Throwable e) {
			a = false;
		}
		ok = a;
	}

	public Class<?> getCallerClass() {
		Class<?>[] ctx = super.getClassContext();
		return ctx.length < 3 ? null : ctx[2];
	}

	@Override
	public Class<?>[] getClassContext() {
		return super.getClassContext();
	}

	@Override
	public int classDepth(String name) {
		return super.classDepth(name);
	}

	public static Class<?> getCallerClass1() {
		return INSTANCE.getCallerClass();
	}

	public static int classDepth1(String name) {
		return INSTANCE.classDepth(name);
	}

	public static StackTraceElement[] getTraces(Throwable t) {
		if (ok) {
			StackTraceElement[] arr = new StackTraceElement[H.JLA.getStackTraceDepth(t)];
			for (int i = 0; i < arr.length; i++) {
				arr[i] = H.JLA.getStackTraceElement(t, i);
			}
			return arr;
		}
		return t.getStackTrace();
	}

	public static int stackDepth(Throwable t) {
		if (ok) return H.JLA.getStackTraceDepth(t);
		return t.getStackTrace().length;
	}

	static class H {
		static final sun.misc.JavaLangAccess JLA = sun.misc.SharedSecrets.getJavaLangAccess();
	}
}