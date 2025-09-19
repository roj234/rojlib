package roj.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 如果这个方法抛出异常，暂停程序等待调试并生成heapdump
 * 注意：该注解的转换器实现位于test模块，而不是core模块
 * @author Roj234
 * @since 2025/3/23 15:44
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD})
public @interface DumpOnException {}
