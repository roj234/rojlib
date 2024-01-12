package roj.asmx;

import roj.asm.tree.ConstantData;

/**
 * @author Roj234
 * @since 2024/1/6 0006 23:05
 */
public interface NodeTransformer<T> {
	boolean transform(ConstantData cls, T ctx) throws TransformException;
}