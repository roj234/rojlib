package roj.kscript;

import roj.config.ParseException;
import roj.kscript.api.ArgList;
import roj.kscript.api.IObject;
import roj.kscript.asm.Context;
import roj.kscript.func.KFuncNative;
import roj.kscript.func.KFunction;
import roj.kscript.func.KInitializer;
import roj.kscript.parser.KParser;
import roj.kscript.type.*;
import roj.kscript.util.ContextPrimer;
import roj.kscript.util.NaFnHlp;
import roj.kscript.vm.KScriptVM;
import roj.kscript.vm.ScriptException;
import roj.text.TextUtil;
import roj.util.ArrayUtil;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Roj234
 * @since 2021/6/17 0:46
 */
public class Test {
	static long last;

	public static void main(String[] args) throws IOException, ParseException {
		Context root = new Context();
		root.define("println", new KFuncNative() {
			@Override
			public KType invoke(@Nonnull IObject $this, ArgList param) {
				System.out.print(" ");
				final KType base = param.getOr(0, KUndefined.UNDEFINED);
				System.out.println(base.canCastTo(Type.STRING) ? base.asString() : base.toString());
				return KUndefined.UNDEFINED;
			}
		});
		root.define("hash", new KFuncNative() {
			@Override
			public KType invoke(@Nonnull IObject $this, ArgList param) {
				System.out.println(param.get(0) + " is " + System.identityHashCode(param.get(0)));
				return KUndefined.UNDEFINED;
			}
		});
		root.define("timer", new KFuncNative() {
			@Override
			public KType invoke(@Nonnull IObject $this, ArgList param) {
				System.out.println("距离上次调用: " + TextUtil.scaledNumber(System.currentTimeMillis() - last) + "MS");
				last = System.currentTimeMillis();
				return KUndefined.UNDEFINED;
			}
		});
		root.define("stackTrace", new KFuncNative() {
			@Override
			public KType invoke(@Nonnull IObject $this, ArgList param) {
				Throwable t = new Throwable();
				final List<StackTraceElement> trace = param.trace();
				ArrayUtil.inverse(trace);
				trace.add(t.getStackTrace()[0]);

				t.setStackTrace(trace.toArray(new StackTraceElement[trace.size()]));
				t.printStackTrace();
				return KUndefined.UNDEFINED;
			}
		});
		root.define("Array", new KInitializer() {
			@Override
			public KType createInstance(ArgList args) {
				KType t = args.get(0);
				if (t.canCastTo(Type.INT)) {
					if (t.asInt() <= 0) throw new NegativeArraySizeException();
					return new KArray(t.asInt());
				} else {
					return new KArray();
				}
			}
		});
		root.define("Date", new KInitializer() {
			@Override
			public KType createInstance(ArgList args) {
				return KInt.valueOf((int) (System.currentTimeMillis() & Long.MAX_VALUE));
			}
		});
		root.define("Error", new KInitializer() {
			@Override
			public KType createInstance(ArgList args) {
				String reason = args.getOr(0, "");

				ArrayList<StackTraceElement> trace = new ArrayList<>();
				args.trace(trace);

				return new KError(new ScriptException(reason, trace.toArray(new StackTraceElement[trace.size()]), null));
			}
		});

		root.define("Math", NaFnHlp.builder().with("pow", new KFuncNative() {
			@Override
			public KType invoke(@Nonnull IObject $this, ArgList param) {
				return KDouble.valueOf(Math.pow(param.getOr(0, 0d), param.getOr(1, 0d)));
			}
		}).with("sin", new KFuncNative() {
			@Override
			public KType invoke(@Nonnull IObject $this, ArgList param) {
				return KDouble.valueOf(Math.sin(param.getOr(0, 0d)));
			}
		}).with("cos", new KFuncNative() {
			@Override
			public KType invoke(@Nonnull IObject $this, ArgList param) {
				return KDouble.valueOf(Math.cos(param.getOr(0, 0d)));
			}
		}).build());

		KParser parser = new KParser(new ContextPrimer(root));

		KFunction fn = parser.parse(new File(args[0]));

		fn.invoke(KNull.NULL, new Arguments());

		KScriptVM.printStats();
	}
}
