package roj.compiler.asm;

import roj.asm.tree.MethodNode;
import roj.asm.type.Generic;
import roj.asm.type.IType;
import roj.asm.type.Signature;
import roj.asm.type.Type;
import roj.collect.LinkedMyHashMap;
import roj.collect.SimpleList;
import roj.compiler.context.LocalContext;

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
			else init1(list);
		}
		init1(values);

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
	private void init1(List<IType> list) {
		for (int i = 0; i < list.size(); i++) {
			IType type = list.get(i);
			if (type instanceof GenericPrimer gp)
				list.set(i, gp.toRealType(this));
		}
	}

	// 查找类型为Type的类型变量，并用TypeParam替换他们
	public void resolveS2() {
		LocalContext ctx = LocalContext.get();
		for (List<IType> value : typeParams.values())
			resolve1(value, ctx);
		resolve1(values, ctx);
	}
	private void resolve1(List<IType> list, LocalContext ctx) {
		for (int i = 0; i < list.size(); i++) {
			IType type = list.get(i);
			if (type instanceof GenericPrimer gp) gp.initS2(ctx);
			ctx.resolveType(type);
		}
	}

	public CharSequence getTypeBound(CharSequence name) {
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
				return sp.parent.getTypeBound(g.owner());
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