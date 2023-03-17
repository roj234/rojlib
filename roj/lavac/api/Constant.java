package roj.lavac.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 应该是一个常量: provided, static final or PreCompile
 *
 * @author Roj233
 * @since 2021/9/2 21:57
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.PARAMETER)
public @interface Constant {}
