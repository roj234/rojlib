package roj.compiler.ast.expr;

import org.jetbrains.annotations.Nullable;
import roj.asm.Opcodes;
import roj.asm.cp.Constant;
import roj.asm.cp.*;
import roj.asm.tree.ConstantData;
import roj.asm.tree.MethodNode;
import roj.asm.tree.attr.BootstrapMethods;
import roj.asm.type.IType;
import roj.asm.type.Type;
import roj.asm.type.TypeHelper;
import roj.compiler.CompilerSpec;
import roj.compiler.JavaLexer;
import roj.compiler.asm.Asterisk;
import roj.compiler.asm.MethodWriter;
import roj.compiler.ast.ParseTask;
import roj.compiler.context.LocalContext;
import roj.compiler.diagnostic.Kind;
import roj.compiler.resolve.ComponentList;
import roj.compiler.resolve.MethodResult;
import roj.compiler.resolve.ResolveException;
import roj.compiler.resolve.TypeCast;
import roj.config.ParseException;
import roj.text.TextUtil;
import roj.util.Helpers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Lambda要么是方法参数，要么是Assign的目标
 * @author Roj234
 * @since 2024/1/23 0023 11:32
 */
public class Lambda extends ExprNode {
	private static final Asterisk[] AnyLambda = new Asterisk[10];
	static {
		for (int i = 0; i < 10; i++) AnyLambda[i] = new Asterisk("<Lambda/"+i+">");
	}
	public static int getLambdaArgCount(IType anyLambda) {
		String name;
		if (anyLambda.genericType() != IType.ASTERISK_TYPE || !(name = anyLambda.toString()).startsWith("<Lambda/")) return -1;

		char c = name.charAt(8);
		return c != '/' ? c - '0' : Integer.parseInt(name.substring(8, name.length() - 1));
	}

	private List<String> args;
	private ExprNode expr;
	private MethodNode mn;
	private String isMethodRef;
	private ParseTask task;

	// args[0] -> expr
	public Lambda(List<String> args, ExprNode expr) {
		this.args = args;
		this.expr = expr;
	}
	// args -> {...}
	public Lambda(List<String> args, MethodNode mn, ParseTask task) {
		this.args = args;
		this.mn = mn;
		this.task = task;
	}
	// parent::methodRef
	public Lambda(ExprNode parent, String methodRef) {
		this.expr = parent;
		this.isMethodRef = methodRef;
	}

	@Override
	public String toString() { return (args.size() == 1 ? args.get(0) : "("+TextUtil.join(args, ", ")+")")+" -> "+expr; }
	@Override
	public IType type() {return args.size() < 10 ? AnyLambda[args.size()] : new Asterisk("<Lambda//"+args.size()+">");}
	@Override
	public ExprNode resolve(LocalContext ctx) throws ResolveException {
		ctx.file.setMinimumBinaryCompatibility(CompilerSpec.COMPATIBILITY_LEVEL_JAVA_8);

		if (isMethodRef != null) {
			if (expr instanceof DotGet d) {
				expr = null;
				ExprNode next = d.resolveEx(ctx, n -> expr = new roj.compiler.ast.expr.Constant(new Type(n.name()), new CstClass(n.name())));
				if (expr == null) expr = next;
			} else {
				expr = expr.resolve(ctx);
			}
		}
		return this;
	}

