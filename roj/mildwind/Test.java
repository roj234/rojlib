package roj.mildwind;

import roj.config.ParseException;
import roj.mildwind.api.Arguments;
import roj.mildwind.asm.AsmClosure;
import roj.mildwind.bridge.JsJavaArray;
import roj.mildwind.bridge.JsJavaClass;
import roj.mildwind.parser.KParser;
import roj.mildwind.type.JsFunction;
import roj.mildwind.type.JsObject;
import roj.mildwind.type.JsString;

import javax.script.ScriptException;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;

/**
 * @author Roj234
 * @since 2021/6/17 0:46
 */
public class Test {
	public static void main(String[] strArgs) throws IOException, ParseException, ScriptException {
		JsContext ctx = new JsContext();
		JsContext.setCurrentContext(ctx);

		JsJavaClass value = new JsJavaClass(System.class);
		JsObject sysout = value.get("out");
		System.out.println(sysout.get("println")._invoke(sysout, new Arguments(Arrays.asList(new JsString("hello world from javascript")))));

		ctx.root.put("System", value);
		ctx.root.put("test", new JsJavaArray(new int[100]));

		KParser parser = new KParser(new AsmClosure(ctx.root));

		JsFunction fn = parser.parse(new File(strArgs[0]));

		fn._invoke(ctx.root, new Arguments());
	}
}