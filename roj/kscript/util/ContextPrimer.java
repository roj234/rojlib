package roj.kscript.util;

import roj.collect.MyHashMap;
import roj.collect.MyHashSet;
import roj.collect.SimpleList;
import roj.collect.ToIntMap;
import roj.kscript.asm.Context;
import roj.kscript.asm.Frame;
import roj.kscript.asm.IContext;
import roj.kscript.asm.Node;
import roj.kscript.parser.ast.Expression;
import roj.kscript.type.KType;
import roj.kscript.util.opm.ConstMap;
import roj.kscript.util.opm.KOEntry;
import roj.util.Helpers;

import java.util.ArrayList;
import java.util.Set;

/**
 * 作用域构建器
 *
 * @author solo6975
 * @since 2020/10/17 23:52
 */
public final class ContextPrimer {
	static final ToIntMap<String> NIL = new ToIntMap<>(0);

	public ArrayList<String> usedArgs = new ArrayList<>();
	public int restParId = -1;

	public final ConstMap globals;
	public ArrayList<Variable> locals = new ArrayList<>();

	public MyHashSet<String> unusedGlobal = new MyHashSet<>();
	private MyHashMap<String, Variable> inRegion = new MyHashMap<>();
	private ToIntMap<String> parameter = NIL;
	public SimpleList<Set<String>> creations = new SimpleList<>();

	public ContextPrimer parent;
	public IContext built;

	public ContextPrimer(Context ctx) {
		this.globals = (ConstMap) ctx.getInternal();
		built = ctx;
	}

	private ContextPrimer(ContextPrimer parent) {
		this.parent = parent;
		this.globals = new ConstMap();
	}

	// region 代码块

	public void enterRegion() {
		creations.add(new MyHashSet<>());
	}

	public void endRegion() {
		for (String name : creations.remove(creations.size() - 1)) {
			inRegion.remove(name);
		}
	}

	public void handle(Frame ctx) {
		built = ctx;

		// gc
		inRegion.clear();
		inRegion = null;
		creations.clear();
		creations = null;
		for (String s : unusedGlobal)
			globals.remove(s);
		unusedGlobal.clear();
		unusedGlobal = null;

		parameter = NIL;
	}

	public void finish() {
		usedArgs.clear();
		usedArgs = null;
		//locals.clear();
		//locals = null;
	}

	// endregion

	public boolean exists(String name) {
		return globals.containsKey(name) || inRegion.containsKey(name) || (parent != null && parent.exists(name));
	}

	public boolean selfExists(String name) {
		return globals.containsKey(name) || inRegion.containsKey(name);
	}

	// region 作用域

	public void global(String key, KType val) {
		if (globals.putIfAbsent(key, val) == null) {
			unusedGlobal.add(key);
		} else {
			unusedGlobal.remove(key);
		}
	}

	public void Const(String key, KType val) {
		if (globals.putIfAbsent(key, val) == null) {
			unusedGlobal.add(key);
		} else {
			unusedGlobal.remove(key);
		}
		globals.markConst(key);
	}

	public void local(String key, KType val, Node node) {
		Variable v = inRegion.get(key);
		if (v == null) {
			inRegion.put(key, v = new Variable(key, val, node, null));
			locals.add(v);
		} else {
			System.err.println("Duplicate " + key);
		}

		v.end = node;

		if (!creations.isEmpty()) creations.get(creations.size() - 1).add(key);
	}

	// endregion

	public void setRestId(int i) {
		restParId = i;
		System.out.println("Rest par is wip");
	}

	public boolean isAdvanced() {
		return false;
	}

	public void setDefault(int parId, Expression expr) {
		System.out.println("Default val is wip");
	}

	public void loadPar(int id, String as) {
		if (parent == null) {
			throw new IllegalArgumentException("<global> function doesn't have any parameter.");
		}

		KType v = globals.get(as);
		if (v == null) {
			globals.put(as, null);

			usedArgs.ensureCapacity(id);
			while (usedArgs.size() <= id) {
				usedArgs.add(null);
			}
			usedArgs.set(id, as);
		}
	}

	public ContextPrimer makeChild() {
		return new ContextPrimer(this);
	}

	public boolean isConst(String name) {
		KOEntry entry = (KOEntry) globals.getEntry(name);
		return entry != null && (entry.flags & 1) != 0;
	}

	public void updateRegion(String key, Node last) {
		Variable v = inRegion.get(key);
		if (v == null) {
			return;
		}

		v.end = last;
	}

	public void chainUpdate(String key) {
		if (!unusedGlobal.remove(key) && !globals.containsKey(key) && !parameter.containsKey(key) && parent != null) parent.chainUpdate(key);
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder("Ctx{");
		if (!globals.isEmpty()) {
			sb.append("vars={");
			final Set<KOEntry> set = Helpers.cast(globals.entrySet());
			for (KOEntry entry : set) {
				sb.append(entry.getKey()).append('=').append(entry.getValue());
				if ((entry.flags & 1) != 0) sb.append("[final]");
				sb.append(',');
			}
			sb.setCharAt(sb.length() - 1, '}');
			sb.append(", ");
		}
		if (!locals.isEmpty()) {
			sb.append("lets=").append(locals).append(", ");
		}
		if (parent != null) sb.append("parent=").append(parent).append(", ");
		return sb.append('}').toString();
	}

	public Frame findProvider(String key) {
		return globals.containsKey(key) || inLocal(key) ? (Frame) this.built : !(parent.built instanceof Frame) ? null : parent.findProvider(key);
	}

	private boolean inLocal(String key) {
		if (locals == null) return false;
		for (int i = 0; i < locals.size(); i++) {
			Variable variable = locals.get(i);
			if (variable.name.equals(key)) return true;
		}
		return false;
	}

	public void setParameter(ToIntMap<String> parameter) {
		this.parameter = parameter;
	}

	public boolean top() {
		return parent == null || !(parent.built instanceof Frame);
	}
}
