package roj.compiler.asm;

import roj.asm.MethodNode;
import roj.asm.type.*;
import roj.collect.ArrayList;
import roj.collect.HashMap;
import roj.collect.LinkedHashMap;
import roj.compiler.CompileContext;
import roj.compiler.api.Types;
import roj.compiler.diagnostic.Kind;

import java.util.Collections;
import java.util.List;

public final class LPSignature extends Signature {
	public static final List<IType> UNBOUNDED_TYPE_PARAM = Collections.singletonList(Types.OBJECT_TYPE);
	private static final Type SKIP = Type.custom('S');

	public LPSignature parent;
	public LPSignature(int type) {
		super(type);
		typeVariables = new LinkedHashMap<>() {
			@Override
			public List<IType> getOrDefault(Object key, List<IType> def) {
				var n = LPSignature.this;
				while (!n.typeVariables.containsKey(key)) {
					n = n.parent;
					if (n == null) return def;
				}
				return ((HashMap<String, List<IType>>)n.typeVariables).getEntry(key).getValue();
			}
		};
	}

	// class decl
	public void _add(IType parent) {
		if (values.isEmpty()) values = new ArrayList<>(3);
		values.add(parent);
	}
	public void _impl(IType itf) {
		if (values.isEmpty()) {
			values = new ArrayList<>(3);
			values.add(Signature.objectBound());
		}
		values.add(itf);
	}

	// method decl
	public void _add(int size, ParameterizedType use) {
		if (values.isEmpty()) values = new ArrayList<>(size+1);
		while (values.size() < size) values.add(SKIP);
		values.add(use);
	}

	public IType returns;

	// 用TypeParam替换LPGeneric的类型变量
	public void applyTypeParam(MethodNode mn) {
		for (List<IType> list : typeVariables.values()) {
			if (list != UNBOUNDED_TYPE_PARAM) applyTypeParam(list);
		}
		applyTypeParam(values);

		if (mn == null) {
			// field
			if (returns != null) {
				values = Collections.singletonList(returns instanceof LavaParameterizedType gp ? applyTypeParam(gp) : returns);
				returns = null;
			}
			return;
		}

		List<Type> list = mn.parameters();
		if (values.isEmpty()) values = new ArrayList<>(list.size() + 1);
		else {
			for (int i = 0; i < values.size(); i++)
				if (values.get(i) == SKIP)
					values.set(i, list.get(i));
		}
		while (values.size() < list.size()) values.add(list.get(values.size()));

		if (returns != null) {
			values.add(returns instanceof LavaParameterizedType gp ? applyTypeParam(gp) : returns);
			returns = null;
		} else {
			values.add(mn.returnType());
		}
	}
	private void applyTypeParam(List<IType> list) {
		for (int i = 0; i < list.size(); i++)
			list.set(i, applyTypeParam(list.get(i)));
	}

	public IType applyTypeParam(IType type) {
		if (type == WildcardType.anyGeneric) return type;

		String owner = type.owner();
		if (owner == null) return type;

		int i = owner.indexOf('/');

		String name = i < 0 ? owner : owner.substring(0, i);
		List<IType> bounds = typeVariables.get(name);
		if (bounds != null) {
			if (type instanceof LavaParameterizedType g && !g.typeParameters.isEmpty()) {
				// 意外的类型
				//  需要: 类
				//  找到: 类型参数T
				CompileContext.get().report(g.pos, Kind.ERROR, "type.parameterizedParam");
			} else {
				// 神奇的语法: <T extends Map> => T.Entry
				if (i >= 0) type.owner(bounds.get(0).owner() + owner.substring(i));
				else return new TypeVariable(owner);
			}
		}

		if (type instanceof LavaParameterizedType g) {
			applyTypeParam(g.typeParameters);

			var x = g.sub;
			while (x != null) {
				applyTypeParam(x.typeParameters);
				x = x.sub;
			}
		}

		return type;
	}

	public void resolve(CompileContext ctx) {
		for (List<IType> value : typeVariables.values()) {
			if (value != UNBOUNDED_TYPE_PARAM) resolve(value, ctx);
		}
		resolve(values, ctx);
	}
	private void resolve(List<IType> list, CompileContext ctx) {
		for (int i = 0; i < list.size(); i++) list.set(i, ctx.resolveType(list.get(i)));
	}

	public boolean isTypeParam(String name) {return typeVariables.get(name) != null;}
	public Type typeParamToBound(Type type) {
		var types = typeVariables.get(type.owner);
		if (types == null) return type;

		for(;;) {
			var gt = types.get(0);
			if (gt.kind() == IType.OBJECT_BOUND) gt = types.get(1);
			if (gt.kind() != IType.TYPE_VARIABLE) return gt.rawType();
			types = typeVariables.get(((TypeVariable) gt).name);
			if (types == null) throw new IllegalStateException("未知的类型参数" + ((TypeVariable) gt).name + "？？？");
		}
	}
}