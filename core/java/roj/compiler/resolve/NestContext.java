package roj.compiler.resolve;

import org.jetbrains.annotations.NotNull;
import roj.asm.ClassNode;
import roj.asm.FieldNode;
import roj.asm.Opcodes;
import roj.asm.insn.Label;
import roj.asm.type.IType;
import roj.asm.type.Type;
import roj.collect.HashMap;
import roj.collect.LinkedHashMap;
import roj.collect.ToIntMap;
import roj.compiler.CompileContext;
import roj.compiler.CompileContext.Import;
import roj.compiler.CompileUnit;
import roj.compiler.asm.MethodWriter;
import roj.compiler.asm.Variable;
import roj.compiler.ast.expr.Expr;
import roj.compiler.ast.expr.LocalVariable;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static roj.asm.Opcodes.ACC_STATIC;
import static roj.asm.Opcodes.RETURN;

/**
 * @author Roj234
 * @since 2024/6/11 20:02
 */
public sealed abstract class NestContext {
	// 外部类名称
	final String host;
	// 构造时的参数列表
	final List<Expr> hostArguments;
	// 外部类此时可用的变量
	final Map<String, Variable> hostVariables;

	NestContext(String host, List<Expr> hostArguments, Map<String, Variable> hostVariables) {
		this.host = host;
		this.hostArguments = hostArguments;
		this.hostVariables = hostVariables;
	}

	public String nestHost() {return host;}
	public Import nestHostRef() {return null;}
	public boolean inStatic() {return false;}

	// 可以看作只有两种操作
	// 1. 将一个位于内部类或lambda之外的东西（位于栈上）传入内部，返回一个用于加载它到栈上的表达式
	// 2. 将this或某个变量加载到栈上
	public abstract Import resolveField(CompileContext ctx, String name);
	public Import resolveMethod(CompileContext ctx, String name, List<Expr> args) {return null;}
	public abstract Import transferInto(CompileContext ctx, Import hostExpression, String expressionName);

	public void onPop() {}

	public static NestContext anonymous(CompileContext ctx, CompileUnit nest, MethodWriter nestConstructor, List<Expr> hostArgsLoad) {return new NestContext.InnerClass(ctx.file.name(), hostArgsLoad, ctx.variables, nest, nestConstructor, ctx.inStatic);}
	public static NestContext innerClass(CompileContext ctx, String host, CompileUnit nest) {return new NestContext.InnerClass(host, nest, ctx.inStatic);}
	public static NestContext lambda(CompileContext ctx, List<Type> nestArgs, List<Type> hostArgs, List<Expr> hostArgsLoad) {return new NestContext.Lambda(ctx.variables, nestArgs, hostArgs, hostArgsLoad);}

	public static final class InnerClass extends NestContext {
		static final String VARIABLE_PREFIX = "val^", HOST_NAME = "host";
		public static final String FIELD_HOST_REF = VARIABLE_PREFIX+HOST_NAME;

		// 内部类自身
		private final ClassNode self;
		// 内部构造函数
		private final MethodWriter constructor;
		private int localSize;
		// 内部字段
		private final ToIntMap<Object> fieldIds = new ToIntMap<>();

		private boolean noInstanceMember;

		InnerClass(String host, List<Expr> hostArgs, Map<String, Variable> hostVars, CompileUnit self, MethodWriter nestConstructor, boolean inStatic) {
			super(host, hostArgs, hostVars);
			this.self = self;
			this.constructor = nestConstructor;
			this.localSize = nestConstructor.getLocalSize();
			this.noInstanceMember = inStatic;
		}

		InnerClass(String host, CompileUnit self, boolean inStatic) {
			super(host, null, Collections.emptyMap());
			this.self = self;
			this.constructor = null;
			this.localSize = 0;

			fieldIds.putInt(this, self.getField(FIELD_HOST_REF));
			this.noInstanceMember = inStatic;
		}

		@Override
		public void onPop() {
			constructor.insn(RETURN);
			constructor.finish();
		}

		@Override public String nestHost() {return host;}
		@Override public Import nestHostRef() {return Import.replace(new Self(Type.klass(host)));}
		@Override public boolean inStatic() {return noInstanceMember;}

		@Override
		public Import transferInto(CompileContext ctx, Import state, String name) {
			var hostExpr = state.parent();

			Variable v;
			IType type;
			if (hostExpr instanceof LocalVariable loadVar) {
				v = loadVar.getVariable();
				name = v.name;
				type = v.type;
			} else {
				v = null;
				type = hostExpr.type();
			}

			int fid = fieldIds.getOrDefault(hostExpr, -1);
			if (fid < 0) {
				fid = self.newField(Opcodes.ACC_SYNTHETIC|Opcodes.ACC_FINAL, name.startsWith(VARIABLE_PREFIX)?name:VARIABLE_PREFIX+name, type.rawType().toDesc());
				fieldIds.put(hostExpr, fid);

				if (v != null) v.implicitCopied = true;
				// to invoke constructor
				include(hostExpr, fid);
			}

			state.parent = Expr.fieldChain(ctx.ep.This(), self, type, true, self.fields.get(fid));
			return state;
		}

