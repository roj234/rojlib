package roj.asmx.injector;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 唯一标识, 与{@link Copy}配合使用，防止字段/方法名称冲突
 * @implNote 被标注元素在生成时会添加随机后缀确保唯一性
 * @author Roj234
 * @since 2023/10/9 19:27
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD, ElementType.FIELD})
public @interface Unique {}
