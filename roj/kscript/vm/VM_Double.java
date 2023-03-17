package roj.kscript.vm;

import roj.kscript.type.KDouble;
import roj.kscript.type.KType;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/6/17 17:41
 */
public final class VM_Double extends KDouble {
	KScriptVM M;
	int ref;

	VM_Double(KScriptVM man) {
		super(0);
		M = man;
	}

	@Override
	public KType memory(int kind) {
		switch (kind) {
			case 0:
			case 5:
				ref++;
				return this;
			case 6:
				ref--;
				return this;
		}
		return M.allocD(value, kind);
	}
}
