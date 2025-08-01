package roj.compiler.ast.expr;

import org.jetbrains.annotations.Nullable;
import roj.asm.ClassDefinition;
import roj.asm.ClassNode;
import roj.asm.MethodNode;
import roj.asm.Opcodes;
import roj.asm.cp.CstClass;
import roj.asm.type.IType;
import roj.asm.type.Type;
import roj.collect.ArrayList;
import roj.compiler.CompileContext;
import roj.compiler.Tokens;
import roj.compiler.api.Compiler;
import roj.compiler.asm.Asterisk;
import roj.compiler.asm.LambdaCall;
import roj.compiler.asm.MethodWriter;
import roj.compiler.ast.ParseTask;
import roj.compiler.diagnostic.Kind;
import roj.compiler.resolve.NestContext;
import roj.compiler.resolve.ResolveException;
import roj.compiler.resolve.TypeCast;
import roj.config.ParseException;
import roj.text.TextUtil;
import roj.util.Helpers;

import java.util.Collections;
import java.util.List;

/**
 * Lambda要么是方法参数，要么是Assign的目标
 * @author Roj234
 * @since 2024/1/23 11:32
 */
public final class Lambda extends Expr {
	public static final int ARGC_UNKNOWN = 0x10000;
	public static final IType ARGC_UNKNOWN_TYPE = new Asterisk("<Lambda//"+ARGC_UNKNOWN+">");

	private static final Asterisk[] cache = new Asterisk[10];
	static {
		for (int i = 0; i < 10; i++) cache[i] = new Asterisk("<Lambda/"+i+">");
	}
	public static int getLambdaArgc(IType lambdaArgcType) {
		String name;
		if (lambdaArgcType.genericType() != IType.ASTERISK_TYPE || !(name = lambdaArgcType.toString()).startsWith("<Lambda/")) return -1;

		char c = name.charAt(8);
		return c != '/' ? c - '0' : Integer.parseInt(name.substring(8, name.length() - 1));
	}
	public static IType getLambdaArgcType(int argc) {return argc < 10 ? cache[argc] : new Asterisk("<Lambda//"+argc+">");}

	private List<?> args;
	private MethodNode impl;
	private ParseTask task;

	private Expr methodRef;
	private String methodName;

	public MethodNode getImpl() {return impl;}

	// args -> {...}
	public Lambda(List<String> args, MethodNode impl, ParseTask task) {
		this.args = args;
		this.impl = impl;
		this.task = task;
	}
	// parent::methodRef
	public Lambda(Expr methodRef, String methodName) {
		this.args = Collections.emptyList();
		this.methodRef = methodRef;
		this.methodName = methodName;
	}

	@Override
	public String toString() {return methodRef == null ? (args.size() == 1 ? args.get(0) : "("+TextUtil.join(args, ", ")+")")+" -> "+impl : methodRef+"::"+methodName;}
	@Override
	public IType type() {
		return methodRef != null && args == Collections.emptyList()
				? ARGC_UNKNOWN_TYPE
				: getLambdaArgcType(args.size());
	}
	@Override
	public Expr resolve(CompileContext ctx) throws ResolveException {
		if (methodRef != null) {
			if (methodRef instanceof MemberAccess d) {
				methodRef = null;
				Expr next = d.resolveEx(ctx, n1 -> {
					var n = (ClassDefinition) n1;
					methodRef = constant(Type.klass(n.name()), new CstClass(n.name()));
				}, null);
				if (methodRef == null) methodRef = next;
			} else {
				methodRef = methodRef.resolve(ctx);
			}

			var data = ctx.resolve(methodRef.type());
			int mid = -1;
			var methods = data.methods();
			for (int i = 0; i < methods.size(); i++) {
				var method = methods.get(i);
				if (methodName.equals(method.name())) {
					if (mid >= 0) {
						//ctx.report(this, Kind.WARNING, "lambda unfriendly overloading");
						args = Collections.emptyList();
						break;
					}
					mid = i;
					args = method.parameters();
				}
			}
		}
		return this;
	}