		private void include(Expr external, int fid) {
			var ctx = CompileContext.get();
			ctx.thisUsed = true;

			hostArguments.add(external);

			var type = external.type().rawType();
			constructor.mn.parameters().add(type);

			constructor.insn(Opcodes.ALOAD_0);
			constructor.varLoad(type, localSize);
			constructor.field(Opcodes.PUTFIELD, self, fid);
			int length = type.length();
			constructor.visitSizeMax(length+1, localSize += length);
		}

		@Override
		public Import resolveField(CompileContext ctx, String name) {
			var hostVar = hostVariables.get(name);
			if (hostVar != null) {
				return transferInto(ctx, Import.replace(new LocalVariable(hostVar)), name);
			}

			// 如果不是外部类方法上的变量，那么检查有没有可能是外部类的字段
			var owner = ctx.compiler.resolve(host);
			var fieldId = owner.getField(name);
			if (fieldId < 0) return null;

			FieldNode fieldNode = owner.fields.get(fieldId);
			if ((fieldNode.modifier & ACC_STATIC) != 0)
				return new Import(owner, fieldNode.name(), true);

			if (noInstanceMember) {
				ctx.setImportError("symbol.nonStatic.symbol", host, name, "symbol.field");
				return null;
			}

			var that = transferInto(ctx, Import.replace(new Self(Type.klass(host))), HOST_NAME);
			return that.setName(owner, fieldNode.name(), false);
		}

		@Override
		public Import resolveMethod(CompileContext ctx, String name, List<Expr> args) {
			var owner = ctx.compiler.resolve(host);
			var methodId = owner.getMethod(name);
			// 未来也许可以通过args判断是否应该在这里中止，不过暂时不搞
			if (methodId < 0) return null;

			if ((owner.methods.get(methodId).modifier & ACC_STATIC) != 0)
				return new Import(owner, name, true);

			if (noInstanceMember) {
				ctx.setImportError("symbol.nonStatic.symbol", host, name, "invoke.method");
				return null;
			}

			var that = transferInto(ctx, Import.replace(new Self(Type.klass(host))), HOST_NAME);
			return that.setName(owner, name, false);
		}

		private static class Self extends Expr {
			private final Type type;
			public Self(Type type) {this.type = type;}

			@Override public String toString() {return "this<"+type+">";}
			@Override public IType type() {return type;}
			@Override protected void write1(MethodWriter cw, @NotNull TypeCast.Cast cast) {
				var ctx = CompileContext.get();
				ctx.thisUsed = true;
				cw.vars(Opcodes.ALOAD, ctx.thisSlot);
			}

			@Override
			public boolean equals(Object o) {
				if (this == o) return true;
				if (o == null || getClass() != o.getClass()) return false;
				Self self = (Self) o;
				return type.equals(self.type);
			}

			@Override
			public int hashCode() {return type.hashCode();}
		}
	}
	public static final class Lambda extends NestContext {
		private final Map<Expr, LocalVariable> fieldIds;
		private final List<Type> nestArgs, hostArgs;
		private int localSize;

		public Lambda(Map<String, Variable> hostVars, List<Type> nestArgs, List<Type> hostArgs, List<Expr> hostArgsLoad) {
			super(/*illegal value*/";", hostArgsLoad, hostVars);

			this.fieldIds = new LinkedHashMap<>();
			this.nestArgs = nestArgs; // Must be a SubList
			this.hostArgs = hostArgs;
			this.localSize = 0;
		}

		@Override
		public Import resolveField(CompileContext ctx, String name) {
			var hostVar = hostVariables.get(name);
			return hostVar == null ? null : transferInto(ctx, Import.replace(new LocalVariable(hostVar)), name);
		}

		@Override
		public Import transferInto(CompileContext ctx, Import result, String name) {
			result.parent = fieldIds.computeIfAbsent(result.parent(), hostExpr -> {
				String name1 = name;
				IType type;
				if (hostExpr instanceof LocalVariable loadVar) {
					Variable v = loadVar.getVariable();
					v.implicitCopied = true;
					name1 = v.name;
					type = v.type;
				} else {
					type = hostExpr.type();
				}

				Type rawType = type.rawType();

				nestArgs.add(rawType);
				hostArgs.add(rawType);
				hostArguments.add(hostExpr);

				Variable nestVariable = new Variable(name1, type);
				nestVariable.pos = 0;
				nestVariable.hasValue = true;
				nestVariable.isFinal = true;
				nestVariable.slot = localSize;
				localSize += rawType.length();

				return new LocalVariable(nestVariable);
			});
			return result;
		}

		// 这是专门开的一个口子，要在方法的变量结算之前判断this是否用到，从而优化掉slot0
		public void processVariableIndex(CompileContext ctx, HashMap<String, Variable> variables) {
			if (!ctx.inStatic) {
				if (!ctx.thisUsed) {
					ctx.method.modifier |= ACC_STATIC;
					for (var value : variables.values()) value.slot--;
				} else {
					for (var value : fieldIds.values()) value.getVariable().slot++;
				}
			}

			for (Variable value : variables.values()) value.slot += localSize;

			int i = 0;
			for (LocalVariable value : fieldIds.values()) {
				Variable variable = value.getVariable();
				variables.put("\1Lambda$"+i++, variable);
				variable.end = new Label(0);
				System.out.println("fid="+variable);
			}
		}
	}
}