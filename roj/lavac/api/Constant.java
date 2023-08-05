package roj.lavac.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 常量
 * 放在字段：如果该字段赋值表达式使用的方法和字段均为常量（包括该注解），则作为常量写入class
 * 放在方法：表示如果入参确定，则结果确定，编译时会执行该方法，以获得常量
 * 仅支持基本类型，Class（需要JVM能加载它们），String和它们的数组
 * @author Roj233
 * @since 2021/9/2 21:52
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface Constant {
	boolean sandboxed() default true;
}
