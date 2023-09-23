
package roj.asm.nixim;

import org.intellij.lang.annotations.MagicConstant;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface SearchReplace {
	/**
	 * @return ‘/’代表使用方法名 ''代表由NiximHelper实现决定
	 */
	String value() default "";
	@MagicConstant(flagsFromClass = Inject.class)
	int flags() default 0;

	String matcher();

	int[] occurrences() default {};
}
