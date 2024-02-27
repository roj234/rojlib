package roj.net.http.server.auto;

import org.intellij.lang.annotations.MagicConstant;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author Roj234
 * @since 2023/2/5 0005 11:34
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface Accepts {
	int GET = 1<<0, POST = 1<<1, PUT = 1<<2, HEAD = 1<<3, DELETE = 1<<4, OPTIONS = 1<<5, TRACE = 1<<6, CONNECT = 1<<7;
	@MagicConstant(flagsFromClass = Accepts.class)
	int value();
}