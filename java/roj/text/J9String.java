package roj.text;

import roj.io.FastFailException;
import roj.reflect.ReflectionUtils;

/**
 * @author Roj234
 * @since 2024/3/5 0005 2:14
 */
public class J9String {
	static {
		if (ReflectionUtils.JAVA_VERSION < 9) throw new FastFailException("J9String must be running on Java 9 or higher JVMs");
	}

	private static final long CODER_OFFSET = ReflectionUtils.fieldOffset(String.class, "coder");
	public static boolean isLatin1(String s) { return ReflectionUtils.u.getByte(s, CODER_OFFSET) == 0; }
}