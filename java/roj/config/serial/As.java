package roj.config.serial;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 使该字段是用指定的自定义序列化器
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.FIELD)
public @interface As {
	String value();
}
