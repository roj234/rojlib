package roj.test.internal;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * @author Roj234
 * @since 2024/5/23 0023 0:44
 */
@Retention(RetentionPolicy.CLASS)
public @interface Test {
	String value();
}