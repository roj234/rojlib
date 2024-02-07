package roj.asm.type;

import roj.collect.SimpleList;
import roj.io.IOUtil;
import roj.text.CharList;
import roj.util.Helpers;

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

	public List<IType> childrenRaw() { return children; }

	@Override
	public String toString() {
		CharList cl = IOUtil.getSharedCharBuf();
		toString(cl);
		return cl.toString();
	}

	@Override
	public IGeneric clone() {
		try {
			IGeneric clone = (IGeneric) super.clone();
			clone.sub = sub == null ? null : (GenericSub) sub.clone();
			if (children.isEmpty()) clone.children = Collections.emptyList();
			else {
				clone.children = new SimpleList<>(children.size());
				for (int i = 0; i < children.size(); i++)
					clone.children.add(children.get(i).clone());
			}
			return clone;
		} catch (CloneNotSupportedException e) {
			Helpers.athrow(e);
			return Helpers.nonnull();
		}
	}
}