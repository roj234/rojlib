package roj.compiler.plugins.asm;

import org.jetbrains.annotations.Nullable;
import roj.asm.ClassNode;
import roj.asm.MethodNode;
import roj.asm.Opcodes;
import roj.asm.type.Generic;
import roj.asm.type.Type;
import roj.collect.MyHashMap;
import roj.compiler.api.InvokeHook;
import roj.compiler.asm.MethodWriter;
import roj.compiler.ast.expr.Expr;
import roj.compiler.ast.expr.Invoke;
import roj.compiler.ast.expr.Lambda;
import roj.compiler.context.LocalContext;
import roj.compiler.diagnostic.Kind;
import roj.compiler.plugin.GlobalContextApi;
import roj.compiler.plugin.LavaApi;
import roj.compiler.plugin.LavaPlugin;
import roj.compiler.resolve.TypeCast;
import roj.concurrent.OperationDone;
import roj.reflect.ReflectionUtils;
import roj.util.Helpers;
import roj.util.TypedKey;

import java.util.List;
import java.util.function.Consumer;

/**
 * @author Roj234
 * @since 2024/6/4 22:15
 */
@LavaPlugin(name = "asm", desc = "名字起的奇奇怪怪其实大概挺基础的")
public final class AsmPlugin extends InvokeHook {
	public static final TypedKey<MyHashMap<String, Expr>> INJECT_PROPERTY = new TypedKey<>("asmHook:injected_property");
	private final MyHashMap<String, Expr> properties = new MyHashMap<>();

	public void pluginInit(LavaApi api) {
		var info = api.getClassInfo("roj/compiler/plugins/asm/ASM");
		List<MethodNode> methods = Helpers.cast(info.methods()); methods.clear();

		MethodNode mn;

		mn = new MethodNode(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC, info.name(), "i2z", "(I)Z");
		mn.addAttribute(this);
		methods.add(mn);

		mn = new MethodNode(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC, info.name(), "z2i", "(Z)I");
		mn.addAttribute(this);
		methods.add(mn);

		mn = new MethodNode(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC, info.name(), "inject", "(Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/Object;");
		mn.addAttribute(this);
		methods.add(mn);

		mn = new MethodNode(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC, info.name(), "cast", "(Ljava/lang/Object;)Ljava/lang/Object;");
		mn.addAttribute(this);
		methods.add(mn);

		mn = new MethodNode(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC, info.name(), "asm", "(Lroj/compiler/plugins/asm/WriterImpl;)Z");
		mn.addAttribute(this);
		methods.add(mn);

		api.attachment(INJECT_PROPERTY, properties);
	}

	public AsmPlugin() {}

	@Override public String toString() {return "__asm hook";}

	@SuppressWarnings("unchecked")
	@Override public Expr eval(MethodNode owner, @Nullable Expr self, List<Expr> args, Invoke node) {
		LocalContext ctx = LocalContext.get();
		switch (owner.name()) {
			default: throw OperationDone.NEVER;
			case "i2z", "z2i": return args.get(0);
			case "cast": return new GenericCat(args.get(0));
			case "asm":
				var lambda = (Lambda) args.get(0);
				lambda.write(null, TypeCast.ANYCAST(0, Generic.generic("roj/compiler/plugins/asm/WriterImpl", Type.klass("roj/asm/insn/CodeWriter"))));

				MethodNode impl = lambda.getImpl();
				//impl.parsed(ctx.file.cp);
				ctx.file.methods.remove(impl);

				if (!impl.rawDesc().equals("(Lroj/asm/insn/CodeWriter;)V") || (impl.modifier & Opcodes.ACC_STATIC) == 0) {
					ctx.report(Kind.ERROR, "asm("+lambda+")使用了this或外部参数，不允许");
					return null;
				}

				var newClass = new ClassNode();
				newClass.name("roj/compiler/plugins/asm/impl$"+ReflectionUtils.uniqueId());
				newClass.addInterface("roj/compiler/plugins/asm/WriterImpl");
				newClass.methods.add(impl);
				impl.name("accept");
				// TODO delegation
				//impl.modifier = Opcodes.ACC_PUBLIC;

				try {
					GlobalContextApi capi = (GlobalContextApi) ctx.classes;
					capi.addSandboxWhitelist("roj.compiler.plugins.asm.WriterImpl", false);
					((Consumer<MethodWriter>) capi.createSandboxInstance(newClass)).accept(null);
				} catch (Throwable e) {
					ctx.report(Kind.ERROR, "asm("+lambda+")还没实现喵");
				}

				return Expr.valueOf(false);
			case "inject":
				if (!args.get(0).isConstant()) {
					ctx.report(Kind.ERROR, "inject("+args+")的第一个参数必须是字符串常量");
					return null;
				}

				var replace = properties.get(args.get(0).constVal().toString());
				return replace != null ? replace : args.get(1);
		}
	}

}