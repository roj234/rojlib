package roj.asm.nixim;

import roj.asm.TransformException;

/**
 * @author Roj234
 * @since 2023/10/9 0009 18:50
 */
public class NiximException extends TransformException {
	public NiximException(String msg) {
		super(msg);
	}
	public NiximException(String msg, Throwable cause) {
		super(msg, cause);
	}
}
