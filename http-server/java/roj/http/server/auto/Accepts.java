package roj.http.server.auto;

import org.intellij.lang.annotations.MagicConstant;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Specifies the allowed HTTP methods for a web route annotated with {@link Route}.
 * This annotation allows combining multiple HTTP methods using bitwise flags.
 * By default, routes annotated with {@link Route} allow both GET and POST requests.
 * Use this annotation to explicitly permit additional methods like PUT, DELETE, etc.
 *
 * <p><strong>Example:</strong></p>
 * <pre>{@code
 * @Route("/users")
 * @Accepts(GET | POST | DELETE)
 * public void handleUsers(Request req, Response res) { ... }
 * }</pre>
 *
 * <p>The constants are defined as powers of 2 for bitwise OR operations.</p>
 *
 * @author Roj234
 * @since 2023/2/5 11:34
 * @see Route
 * @see GET
 * @see POST
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface Accepts {
	int GET = 1<<0, POST = 1<<1, PUT = 1<<2, HEAD = 1<<3, DELETE = 1<<4, OPTIONS = 1<<5, TRACE = 1<<6, CONNECT = 1<<7;
	/**
	 * The bitwise OR combination of allowed HTTP methods (e.g., {@code GET | POST}).
	 *
	 * @return the flags representing allowed methods
	 */
	@MagicConstant(flagsFromClass = Accepts.class)
	int value();
}