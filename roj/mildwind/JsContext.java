package roj.mildwind;

import roj.collect.MyHashMap;
import roj.collect.SimpleList;
import roj.mildwind.api.ArgListInternal;
import roj.mildwind.api.Arguments;
import roj.mildwind.bridge.JsJavaClass;
import roj.mildwind.parser.KParser;
import roj.mildwind.parser.ast.ExprParser;
import roj.mildwind.type.*;
import roj.mildwind.util.ObjectShape;
import roj.mildwind.util.TCOException;
import roj.text.TextUtil;

import java.util.ArrayList;

/**
 * @author Roj234
 * @since 2023/6/12 0012 15:45
 */
public final class JsContext {
	public final JsMap OBJECT_PROTOTYPE, ARRAY_PROTOTYPE, NUMBER_PROTOTYPE, FUNCTION_PROTOTYPE, STRING_PROTOTYPE, ERROR_PROTOTYPE;

	public JsMap root;
	public boolean compiling;

	private static final ThreadLocal<JsContext> CURRENT_CONTEXT = new ThreadLocal<>();

	public JsContext() {
		JsContext prev = context();
		setCurrentContext(this);
		try {
			OBJECT_PROTOTYPE = new JsMap(null);
			ARRAY_PROTOTYPE = new JsMap(OBJECT_PROTOTYPE);
			NUMBER_PROTOTYPE = new JsMap(OBJECT_PROTOTYPE);
			FUNCTION_PROTOTYPE = new JsMap(OBJECT_PROTOTYPE);
			STRING_PROTOTYPE = new JsMap(OBJECT_PROTOTYPE);
			ERROR_PROTOTYPE = new JsMap(OBJECT_PROTOTYPE);

			root = createRootMap();
		} finally {
			setCurrentContext(prev);
		}
	}

	private JsMap createRootMap() {
		JsMap root = new JsMap(null);
		root.put("Object", new JsConstructor(OBJECT_PROTOTYPE) {
			@Override
			public JsObject _invoke(JsObject self, Arguments arguments) {
				return null;
			}
		});
		return root;
	}

	public static JsContext context() { return CURRENT_CONTEXT.get(); }
	public static void setCurrentContext(JsContext ctx) { CURRENT_CONTEXT.set(ctx); }

	static final int
		PARSE_MAX = TextUtil.parseInt(System.getProperty("kscript.parser_cache", "32")),
		ARG_MAX = TextUtil.parseInt(System.getProperty("kscript.arg_cache", "64")),
		DEPTH_MAX = TextUtil.parseInt(System.getProperty("kscript.parser_depth", "50"));

	// region parse/invoke cache

	// parser
	private final ExprParser[] h_ep = new ExprParser[PARSE_MAX];
	private final KParser[] h_kp = new KParser[PARSE_MAX];

	// invocation node
	private final ArrayList<Arguments> h_arg = new ArrayList<>();

	public final TCOException localTCOInit = new TCOException();

	public static ExprParser retainExprParser(int depth) {
		if (depth > DEPTH_MAX) throw new IllegalArgumentException("Depth > " + DEPTH_MAX);
		if (depth >= PARSE_MAX) return new ExprParser(depth);

		ExprParser[] arr = context().h_ep;
		if (arr[depth] == null) arr[depth] = new ExprParser(depth);
		return arr[depth];
	}

	public static KParser retainScriptParser(int depth, KParser parent) {
		if (depth > DEPTH_MAX) throw new RuntimeException("Depth > " + DEPTH_MAX);
		if (depth >= PARSE_MAX) return new KParser(depth).reset(parent);

		KParser[] arr = context().h_kp;
		if (arr[depth] == null) arr[depth] = new KParser(depth);
		return arr[depth].reset(parent);
	}

	public static JsObject[][] getClosureArr(int length) {
		return new JsObject[length][];
	}

	// endregion

	public void add(JsInt v) {}
	public void add(JsDouble v) {}
	public void add(JsString v) {}

	public static JsInt getInt(int v) { return new JsInt(context(), v); }
	public static JsDouble getDouble(double v) { return new JsDouble(context(), v); }
	public static JsString getStr(String v) { return new JsString(context(), v); }


	public void add(ArgListInternal internal) {}
	public static ArgListInternal getArguments(int minSize) { return new ArgListInternal(context(), minSize); }

	MyHashMap<String, JsJavaClass> classes = new MyHashMap<>();
	public static JsJavaClass getClassInfo(Class<?> type) {
		JsJavaClass caca = context().classes.get(type.getName());
		if (caca == null) caca = new JsJavaClass(type);
		return caca;
	}

	public ObjectShape shapeFor(JsObject proto) {
		SimpleList<JsObject> par = new SimpleList<>();
		while (proto instanceof JsMap) {
			par.add(proto);
			proto = proto.__proto__();
		}
		return new ObjectShape(par);
	}
}
