package roj.compiler.plugins.asm;

import org.jetbrains.annotations.Nullable;
import roj.asm.MethodNode;
import roj.asm.Opcodes;
import roj.collect.MyHashMap;
import roj.compiler.api.Evaluable;
import roj.compiler.ast.expr.ExprNode;
import roj.compiler.ast.expr.Invoke;
import roj.compiler.ast.expr.Lambda;
import roj.compiler.plugin.LavaApi;
import roj.compiler.plugin.LavaPlugin;
import roj.concurrent.OperationDone;
import roj.util.Helpers;
import roj.util.TypedKey;

import java.util.List;

/**
 * @author Roj234
 * @since 2024/6/4 22:15
 */
@LavaPlugin(name = "asm", desc = "名字起的奇奇怪怪其实大概挺基础的")
public final class AsmPlugin extends Evaluable {
	public static final TypedKey<MyHashMap<String, ExprNode>> INJECT_PROPERTY = new TypedKey<>("asmHook:injected_property");
	private final MyHashMap<String, ExprNode> properties = new MyHashMap<>();

	public void pluginInit(LavaApi api) {
		var info = api.getClassInfo("roj/compiler/plugins/asm/ASM");
		List<MethodNode> methods = Helpers.cast(info.methods()); methods.clear();

		MethodNode mn;

		mn = new MethodNode(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC, info.name(), "i2z", "(I)Z");
		mn.putAttr(this);
		methods.add(mn);

		mn = new MethodNode(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC, info.name(), "z2i", "(Z)I");
		mn.putAttr(this);
		methods.add(mn);

		mn = new MethodNode(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC, info.name(), "inject", "(Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/Object;");
		mn.putAttr(this);
		methods.add(mn);

		mn = new MethodNode(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC, info.name(), "cast", "(Ljava/lang/Object;)Ljava/lang/Object;");
		mn.putAttr(this);
		methods.add(mn);

		api.attachment(INJECT_PROPERTY, properties);
	}

	public AsmPlugin() {}

	@Override public String toString() {return "__asm hook";}
	@Override public ExprNode eval(MethodNode owner, @Nullable ExprNode self, List<ExprNode> args, Invoke node) {
		switch (owner.name()) {
			default: throw OperationDone.NEVER;
			case "i2z", "z2i": return args.get(0);
			case "cast": return new GenericCat(args.get(0));
			case "__asm":
				var lambda = (Lambda) args.get(0);
				throw new IllegalArgumentException("未实现");
			case "inject":
				if (!args.get(0).isConstant()) throw new IllegalArgumentException("__inject()的参数必须是常量字符串");

				var replace = properties.get(args.get(0).constVal().toString());
				return replace != null ? replace : args.get(1);
		}
	}

}