package ilib.asm;

import roj.collect.SimpleList;

import net.minecraft.launchwrapper.IClassTransformer;

import java.util.List;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
class WrappedTransformers extends SimpleList<IClassTransformer> {
	WrappedTransformers(List<IClassTransformer> list) {
		super(list);
	}

	@Override
	public boolean add(IClassTransformer tr) {
		super.add(tr);
		Loader.onUpdate(this);
		return true;
	}

	@Override
	public IClassTransformer remove(int index) {
		IClassTransformer b = super.remove(index);
		Loader.onUpdate(this);
		return b;
	}

	@Override
	public IClassTransformer set(int i, IClassTransformer tr) {
		IClassTransformer b = super.set(i, tr);
		Loader.onUpdate(this);
		return b;
	}
}