	@Override
	public final void write(MethodWriter cw, boolean noRet) {write(cw, null);}
	@Override
	public void write(MethodWriter cw, @Nullable TypeCast.Cast _returnType) {
		var ctx = CompileContext.get();
		if (_returnType == null) {
			ctx.report(this, Kind.ERROR, "lambda.untyped");
			return;
		}

		IType lambda目标类型 = _returnType.getType1();
		var lambda接口 = ctx.compiler.resolve(lambda目标类型.owner());

		var resolveHelper = ctx.compiler.link(lambda接口);
		int lambda转换方式 = resolveHelper.getLambdaType();
		var lambda方法 = resolveHelper.getLambdaMethod();
		LambdaCall generator = LambdaCall.INVOKE_DYNAMIC;
		switch (lambda转换方式) {
			default -> {
				ctx.report(this, Kind.ERROR, "lambda.unsupported."+lambda转换方式);
				return;
			}
			case 1 -> {
				if (ctx.compiler.getMaximumBinaryCompatibility() < Compiler.JAVA_8)
					generator = LambdaCall.ANONYMOUS_CLASS;
				else
					ctx.file.setMinimumBinaryCompatibility(Compiler.JAVA_8);
			}
			case 2 -> generator = LambdaCall.ANONYMOUS_CLASS;
		}

		var lambda实参 = ctx.inferrer.getGenericParameters(lambda接口, lambda方法, lambda目标类型).rawDesc();

		var lambda入参 = new ArrayList<Type>();
		List<Expr> lambda入参的取值表达式;

		ClassNode lambda实现所属的类;

		if (methodRef != null) {
			lambda实现所属的类 = ctx.compiler.resolve(methodRef.type().owner());

			List<Type> args = Type.methodDesc(lambda实参);
			args.remove(args.size()-1);

			var r = ctx.getMethodListOrReport(lambda实现所属的类, methodName, methodRef).findMethod(ctx, Helpers.cast(args), 0);
			if (r == null) return;

			// 方法引用
			impl = r.method;

			lambda入参的取值表达式 = Collections.emptyList();

			// This
			if ((impl.modifier&Opcodes.ACC_STATIC) == 0) {
				methodRef.write(cw, false);
				lambda入参.add(0, methodRef.type().rawType());
			} else if (!(methodRef instanceof Literal)) {
				ctx.report(this, Kind.WARNING, "symbol.warn.static_on_half", lambda实现所属的类, methodName, this);
			}
		} else {
			ctx.file.methods.add(impl);
			// 块lambda
			impl.name(ctx.method.name()+"^lambda"+ctx.nameIndex++);
			impl.rawDesc(lambda实参);

			lambda入参的取值表达式 = new ArrayList<>();
			// lambda参数注入 (逆向出来的，我也不知道去哪找文档看):
			// lambda实现的签名：隐含的this,[注入参数,lambda参数]
			// invokeDyn的签名: lambda类 lambda方法名 (this, 注入参数)
			ctx.enclosing.add(NestContext.lambda(ctx, impl.parameters().subList(0, 0), lambda入参, lambda入参的取值表达式));

			var next = CompileContext.next();
			try {
				next.isArgumentDynamic = true;
				next.setClass(ctx.file);
				next.lexer.state = Tokens.STATE_EXPR;
				task.parse(next);
				ctx.file._setCtx(ctx);
			} catch (ParseException e) {
				throw new ResolveException("Lambda表达式解析失败", e);
			} finally {
				next.isArgumentDynamic = false;
				CompileContext.prev();
				ctx.enclosing.pop();
			}

			lambda实现所属的类 = ctx.file;

			// This
			if ((impl.modifier&Opcodes.ACC_STATIC) == 0) {
				ctx.thisUsed = true;
				cw.vars(Opcodes.ALOAD, ctx.thisSlot);
				lambda入参.add(0, Type.klass(ctx.file.name()));
			}
		}

		if (cw == null) return;

		// 注入参数
		for (var node : lambda入参的取值表达式) node.write(cw, false);
		// lambda类 (returnType)
		lambda入参.add(Type.klass(lambda接口.name()));

		generator.generate(ctx, cw, lambda接口, lambda方法, lambda实参, lambda入参, lambda实现所属的类, impl);
	}
}