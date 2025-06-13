package roj.compiler.plugins.stc;

import org.jetbrains.annotations.Nullable;
import roj.asm.ClassNode;
import roj.asm.MethodNode;
import roj.asm.type.IType;
import roj.asm.type.Type;
import roj.collect.ArrayList;
import roj.collect.HashMap;
import roj.compiler.CompileContext;
import roj.compiler.api.Compiler;
import roj.compiler.api.InvokeHook;
import roj.compiler.asm.MethodWriter;
import roj.compiler.ast.expr.Expr;
import roj.compiler.ast.expr.Invoke;
import roj.compiler.ast.expr.LeftValue;
import roj.compiler.diagnostic.Kind;
import roj.util.ArrayUtil;
import roj.util.TypedKey;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * @author Roj234
 * @since 2024/6/15 15:51
 */
public class StreamChainPlugin implements Compiler.Resolver {
	public static final TypedKey<StreamChainPlugin> INSTANCE = new TypedKey<>("scp:instance");

	private static class ChainExpr extends Expr implements StreamChainExpr {
		private List<Invoke> chain;
		private final LeftValue sourceType;
		private int targetType;

		private Type exactType;
		private final Consumer<StreamChainExpr> callback;
		private MethodWriter mw;

		public ChainExpr(Type exactType, List<Invoke> chain, LeftValue sourceType, Consumer<StreamChainExpr> callback, int flag) {
			this.exactType = exactType;
			this.chain = chain;
			this.sourceType = sourceType;
			this.callback = callback;
			this.targetType = flag;
		}

		@Override public List<Invoke> chain() {return chain;}
		@Override public @Nullable LeftValue sourceType() {return sourceType;}
		@Override public int targetType() {return targetType;}

		@Override public MethodWriter writer() {return mw;}
		@Override public CompileContext context() {return CompileContext.get();}

		@Override public String toString() {return "StreamChainExpr$$";}

		@Override public IType type() {return exactType;}

		@Override
		public void write(MethodWriter cw, boolean noRet) {
			if (noRet) targetType = 2;
			try {
				mw = cw;
				callback.accept(this);
			} catch (Exception e) {
				CompileContext.get().report(Kind.ERROR, e.getMessage());
				e.printStackTrace();
			}
		}
	}

	private static class ChainOp extends InvokeHook {
		private final Type exactType;
		private final Consumer<StreamChainExpr> callback;
		private final int type;

		public ChainOp(boolean allowFallback, Type exactType, Consumer<StreamChainExpr> callback, int type) {
			this.exactType = exactType;
			this.callback = callback;
			this.type = type;
		}

		@Override
		public @Nullable Expr eval(MethodNode owner, @Nullable Expr that, List<Expr> args, Invoke _self) {
			LeftValue loadFrom = null;
			List<Invoke> chain = new ArrayList<>();
			chain.add(_self);
			int i = 0;

			noInverse: {
				while (true) {
					var x = _self.getParent();
					if (x instanceof Invoke inv) {
						chain.add(inv);
						_self = inv;
					} else if (x instanceof LeftValue v) {
						loadFrom = v;
						break;
					} else if (x == null) {
						i++;
						if (getSTATE(chain.get(chain.size() - 1)) != 0) {
							CompileContext.get().report(Kind.ERROR, "streamChain IllegalStart");
							return null;
						}
						break;
					} else {
						if (x instanceof ChainExpr altChain) {
							ArrayUtil.inverse(chain);
							i = altChain.chain.size();
							altChain.chain.addAll(chain);
							chain = altChain.chain;
							break noInverse;
						} else {
							throw new IllegalArgumentException(x.toString());
						}
					}
				}
				ArrayUtil.inverse(chain);
			}

			for (; i < chain.size()-1; i++) {
				if (getSTATE(chain.get(i)) != 1) {
					CompileContext.get().report(Kind.ERROR, "streamChain IllegalIntermediate "+chain.get(i));
					//return null;
				}
			}

			// I must be Terminator method
			// else if intermediateOp => storeToTempVar
			int flag = getSTATE(chain.get(chain.size() - 1)) != 2 ? 1 : 0;
			Type visibleType;
			if (flag == 0) visibleType = owner.returnType();
			else visibleType = new Type.DirtyHacker(exactType.getActualType(), owner.owner());

			if (_self.getParent() instanceof ChainExpr altChain) {
				altChain.targetType = flag;
				altChain.exactType = visibleType;
				assert altChain.callback == callback;
				return altChain;
			}

			return new ChainExpr(visibleType, chain, loadFrom, callback, flag);
		}

		private static int getSTATE(Invoke x) {return ((ChainOp) x.getMethod().getRawAttribute(InvokeHook.NAME)).type;}

		@Override
		public String toString() {
			return "StreamChain[TERMINATE]";
		}
	}

	public void pluginInit(Compiler api) {api.addResolveListener(0, this);}

	private final Map<String, ClassNode> chain = new HashMap<>();
	@Override public ClassNode classResolved(ClassNode info) {return chain.getOrDefault(info.name(), info);}

	public StreamChain newStreamChain(String chainType, boolean allowFallback, Consumer<StreamChainExpr> callback, Type exactType) {
		var ref = new ClassNode();
		ref.name(chainType);
		ref.parent(null);
		chain.put(chainType, ref);

		return new StreamChain() {
			@Override public StreamChain startOp(MethodNode node) {return add(node, 0);}
			@Override public StreamChain intermediateOp(MethodNode node) {return add(node, 1);}
			@Override public StreamChain terminalOp(MethodNode node) {return add(node, 2);}
			private StreamChain add(MethodNode node, int type) {
				node.addAttribute(new ChainOp(allowFallback, exactType, callback, type));
				ref.methods.add(node);
				return this;
			}
		};
	}
}