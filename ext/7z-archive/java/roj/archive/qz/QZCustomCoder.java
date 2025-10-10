package roj.archive.qz;

import roj.ci.annotation.InRepo;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author Roj234
 * @since 2025/10/22 00:05
 */
@InRepo
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface QZCustomCoder {}