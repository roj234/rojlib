package roj.text;

import roj.reflect.ReflectionUtils;

/**
 * @author Roj234
 * @since 2024/3/5 0005 2:14
 */
public class J9String {
	private static final long CODER_OFFSET = ReflectionUtils.fieldOffset(String.class, "coder");
	public static boolean isLatin1(String s) { if (s == null) throw new NullPointerException(); return ReflectionUtils.u.getByte(s, CODER_OFFSET) == 0; }
}