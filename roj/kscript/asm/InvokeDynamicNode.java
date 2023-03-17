package roj.kscript.asm;

import roj.kscript.api.ArgList;
import roj.kscript.api.IArray;
import roj.kscript.api.IObject;
import roj.kscript.func.KFunction;
import roj.kscript.type.KType;
import roj.kscript.vm.KScriptVM;
import roj.kscript.vm.TCOException;

import java.util.List;

/**
 * @author Roj234
 * @since 2021/6/18 10:06
 */
public final class InvokeDynamicNode extends InvokeNode {
	final long activeBits;

	public InvokeDynamicNode(boolean staticCall, int argCount, boolean noRet, long activeBits) {
		super(staticCall, argCount, noRet);
		this.activeBits = activeBits;
	}

	@Override
	public Opcode getCode() {
		return Opcode.USELESS;
	}

	@Override
	public Node exec(Frame f) {
		KScriptVM.get().pushStack(0);

		List<KType> args;
		int argc = this.argc;
		if (argc != 0) {
			args = KScriptVM.retainArgHolder(argc, true);

			for (int i = argc - 1; i >= 0; i--) {
				KType t = f.pop();
				if ((activeBits & (1L << i)) != 0) {
					IArray array = t.asArray();
					for (int j = 0; j < array.size(); j++) {
						args.add(array.get(j));
					}
				} else {
					args.add(t);
				}
			}
		} else {
			args = null;
		}

		KFunction fn = f.last().asFunction();
		ArgList argList = KScriptVM.retainArgList(this, f, args);

		IObject $this;
		int v = this.flag;
		if ((v & 1) == 0) {
			KType tmp = fn.createInstance(argList);
			if (!(tmp instanceof IObject)) {
				if ((v & 2) == 0) {f.setLast(tmp);} else f.pop();

				KScriptVM.releaseArgList(argList);
				KScriptVM.get().popStack();

				return next;
			}
			$this = (IObject) tmp;
		} else {
			$this = f.$this;
		}
		v &= 2;

		do {
			try {
				KType result = fn.asFunction().invoke($this, argList);

				if (v == 0) {f.setLast(result);} else f.pop();

				KScriptVM.releaseArgList(argList);
				KScriptVM.get().popStack();

				return next;
			} catch (TCOException e) {
				KScriptVM.releaseArgList(argList);

				argList = e.argList;
				fn = e.fn;
				$this = e.$this;
			} catch (Throwable e) { // fake finally
				KScriptVM.releaseArgList(argList);
				KScriptVM.get().popStack();
				throw e;
			}
		} while (true);
	}
}
