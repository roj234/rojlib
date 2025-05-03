package roj.asmx.launcher;

/**
 * @author Roj234
 * @since 2025/4/4 20:39
 */
public @interface Conditional {
	String value();
	Class<?>[] itf() default {};
}
