package roj.kscript.vm;

import roj.kscript.type.KInt;
import roj.kscript.type.KType;

/**
 * @author Roj234
 * @since 2021/6/17 17:41
 */
public final class VM_Int extends KInt {
	KScriptVM M;
	int ref;

	VM_Int(KScriptVM man) {
		super(0);
		M = man;
	}

	@Override
	public KType memory(int kind) {
		switch (kind) {
			case 5:
				ref++;
				return this;
			case 6:
				ref--;
				return this;
		}
		return M.allocI(value, kind);
	}
}
