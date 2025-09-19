package roj.asm.type;

import roj.collect.ArrayList;
import roj.io.IOUtil;
import roj.text.CharList;
import roj.util.OperationDone;

import java.util.Collections;
import java.util.List;

/**
 * @author Roj234
 * @since 2022/11/1 11:21
 */
public abstract class IGeneric implements IType {
	public String owner;
	public GenericSub sub;
	public List<IType> typeParameters = Collections.emptyList();

	public IGeneric() {}

	public void addChild(IType child) {
		if (typeParameters.isEmpty()) typeParameters = new ArrayList<>(2);
		typeParameters.add(child);
	}

	public List<IType> childrenRaw() { return typeParameters; }

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
			if (typeParameters.isEmpty()) clone.typeParameters = Collections.emptyList();
			else {
				clone.typeParameters = new ArrayList<>(typeParameters.size());
				for (int i = 0; i < typeParameters.size(); i++)
					clone.typeParameters.add(typeParameters.get(i).clone());
			}
			return clone;
		} catch (CloneNotSupportedException e) {
			throw OperationDone.NEVER;
		}
	}
}