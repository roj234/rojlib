package roj.asm.type;

import roj.collect.SimpleList;
import roj.io.IOUtil;
import roj.text.CharList;

import java.util.Collections;
import java.util.List;

/**
 * @author Roj234
 * @since 2022/11/1 11:21
 */
public abstract class IGeneric implements IType {
	public String owner;
	public GenericSub sub;
	public List<IType> children = Collections.emptyList();

	public IGeneric() {}

	public void addChild(IType child) {
		if (children.isEmpty()) children = new SimpleList<>(2);
		children.add(child);
	}

	public List<IType> childrenRaw() {
		return children;
	}

	@Override
	public String toString() {
		CharList cl = IOUtil.getSharedCharBuf();
		toString(cl);
		return cl.toString();
	}
}