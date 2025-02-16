package roj.plugins.ci.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 动态权限标识
 * * WIP
 * * 可能需要Lava编译器
 * @author Roj234
 * @since 2025/3/15 2:22
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.FIELD})
public @interface ActualModifier {
	int value();
}
