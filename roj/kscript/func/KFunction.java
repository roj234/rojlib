package roj.kscript.func;

import roj.kscript.Constants;
import roj.kscript.api.ArgList;
import roj.kscript.api.IObject;
import roj.kscript.asm.Frame;
import roj.kscript.type.KInstance;
import roj.kscript.type.KObject;
import roj.kscript.type.KType;
import roj.kscript.type.Type;
import roj.kscript.util.opm.ObjectPropMap;

import javax.annotation.Nonnull;

/**
 * @author Roj234
 * @since 2021/6/16 20:28
 */
public abstract class KFunction extends KObject {
	protected String name, source, clazz;

	public KFunction() {
		super(new ObjectPropMap(), Constants.FUNCTION);
		this.put("prototype", new KObject(Constants.OBJECT));
		this.chmod("prototype", false, true, null, null);
	}

	@Override
	public Type getType() {
		return Type.FUNCTION;
	}

	@Override
	public KFunction asFunction() {
		return this;
	}

	public abstract KType invoke(@Nonnull IObject $this, ArgList param);

	/**
	 * 函数名称 <BR>
	 * eg: func
	 */
	public String getName() {
		return name == null ? "<anonymous>" : name;
	}

	/**
	 * 函数全称 - name <BR>
	 * eg: <global>.funcA.funcB.funcC.
	 */
	public String getClassName() {
		return clazz;
	}

	public KFunction set(String source, String name, String clazz) {
		this.source = source;
		this.name = name;
		this.clazz = clazz;
		return this;
	}

	@Override
	public KType copy() {
		return this;
	}

	/**
	 * 文件名 <BR>
	 * eg: test.js
	 */
	public String getSource() {
		return source == null ? getClass().getSimpleName() + ".java" : source;
	}

	public KType createInstance(ArgList args) {
		return new KInstance(this, get("prototype").asKObject());
	}

	public KFunction onReturn(Frame frame) {
		return this;
	}
}
