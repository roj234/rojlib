package roj.compiler.types;

import org.jetbrains.annotations.Nullable;
import roj.asm.Attributed;
import roj.asm.ClassNode;
import roj.asm.MethodNode;
import roj.asm.attr.Attribute;
import roj.asm.attr.ClassListAttribute;
import roj.asm.type.*;
import roj.collect.ArrayList;
import roj.collect.ToIntMap;
import roj.compiler.CompileContext;
import roj.compiler.api.Types;
import roj.compiler.diagnostic.Kind;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static roj.asm.type.IType.UNBOUNDED_WILDCARD;

public final class SignatureBuilder extends Signature {
	private static final Object[] UNBOUNDED_TYPE_PARAM = {Types.OBJECT_TYPE};
	public static TypeVariableDeclaration newUnbounded(String name) {return new TypeVariableDeclaration(name, UNBOUNDED_TYPE_PARAM);}

	private static final Type SKIP = Type.klass("");

	public SignatureBuilder parent;
	public SignatureBuilder(int type) {
		super(type);
		typeVariables = new TVDSet();
	}

	public void set(int size, IType type) {
		if (values == Collections.EMPTY_LIST) values = new ArrayList<>(size+1);

		while (values.size() <= size) values.add(SKIP);
		if (values.get(size) != SKIP) return;

		values.set(size, type);
	}

	public void setException(int size, IType type) {
		if (exceptions == Collections.EMPTY_LIST) exceptions = new ArrayList<>(size+1);

		while (exceptions.size() <= size) exceptions.add(SKIP);
		if (exceptions.get(size) != SKIP) return;

		exceptions.set(size, type);
	}

	public IType returns;

	// 用TypeVariable取代Type.klass引用的
	public void applyTypeParam(Attributed node) {
		for (var decl : typeVariables) {
			if (decl.getInternalArray() != UNBOUNDED_TYPE_PARAM) applyTypeParam(decl);
		}
		applyTypeParam(values);

		if (node instanceof ClassNode cn) {
			var interfaces = cn.interfaces();
			if (values == Collections.EMPTY_LIST) values = new ArrayList<>(interfaces.size() + 1);

			String parent = cn.parent();
			set(0, parent.equals("java/lang/Object") ? objectBound() : Type.klass(parent));

			for (int i = 0; i < interfaces.size(); i++) set(i+1, Type.klass(interfaces.get(i)));

			return;
		}

		if (node instanceof MethodNode mn) {
			List<Type> parameters = mn.parameters();
			if (values == Collections.EMPTY_LIST) values = new ArrayList<>(parameters.size() + 1);

			for (int i = 0; i < parameters.size(); i++) set(i, parameters.get(i));

			if (returns != null) {
				values.add(applyTypeParam(returns));
				returns = null;
			} else {
				values.add(mn.returnType());
			}

			ClassListAttribute excList = mn.getAttribute(null, Attribute.Exceptions);
			if (excList != null) {
				List<String> value = excList.value;
				for (int i = 0; i < value.size(); i++) {
					setException(i, Type.klass(value.get(i)));
				}
			}

			return;
		}

		// field node
		if (returns != null) {
			// values must be mutable
			//noinspection ArraysAsListWithZeroOrOneArgument
			values = Arrays.asList(applyTypeParam(returns));
			returns = null;
		}
	}
	private void applyTypeParam(List<IType> list) {
		for (int i = 0; i < list.size(); i++)
			list.set(i, applyTypeParam(list.get(i)));
	}

	public IType applyTypeParam(IType type) {
		if (type == Types.anyGeneric || type.kind() == UNBOUNDED_WILDCARD) return type;

		String owner = type.owner();
		if (owner == null) return type;

		int i = owner.indexOf('/');

		String name = i < 0 ? owner : owner.substring(0, i);
		var bounds = resolveTypeVariable(name);
		if (bounds != null) {
			if (type instanceof LavaParameterizedType g && !g.typeParameters.isEmpty()) {
				// 意外的类型
				//  需要: 类
				//  找到: 类型参数T
				CompileContext.get().report(g.pos, Kind.ERROR, "type.parameterizedParam");
			} else {
				// TODO 神奇的语法: <T extends Map> => T.Entry
				if (i >= 0) type.owner(bounds.get(0).owner() + owner.substring(i));
				else return new TypeVariable(bounds);
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

	@Override
	public @Nullable TypeVariableDeclaration resolveTypeVariable(String name) {
		SignatureBuilder self = this;
		do {
			var decl = self.typeVariables.get(name);
			if (decl != null) return decl;

			self = self.parent;
		} while (self != null && self != self.parent);
		return null;
	}

	public void resolve(CompileContext ctx) {
		ToIntMap<Object> states = new ToIntMap<>();
		for (var decl : typeVariables) {
			if (decl.getInternalArray() != UNBOUNDED_TYPE_PARAM) {
				validateHierarchy(decl, states);
				resolve(decl, ctx);
			}
		}
		resolve(values, ctx);
	}

	private void validateHierarchy(TypeVariableDeclaration decl, ToIntMap<Object> states) {
		int status = states.getOrDefault(decl, 0);
		if (status == 2) return;
		if (status == 1) {
			CompileContext.get().report(Kind.ERROR, "semantic.resolution.cyclicDepend", decl.name);
			return;
		}
		states.putInt(decl, 1);

		for (IType bound : decl) {
			validateHierarchy(bound, states);
		}

		states.putInt(decl, 2);
	}
	private void validateHierarchy(IType type, ToIntMap<Object> states) {
		if (type instanceof TypeVariable tv) {
			validateHierarchy(tv.decl, states);
		}
		// F-Bounded Polymorphism
		/* else if (type instanceof ParameterizedType pt) {
			IGeneric x = pt;
			while (x != null) {
				for (IType type1 : x.typeParameters) {
					validateHierarchy(type1, states);
				}
				x = pt.sub;
			}
		}*/
	}

	private void resolve(List<IType> list, CompileContext ctx) {
		for (int i = 0; i < list.size(); i++) list.set(i, ctx.resolveType(list.get(i)));
	}

	public Type getErasuredType(IType type) {
		List<IType> bounds;
		if (type.isPrimitive()
			|| (bounds = resolveTypeVariable(type instanceof TypeVariable tv ? tv.name() : type.owner())) == null
		) return type.rawType();

		for(;;) {
			var bound = bounds.get(0);
			if (bound.kind() == IType.OBJECT_BOUND) bound = bounds.get(1);
			if (bound.kind() != IType.TYPE_VARIABLE) return bound.rawType();

			String name = ((TypeVariable) bound).name();
			bounds = resolveTypeVariable(name);
			if (bounds == null) throw new IllegalStateException("未知的类型参数"+name);
		}
	}
}