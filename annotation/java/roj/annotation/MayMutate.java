package roj.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates that the control of a mutable object is transferred to this method.
 * Unless the internal implementation is understood, the parameter should not be used further.
 * <p>
 * When used as a return value annotation, it indicates that the method returns a mutable object
 * (such as a ThreadLocal shared instance) that may be modified by other method calls.
 *
 * @see roj.io.IOUtil#getSharedByteBuf()
 * @author Roj234
 * @since 2024/6/24 8:35
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD})
public @interface MayMutate {
	String value() default "";
}