package roj.lavac.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author Roj234
 * @since 2023/9/23 0023 19:21
 */
public class ASM {
	public static void asm(String asm) {}

	/**
	 * 在该方法中，可以使用ASM
	 */
	@Retention(RetentionPolicy.SOURCE)
	@Target({ElementType.TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR})
	public @interface User {}
}
