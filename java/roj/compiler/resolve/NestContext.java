package roj.compiler.resolve;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import roj.asm.ClassNode;
import roj.asm.FieldNode;
import roj.asm.Opcodes;
import roj.asm.type.IType;
import roj.asm.type.Type;
import roj.collect.ArrayList;
import roj.collect.HashMap;
import roj.collect.LinkedHashMap;
import roj.collect.ToIntMap;
import roj.compiler.CompileContext;
import roj.compiler.CompileUnit;
import roj.compiler.asm.MethodWriter;
import roj.compiler.asm.Variable;
import roj.compiler.ast.expr.Expr;
import roj.compiler.ast.expr.LocalVariable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static roj.asm.Opcodes.ACC_STATIC;

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

	public MethodWriter constructor() {return null;}

	public String nestHost() {return host;}
	public @Nullable FieldNode nestHostRef() {return null;}

	public abstract CompileContext.Import resolveField(CompileContext ctx, ArrayList<NestContext> nestHost, int i, String name);
	public CompileContext.Import resolveMethod(CompileContext ctx, ArrayList<NestContext> nestHost, int i, String name) {return null;}

	public static NestContext anonymous(CompileContext ctx, CompileUnit nest, MethodWriter nestConstructor, List<Expr> hostArgsLoad) {return new NestContext.InnerClass(ctx.file.name(), hostArgsLoad, ctx.variables, nest, nestConstructor);}
	public static NestContext innerClass(CompileContext ctx, String host, CompileUnit nest) {return new NestContext.InnerClass(host, nest);}
	public static NestContext lambda(CompileContext ctx, List<Type> nestArgs, List<Type> hostArgs, List<Expr> hostArgsLoad) {return new NestContext.Lambda(ctx.variables, nestArgs, hostArgs, hostArgsLoad);}

	public static final class InnerClass extends NestContext {
		public static final String FIELD_HOST_REF = "$host";

		// 内部类自身
		private final ClassNode nest;
		// 内部构造函数
		private final MethodWriter constructor;
		private int localSize;
		// 内部字段
		private final ToIntMap<Object> fieldIds = new ToIntMap<>();

		InnerClass(String host, List<Expr> hostArgs, Map<String, Variable> hostVars, CompileUnit nest, MethodWriter nestConstructor) {
			super(host, hostArgs, hostVars);
			this.nest = nest;
			this.constructor = nestConstructor;
			this.localSize = nestConstructor.getLocalSize();
		}

		InnerClass(String host, CompileUnit nest) {
			super(host, null, Collections.emptyMap());
			this.nest = nest;
			this.constructor = null;
			this.localSize = 0;

			fieldIds.putInt(this, nest.getField(FIELD_HOST_REF));
		}

		public MethodWriter constructor() {return constructor;}

		public String nestHost() {return host;}
		public FieldNode nestHostRef() {
			int fid = fieldIds.getOrDefault(this, -1);
			if (fid < 0) {
				Type type = Type.klass(host);
				fid = nest.newField(Opcodes.ACC_SYNTHETIC|Opcodes.ACC_FINAL, FIELD_HOST_REF, type);
				fieldIds.putInt(this, fid);

				copyIn(new Expr() {
					@Override public String toString() {return "this<"+type+">";}
					@Override public IType type() {return type;}
					@Override public void write(MethodWriter cw, boolean noRet) {
						var ctx = CompileContext.get();
						ctx.thisUsed = true;
						cw.vars(Opcodes.ALOAD, ctx.thisSlot);
					}
				}, fid);
			}
			return nest.fields.get(fid);
		}
		private int getVariable(Variable v) {
			int fid = fieldIds.getOrDefault(v, -1);
			if (fid < 0) {
				fid = nest.newField(Opcodes.ACC_SYNTHETIC|Opcodes.ACC_FINAL, "arg$"+v.name, v.type.rawType().toDesc());
				fieldIds.put(v, fid);

				v.implicitCopied = true;
				// to invoke constructor
				copyIn(new LocalVariable(v), fid);
			}
			return fid;
		}
		private void copyIn(Expr external, int fid) {
			var ctx = CompileContext.get();
			ctx.thisUsed = true;

			hostArguments.add(external);

			var type = external.type().rawType();
			constructor.mn.parameters().add(type);

			constructor.insn(Opcodes.ALOAD_0);
			constructor.varLoad(type, localSize);
			constructor.field(Opcodes.PUTFIELD, nest, fid);
			int length = type.length();
			constructor.visitSizeMax(length+1, localSize += length);
		}

		public CompileContext.Import resolveField(CompileContext ctx, ArrayList<NestContext> nestHost, int i, String name) {
			int fid;
			var hostVar = hostVariables.get(name);
			ClassNode owner;
			if (hostVar == null) {
				owner = ctx.compiler.resolve(host);
				fid = owner.getField(name);
				if (fid < 0) return null;
				i--;
			} else {
				owner = nest;
				fid = getVariable(hostVar);
			}

			var chain = new FieldNode[nestHost.size()-i];

			int k = 0;
			for (int j = nestHost.size()-1; j > i; j--) {
				FieldNode ref = nestHost.get(j).nestHostRef();
				if (ref != null) chain[k++] = ref;
			}

			FieldNode fieldNode = owner.fields.get(fid);
			chain[k++] = fieldNode;

			if (k < chain.length) chain = Arrays.copyOf(chain, k);

			return makeChain(ctx, nestHost, null, null, owner == nest || (fieldNode.modifier&Opcodes.ACC_FINAL) != 0, chain);
		}
		public CompileContext.Import resolveMethod(CompileContext ctx, ArrayList<NestContext> nestHost, int i, String name) {
			var host = ctx.compiler.resolve(this.host);
			var v = host.getMethod(name);
			if (v >= 0) {
				i--;
				var chain = new FieldNode[nestHost.size()-i];

				int k = 0;
				for (int j = nestHost.size()-1; j > i; j--) {
					FieldNode ref = nestHost.get(j).nestHostRef();
					if (ref != null) chain[k++] = ref;
				}
				if (k < chain.length) chain = Arrays.copyOf(chain, k);

				return makeChain(ctx, nestHost, host, name, true, chain);
			}
			return null;
		}

		@NotNull
		private static CompileContext.Import makeChain(CompileContext ctx, ArrayList<NestContext> nestHost, ClassNode owner, String name, boolean isFinal, FieldNode... chain) {
			for (int i = nestHost.size() - 1; i >= 0; i--) {
				if (nestHost.get(i) instanceof InnerClass ic) {
					// must use This(), will reference topmost fieldIds
					return new CompileContext.Import(owner, name, Expr.fieldChain(ctx.ep.This().resolve(ctx), ic.nest, null, isFinal, chain));
				}
			}
			throw new AssertionError();
		}
	}
	public static final class Lambda extends NestContext {
		private final Map<Variable, LocalVariable> fieldIds;
		private final List<Type> nestArgs, hostArgs;
		private int localSize;

		public Lambda(Map<String, Variable> hostVars, List<Type> nestArgs, List<Type> hostArgs, List<Expr> hostArgsLoad) {
			super("OIIAOIIA", hostArgsLoad, hostVars);

			this.fieldIds = new LinkedHashMap<>();
			this.nestArgs = nestArgs; // Must be a SubList
			this.hostArgs = hostArgs;
			this.localSize = 0x10000;
		}

		@Override
		public CompileContext.Import resolveField(CompileContext ctx, ArrayList<NestContext> nestHost, int i, String name) {
			var hostVar = hostVariables.get(name);
			if (hostVar == null) return null;

			var nestVar = fieldIds.computeIfAbsent(hostVar, hostVariable -> {
				hostVariable.implicitCopied = true;

				Type rawType = hostVariable.type.rawType();

				nestArgs.add(rawType);
				hostArgs.add(rawType);
				hostArguments.add(new LocalVariable(hostVariable));

				Variable nestVariable = ctx.bp.newVar(name, hostVariable.type);
				nestVariable.pos = 0;
				nestVariable.hasValue = true;
				nestVariable.isFinal = true;
				nestVariable.slot = localSize;
				localSize += rawType.length();

				return new LocalVariable(nestVariable);
			});
			return CompileContext.Import.replace(nestVar);
		}

		public void processVariableIndex(CompileContext ctx, HashMap<String, Variable> variables) {
			if (!ctx.thisUsed) {
				ctx.method.modifier |= ACC_STATIC;
				for (var value : variables.values()) value.slot--;
			}

			for (Variable value : variables.values()) {
				if (value.slot >= 0x10000) value.slot -= 0x10000;
				else value.slot += localSize - 0x10000;
			}
		}
	}
}