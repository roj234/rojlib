package roj.asmx;

import roj.asm.util.Context;

/**
 * @author Roj233
 * @since 2020/11/9 22:39
 */
public interface ITransformer {
	boolean transform(String name, Context ctx) throws TransformException;
}