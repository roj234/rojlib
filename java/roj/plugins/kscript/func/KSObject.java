package roj.plugins.kscript.func;

import roj.config.data.CEntry;
import roj.config.data.CMap;
import roj.plugins.kscript.VariableContext;

import java.util.Collections;
import java.util.Map;

/**
 * @author Roj234
 * @since 2024/11/24 0024 3:52
 */
public class KSObject extends CMap implements VariableContext {
	private VariableContext delegation;
	private CEntry protoChain;

	public KSObject() {this(Constants.OBJECT);}
	public KSObject(CEntry parent) {this.protoChain = parent;}
	public KSObject(VariableContext delegation, CEntry parent) {
		super(Collections.emptyMap());
		this.delegation = delegation;
		this.protoChain = parent;
	}
	public KSObject(Map<String, CEntry> map, CEntry parent) {
		super(map);
		this.protoChain = parent;
	}

	@Override
	public CEntry getOr(String name, CEntry def) {
		CEntry entry = delegation == null ? super.getOr(name, null) : delegation.getVar(name);
		if (entry != null) return entry;

		if (name.equals("__proto__")) return protoChain;
		return protoChain.asMap().getOr(name, def);
	}

	@Override public CEntry getVar(String name) {return get(name);}
	@Override public void putVar(String name, CEntry value) {put(name, value);}
}
