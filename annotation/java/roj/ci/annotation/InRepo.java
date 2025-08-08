package roj.ci.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 当项目中任意类具有该注解时，预生成的{@link roj.asmx.AnnotationRepo}将以它们为白名单，否则使用默认的黑名单模式
 * @author Roj234
 * @since 2025/06/14 19:17
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.ANNOTATION_TYPE})
public @interface InRepo {}
