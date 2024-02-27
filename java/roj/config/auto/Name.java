package roj.config.auto;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 序列化后的名称
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.FIELD)
public @interface Name {
	String value();
}