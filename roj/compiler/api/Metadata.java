package roj.compiler.api;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * {@link roj.util.ArrayUtil#pack(byte[])}
 * @author Roj234
 * @since 2023/9/23 0023 19:25
 */
@Retention(RetentionPolicy.CLASS)
@Target({}) // on method field type
@interface Metadata { String value(); }