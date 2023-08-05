package roj.launcher;

import roj.asm.TransformException;
import roj.asm.util.Context;

/**
 * @author Roj233
 * @since 2020/11/9 22:39
 */
public interface ITransformer {
	boolean transform(String mappedName, Context ctx) throws TransformException;
}
