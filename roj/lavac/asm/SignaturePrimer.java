package roj.lavac.asm;

import roj.asm.tree.MethodNode;
import roj.asm.type.Generic;
import roj.asm.type.IType;
import roj.asm.type.Signature;
import roj.asm.type.Type;
import roj.collect.LinkedMyHashMap;
import roj.collect.SimpleList;
import roj.lavac.parser.CompileUnit;

import java.util.List;

public class SignaturePrimer extends Signature {
	public Generic returns;

	private String current;
	public SignaturePrimer parent;

	public SignaturePrimer(int type) {
		super(type);
		typeParams = new LinkedMyHashMap<>();
	}

	public boolean addTypeParam(String name) {
		current = name;
		return null == typeParams.put(name, new SimpleList<>());
	}

	public void addBound(IType g) {
		typeParams.get(current).add(g);
	}

	public void _add(IType parent) {
		if (values.isEmpty()) {
			values = new SimpleList<>(3);
		}
		values.add(parent);
	}

	public void _add(int size, Generic use) {
		if (values.isEmpty()) {
			values = new SimpleList<>(size + 1);
		}
		while (values.size() < size) values.add(object);
		values.add(use);
	}

	public void _impl(IType itf) {
		if (values.isEmpty()) {
			values = new SimpleList<>(3);
			values.add(new Type("java/lang/Object"));
		}
		values.add(itf);
	}

	static final Type object = new Type("java/lang/Object");

	public void initS0(MethodNode mn) {
		for (List<IType> list : typeParams.values()) {
			if (list.isEmpty()) list.add(object);
		}
		if (mn == null) {
			// field
			if (returns != null) {
				if (values.isEmpty()) values = new SimpleList<>(1);
				values.add(returns);
				returns = null;
			}
			return;
		}

		List<Type> list = mn.parameters();
		if (values.isEmpty()) values = new SimpleList<>(list.size() + 1);

		for (int i = 0; i < values.size(); i++) {
			if (values.get(i) == object) {
				values.set(i, list.get(i));
			}
		}
		while (values.size() < list.size()) values.add(list.get(values.size()));

		if (returns != null) {
			values.add(returns);
			returns = null;
		} else {
			values.add(mn.returnType());
		}
	}

	public void initS1(CompileUnit file) {
		for (List<IType> list : typeParams.values()) {
			for (int i = 0; i < list.size(); i++) {
				IType t = list.get(i);
				file._resolve(t, "ANNOTATION_TYPE");
				if (t instanceof GenericPrimer)
					((GenericPrimer) t).resolveS2(file, "ANNOTATION_TYPE");
			}
		}
		for (int i = 0; i < values.size(); i++) {
			IType t = values.get(i);
			file._resolve(t, "ANNOTATION_VALUE");
			if (t instanceof GenericPrimer)
				((GenericPrimer) t).resolveS2(file, "ANNOTATION_TYPE");
		}
	}

	public CharSequence getTypeBoundary(CharSequence name) {
		SignaturePrimer sp = this;
		do {
			List<IType> list = sp.typeParams.get(name);
			if (list != null) {
				if (list.isEmpty()) return "java/lang/Object";

				IType g = list.get(0);
				if (g.genericType() != 2) return g.owner();

				System.out.println("name="+name);
				// todo? fix for <T extends Y<?>> from class
				System.out.println("type boundary for " + name + " is " + g);
				if (sp.parent == null) throw new NullPointerException("type param not found ?");
				return sp.parent.getTypeBoundary(g.owner());
			}
			sp = sp.parent;
		} while (sp != null);
		return name;
	}

	public boolean hasTypeParam(CharSequence name) {
		SignaturePrimer sp = this;
		do {
			List<IType> list = sp.typeParams.get(name);
			if (list != null) return true;
			sp = sp.parent;
		} while (sp != null);
		return false;
	}
}
