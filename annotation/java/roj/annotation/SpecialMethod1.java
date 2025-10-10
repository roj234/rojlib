package roj.annotation;

import roj.ci.annotation.ExcludeFromArtifact;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author Roj234
 * @since 2025/07/28 12:13
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
@ExcludeFromArtifact
public @interface SpecialMethod1 {}
