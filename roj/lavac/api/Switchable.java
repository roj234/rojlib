package roj.lavac.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 让这个类的实例可以被switch
 * case必须是常量(public static final)
 * 为了防止bug，不能在static{}中使用
 * （实质是ToIntMap）
 * @author Roj234
 * @since 2022/10/23 0023 13:27
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface Switchable {}
