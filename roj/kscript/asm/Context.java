package roj.kscript.asm;

import org.jetbrains.annotations.ApiStatus.Internal;
import roj.kscript.Constants;
import roj.kscript.api.IObject;
import roj.kscript.type.KType;
import roj.kscript.type.Type;
import roj.kscript.util.JavaException;
import roj.kscript.util.opm.ConstMap;
import roj.kscript.util.opm.KOEntry;

import java.util.Map;

/**
 * @author Roj234
 * @since 2021/4/17 22:52
 */
public class Context extends IContext implements IObject {
	public Context() {
		this(null);
	}

	public Context(Context parent) {
		this.parent = parent;
		this.vars = new ConstMap();
	}

	/**
	 * 闭包：上级变量/作用域 <BR>
	 * class可能为以下几种:
	 * {@link Frame}: 上级函数作用域, 有全局对象(var/const)和部分对象(let)
	 * {@link Context}: 顶层命名空间，例如浏览器中的window, 只有全局对象
	 * {@link Closure}: 导出(作为返回值)的函数，其在return返回下级的所有作用域
	 */
	Context parent;
	ConstMap vars;

	@Override
	public final boolean canCastTo(Type type) {
		return type == Type.OBJECT;
	}

	@Override
	public final boolean isInstanceOf(IObject obj) {
		return obj == this;
	}

	@Override
	public final IObject getProto() {
		return Constants.OBJECT;
	}

	@Override
	public final KType getOr(String key, KType def) {
		return getEx(key, def);
	}

	@Override
	public final int size() {
		return vars.size();
	}

	@Override
	public final Map<String, KType> getInternal() {
		return vars;
	}

	@Internal
	public boolean lock(String id, boolean cfg) {
		KOEntry entry = (KOEntry) vars.getEntry(id);

		if (entry == null) return false;
		if (cfg) {
			entry.flags |= 1;
		} else {
			entry.flags &= ~1;
		}
		return true;

	}

	public final void define(String k, KType v) {
		vars.put(k, v);
	}

	@Override
	public final Type getType() {
		return Type.OBJECT;
	}

	@Override
	public final StringBuilder toString0(StringBuilder sb, int depth) {
		return sb.append("[Context]");
	}

	public final boolean delete(String name) {
		return vars.remove(name) != null;
	}

	KType getEx(String keys, KType def) {
		Context self = this;

		while (self != null) {
			KType v = self.vars.get(keys);
			if (v != null) {
				return v;
			} else {
				self = self.parent;
			}
		}

		return def;
	}

	public void put(String id, KType val) {
		Context self = this;

		while (self != null) {
			KOEntry entry = (KOEntry) self.vars.getEntry(id);
			if (entry != null) {
				if ((entry.flags & 1) == 1) throw new JavaException("尝试写入常量 " + id);
				entry.v = val;
				return;
			}
			self = self.parent;
		}

		throw new JavaException("未定义的 " + id);
	}
}
