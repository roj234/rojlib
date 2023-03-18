package roj.mildwind.type;

import roj.mildwind.JsContext;
import roj.mildwind.api.Arguments;
import roj.mildwind.util.ScriptException;
import roj.mildwind.util.TCOException;

/**
 * @author Roj234
 * @since 2023/6/12 0012 14:48
 */
public abstract class JsFunction extends JsMap {
	public JsFunction() { this(JsContext.context().FUNCTION_PROTOTYPE); }
	public JsFunction(JsMap proto) { super(proto); }

	public final Type type() { return Type.FUNCTION; }

	public final boolean op_instanceof(JsObject object) {
		if (!object.type().object()) return false;
		JsObject prototype = prototype();
		if (!prototype.type().object()) throw new ScriptException("Function has non-object prototype '"+prototype+"' in instanceof check");

		JsObject proto = object.__proto__();
		while (proto != null) {
			proto = proto.__proto__();
			if (proto == prototype) return true;
		}
		return false;
	}

	public abstract JsObject _invoke(JsObject self, Arguments arguments);

	public boolean hasTCO() { return false; }

	public static JsObject invokeTCO(JsFunction fn, JsObject self, Arguments args) {
		while (true) {
			try {
				return self == null ? fn._new(args) : fn._invoke(self, args);
			} catch (TCOException e) {
				fn = e.fn;
				self = e.self;
				args = e.args;
			} catch (ScriptException e) {
				throw e;
			} catch (Throwable e) {
				throw new ScriptException("execution failed", e);
			}
		}
	}
}
