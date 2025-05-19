package roj.compiler.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Post-mapping? WIP
 * @author Roj234
 * @since 2025/06/03 09:21
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface ExceptionalStub {
	Class<?> value();
}