	@Override
	public final void write(MethodWriter cw, boolean noRet) {write(cw, null);}
	@Override
	public void write(MethodWriter cw, @Nullable TypeCast.Cast returnType) {
		var ctx = LocalContext.get();

		if (returnType == null) {
			ctx.report(Kind.ERROR, "lambda.untyped");
			return;
		}

		var info = ctx.classes.getClassInfo(returnType.getType1().owner());
		var rh = ctx.classes.getResolveHelper(info);
		int lambdaType = rh.getLambdaType();

		switch (lambdaType) {
			default -> ctx.report(Kind.ERROR, "lambda.unsupported."+lambdaType);
			case 1 -> {
				// interface lambda
			}
			case 2 -> {
				// abstract class lambda
				ctx.report(Kind.INCOMPATIBLE, "抽象类自动lambda转化暂未实现，请耐心等待！");
			}
		}

		MethodNode method = rh.getLambdaMethod();
		MethodResult r = ctx.inferrer.getGenericParameters(info, method, returnType.getType1());

		CstRef ref;
		if (isMethodRef != null) {
			ConstantData info1 = ctx.classes.getClassInfo(expr.type().owner());
			ComponentList list = ctx.methodListOrReport(info1, isMethodRef);
			if (list == null) {
				ctx.report(Kind.ERROR, "lambda.notfound");
				return;
			}

			MethodResult r1 = list.findMethod(ctx, Helpers.cast(method.parameters()), 0);
			if (r1 == null) return;

			var m = r1.method;
			ref = (info1.modifier()&Opcodes.ACC_INTERFACE) != 0
				? ctx.file.cp.getItfRef(m.owner, m.name(), m.rawDesc())
				: ctx.file.cp.getMethodRef(m.owner, m.name(), m.rawDesc());
		} else if (mn != null) {
			//TODO mn can be null
			// ctx.variableTransfer = new MyHashSet<>();

			List<Type> collect = new ArrayList<>();
			for (IType type : r.desc) {
				Type rawType = type.rawType();
				collect.add(rawType);
			}
			Type remove = collect.remove(collect.size() - 1);
			mn.name("lambda$1");
			mn.parameters().clear();
			mn.parameters().addAll(Helpers.cast(collect));
			mn.setReturnType(remove);

			try {
				LocalContext next = LocalContext.next();
				next.setClass(ctx.file);
				next.setMethod(mn);
				next.lexer.state = JavaLexer.STATE_EXPR;
				task.parse(next);
				LocalContext.prev();
			} catch (ParseException e) {
				e.printStackTrace();
			}

			ref = ctx.file.cp.getMethodRef(ctx.file.name, mn.name(), mn.rawDesc());
		} else {
			//ctx.enclosingThis.add(new TransitiveContext(ctx.file));

			mn = new MethodNode(0, ctx.file.name, "lambda$1", "()V");
			MethodWriter cw1 = ctx.classes.createMethodPoet(ctx.file, mn);

			ExprNode node = expr.resolve(ctx);
			//node.writeDyn(cw1, );

			ref = null;
		}

		if (r.exception != null) {
			// TODO method throws
		}

		// TODO also needed to transfer variable

		String actualDesc = r.desc == null ? method.rawDesc() : TypeHelper.getMethod(Arrays.stream(r.desc).map(IType::rawType).collect(Collectors.toList()));

		var item = createRef(method, ref, actualDesc);
		int tableIdx = ctx.file.addLambdaRef(item);
		System.out.println(item);

		cw.invokeDyn(tableIdx, method.name(), ref.descType(), 0);
	}

	private static BootstrapMethods.Item createRef(MethodNode mn, CstRef actualMethod, String actualDesc) {
		// 前三个参数由VM提供
		// MethodHandles.Lookup caller
		// String interfaceMethodName
		// MethodType factoryType
		// 后三个参数由这里的arguments提供, 分别是(describeConstantable)
		// CMethodType: 接口方法的真实参数
		//   (Ljava/lang/Object;)Ljava/lang/Object;
		// CMethodHandle: 实际调用的方法
		//   REF_invokeStatic java/lang/String.valueOf:(Ljava/lang/Object;)Ljava/lang/String;
		// CMethodType: 接口方法的泛型参数 (在调用时验证)
		//   (Ljava/lang/Integer;)Ljava/lang/String;
		return new BootstrapMethods.Item("java/lang/invoke/LambdaMetafactory", "metafactory", "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;" +
			"Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;", BootstrapMethods.Kind.INVOKESTATIC,
			Constant.METHOD, Arrays.asList(new CstMethodType(mn.rawDesc()), new CstMethodHandle(BootstrapMethods.Kind.INVOKESTATIC, actualMethod), new CstMethodType(actualDesc)));
	}
}