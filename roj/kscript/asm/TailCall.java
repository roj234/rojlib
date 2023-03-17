package roj.kscript.asm;

import roj.kscript.api.ArgList;
import roj.kscript.api.IObject;
import roj.kscript.type.KType;
import roj.kscript.vm.KScriptVM;
import roj.kscript.vm.TCOException;

import java.util.List;

/**
 * Tail Call Optimization Part 2
 *
 * @author solo6975
 * @since 2021/6/16 23:14
 */
public class TailCall extends Node {
	final short argc;
	final byte flag;

	public TailCall(InvokeNode self) {
		this.flag = self.flag;
		this.argc = self.argc;
	}

	@Override
	public Opcode getCode() {
		return Opcode.USELESS;
	}

	@Override
	public Node exec(Frame f) {
		// self
		if (f.last(argc) == f.owner) {
			IObject t = f.$this;
			ArgList al;

			if (argc > 0) {
				List<KType> args = KScriptVM.resetArgList(al = f.args, this, f, argc);

				for (int i = argc - 1; i >= 0; i--) {
					args.set(i, f.pop()/*.memory(1)*/);
				}
			} else {
				al = null;
			}

			f.reset();
			if (!f.init(t, al)) throw TCOException.TCO_RESET;
			return f.owner.begin;
		}

		List<KType> args;
		int argc = this.argc;
		if (argc != 0) {
			args = KScriptVM.retainArgHolder(argc, false);

			for (int i = argc - 1; i >= 0; i--) {
				args.set(i, f.pop());
			}
		} else {
			args = null;
		}

		KType fn = f.last();
		ArgList argList = KScriptVM.retainArgList(this, f, args);

		if ((flag & 1) != 0) {
			fn = fn.asFunction().invoke(f.$this, argList);
		}

		// 把控制权返回调用者
		throw KScriptVM.get().localTCOInit.reset(f.$this, argList, fn.asFunction(), flag);
	}

	@Override
	public String toString() {
		String b = "";
		switch (flag) {
			case 0:
				b = "T invoke ";
				break;
			case 1:
				b = "T new ";
				break;
			case 2:
				b = "T void invoke ";
				break;
			case 3:
				b = "T void new ";
				break;
		}
		return b + (argc & 32767);
	}

}