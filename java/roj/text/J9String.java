package roj.text;

import roj.reflect.ReflectionUtils;

import java.util.Objects;

/**
 * @author Roj234
 * @since 2024/3/5 0005 2:14
 */
public class J9String {
	private static final long CODER_OFFSET = ReflectionUtils.fieldOffset(String.class, "coder");
	public static boolean isLatin1(String s) {return ReflectionUtils.u.getByte(Objects.requireNonNull(s), CODER_OFFSET) == 0;}
}