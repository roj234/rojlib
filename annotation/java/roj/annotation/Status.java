package roj.annotation;

import roj.ci.annotation.InRepo;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 仅用于追踪（状态展示）
 * @author Roj234
 * @since 2024/8/6 16:37
 */
@InRepo
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.METHOD)
public @interface Status {}