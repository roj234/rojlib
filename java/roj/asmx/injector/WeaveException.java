package roj.asmx.injector;

import roj.asmx.TransformException;

/**
 * @author Roj234
 * @since 2023/10/9 18:50
 */
public class WeaveException extends TransformException {
	public WeaveException(String msg) { super(msg); }
	public WeaveException(String msg, Throwable cause) { super(msg, cause); }
}