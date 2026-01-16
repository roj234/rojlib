package roj.ci.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 将final的实例函数转换为静态函数调用，未实现，而且估计很长一段时间都只起标记作用了。不依靠编译器而是遍历字节码会很影响性能
 * @author Roj234
 * @since 2025/9/22 18:37
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD})
public @interface StaticMethod {}
