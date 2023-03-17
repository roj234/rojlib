package roj.lavac.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 让这个类的实例可以被switch (注:会退化成二分法，但是O(log N) << O(N))
 * 标记的函数有且仅有一个, 参数为: static int x(Instance inst);
 * @author Roj234
 * @since 2022/10/23 0023 13:27
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.METHOD)
public @interface Switchable {}
