package roj.kscript.vm;

import roj.collect.SimpleList;
import roj.kscript.api.ArgList;
import roj.kscript.asm.Frame;
import roj.kscript.asm.Node;
import roj.kscript.func.KFunction;
import roj.kscript.parser.KParser;
import roj.kscript.parser.ast.ExprParser;
import roj.kscript.type.KDouble;
import roj.kscript.type.KInt;
import roj.kscript.type.KType;
import roj.math.MathUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Roj234
 * @since 2021/5/28 12:08
 */
public final class KScriptVM {
	static final ThreadLocal<KScriptVM> managerPerThread = ThreadLocal.withInitial(KScriptVM::new);

	public static KScriptVM get() {
		return managerPerThread.get();
	}

	static final int USAGE_MAX = MathUtils.parseInt(System.getProperty("kscript.usage_cache", "128")), CACHE_MAX = MathUtils.parseInt(
		System.getProperty("kscript.int_cache", "128")), PARSE_MAX = MathUtils.parseInt(System.getProperty("kscript.parser_cache", "32")), ARG_MAX = MathUtils.parseInt(
		System.getProperty("kscript.arg_cache", "64")), DEPTH_MAX = MathUtils.parseInt(System.getProperty("kscript.parser_depth", "50"));

	// region statistic

	static AtomicLong stat_c_all = new AtomicLong(), stat_c_hit = new AtomicLong(), stat_c_ref = new AtomicLong(), stat_c_new = new AtomicLong(), stat_c_force = new AtomicLong();

	public static void printStats() {
		System.out.println(
			"VM.IntCache: called " + stat_c_all.get() + " \r\n" + " - hit:\r\n" + "   - index " + stat_c_hit.get() + ": " + (100 * (double) stat_c_hit.get() / stat_c_all.get()) + "%\r\n" + "   - ref   " + stat_c_ref.get() + ": " + (100 * (double) stat_c_ref.get() / stat_c_all.get()) + "%\r\n" + " - new     " + stat_c_new.get() + ": " + (100 * (double) stat_c_new.get() / stat_c_all.get()) + "%\r\n" + " - force   " + stat_c_force.get() + ": " + (100 * (double) stat_c_force.get() / stat_c_all.get()) + "%\r\n");
	}

	// endregion
	// region parse/invoke cache

	// number parse array
	private final int[] h_np = new int[1];

	// parser
	private final ExprParser[] h_ep = new ExprParser[PARSE_MAX];
	private final KParser[] h_kp = new KParser[PARSE_MAX];

	// invocation node
	private final ArrayList<VM_ArgList> h_arg = new ArrayList<>();
	private final ArrayList<VM_JIT_ArgList> h_arg_jit = new ArrayList<>();
	private final ArrayList<SimpleList<KType>> h_arg_val = new ArrayList<>();

	public final TCOException localTCOInit = new TCOException();

	public static int[] retainNumParseTmp(int radix) {
		int[] arr = get().h_np;
		arr[0] = radix;
		return arr;
	}

	public static ExprParser retainExprParser(int depth) {
		if (depth > DEPTH_MAX) {
			throw new IllegalArgumentException("Depth > " + DEPTH_MAX);
		}

		if (depth >= PARSE_MAX) {
			return new ExprParser(depth);
		}

		ExprParser[] arr = get().h_ep;
		if (arr[depth] == null) {
			arr[depth] = new ExprParser(depth);
		}

		return arr[depth];
	}

	public static KParser retainScriptParser(int depth, KParser parent) {
		if (depth > DEPTH_MAX) {
			throw new RuntimeException("Depth > " + DEPTH_MAX);
		}

		if (depth >= PARSE_MAX) {
			return new KParser(depth).reset(parent);
		}

		KParser[] arr = get().h_kp;
		if (arr[depth] == null) {
			arr[depth] = new KParser(depth);
		}

		return arr[depth].reset(parent);
	}

	public static List<KType> retainArgHolder(int argc, boolean spread) {
		ArrayList<SimpleList<KType>> arr = get().h_arg_val;
		SimpleList<KType> ls;
		if (arr.isEmpty()) {
			ls = new SimpleList<>();
		} else {
			ls = arr.remove(arr.size() - 1);
		}
		ls.ensureCapacity(argc);
		if (!spread) ls.i_setSize(argc);
		return ls;
	}

	public static Frame getCallerFrame(ArgList list) {
		return list instanceof VM_ArgList ? ((VM_ArgList) list).caller : null;
	}

	public static boolean isVMArgList(ArgList list) {
		return list instanceof VM_ArgList;
	}

	public static ArgList retainArgList(Node node, Frame frame, List<KType> argsL) {
		ArrayList<VM_ArgList> arr = get().h_arg;
		return (arr.isEmpty() ? new VM_ArgList() : arr.remove(arr.size() - 1)).reset(node, frame, argsL);
	}

	public static ArgList retainJITArgList(KFunction fn, List<KType> argsL) {
		ArrayList<VM_JIT_ArgList> arr = get().h_arg_jit;
		return (arr.isEmpty() ? new VM_JIT_ArgList() : arr.remove(arr.size() - 1)).reset(fn, argsL);
	}

	public static List<KType> resetArgList(ArgList al, Node node, Frame frame, int argc) {
		VM_ArgList vm_argList = (VM_ArgList) al;
		SimpleList<KType> argv = (SimpleList<KType>) vm_argList.argv;
		vm_argList.reset(node, frame, argv);
		argv.ensureCapacity(argc);
		argv.i_setSize(argc);
		return argv;
	}

	public static void releaseArgList(ArgList args) {
		KScriptVM man = get();

		if (man.h_arg_val.size() < ARG_MAX) {
			List<KType> argv = args.argv;
			if (argv instanceof SimpleList) {
				argv.clear();
				man.h_arg_val.add((SimpleList<KType>) argv);
			}
		}
		args.argv = null;

		if (args instanceof VM_ArgList) {
			if (man.h_arg.size() < ARG_MAX) {
				VM_ArgList args1 = (VM_ArgList) args;

				args1.caller = null;
				args1.from = null;

				man.h_arg.add(args1);
			}
		} else {
			if (man.h_arg_jit.size() < ARG_MAX) {
				VM_JIT_ArgList args1 = (VM_JIT_ArgList) args;

				args1.caller = null;

				man.h_arg_jit.add(args1);
			}
		}
	}

	// endregion
	// region primitive type

	//private VN_Float[] h_f = new VN_Float[1024];
	//private VM_Long[] h_l = new VM_Long[1024];
	private VM_Double[] h_d = new VM_Double[1024];
	private VM_Int[] h_i = new VM_Int[1024];

	private final ArrayList<Usage> usages = new ArrayList<>(), backup = new ArrayList<>();

	private int usage_f, usage_d, usage_i, usage_l, keepAmount, delta_f, delta_d, delta_i, delta_l;

	public void pushStack(int maxKeepAmount) {
		Usage u = backup.isEmpty() ? new Usage() : backup.get(backup.size() - 1);
		u.usage_f = usage_f;
		u.usage_d = usage_d;
		u.usage_i = usage_i;
		u.usage_l = usage_l;
		u.keepAmount = maxKeepAmount;
		keepAmount = maxKeepAmount;
		delta_f = delta_d = delta_i = delta_l = 0;
		usages.add(u);
	}

	public void popStack() {
		Usage u = usages.remove(usages.size() - 1);
		if (backup.size() < USAGE_MAX) backup.add(u);

		usage_f = u.usage_f;
		usage_l = u.usage_l;
		usage_i = u.usage_i;
		usage_d = u.usage_d;
		keepAmount = u.keepAmount;
		delta_f = delta_d = delta_i = delta_l = 0;
	}

	/**
	 * kind =-1: 从变量返回栈上 <br>
	 * kind = 0: 作为本地变量 <br>
	 * // * kind = 1: 作为方法参数 <br>
	 * kind = 2: 复制 <br>
	 * kind = 3: 从栈去外部 <br>
	 * kind = 4: 从外部进入栈
	 */
	public KInt allocI(int v, int kind) {
		stat_c_all.getAndIncrement();

		if (kind == 3) {
			stat_c_new.getAndIncrement();

			return KInt.valueOf(v);
		}

		//if(usage_i > keepAmount) {

		//}

		delta_i++;
		int cur = usage_i++;
		if (h_i.length <= cur) {
			VM_Int[] NaNY = new VM_Int[h_i.length << 1];
			System.arraycopy(h_i, 0, NaNY, 0, h_i.length);
			h_i = NaNY;
		}

		VM_Int h = h_i[cur];
		if (h == null) {
			h = checkFreeI(cur);
		} else {
			stat_c_hit.getAndIncrement();
		}

		switch (kind) {
			case 2:
			case 4:
				h.ref = 1;
		}

		h.value = v;
		return h;
	}

	private VM_Int checkFreeI(int cur) {
		VM_Int[] h_i = this.h_i;
		int e = usage_i - 1;
		for (int i = usage_i - delta_i; i < e; i++) {
			VM_Int vi = h_i[i];
			if (vi.ref <= 0) {
				usage_i--;
				delta_i--;

				stat_c_ref.getAndIncrement();

				return vi;
			}
		}
		stat_c_force.getAndIncrement();
		return h_i[cur] = new VM_Int(this);
	}

	public KDouble allocD(double v, int kind) {
		stat_c_all.getAndIncrement();

		if (kind == 3) {
			stat_c_new.getAndIncrement();

			return new KDouble(v);
		}

		//if(usage_d > keepAmount) {

		//}

		delta_d++;
		int cur = usage_d++;
		if (h_d.length <= cur) {
			VM_Double[] NaNY = new VM_Double[h_d.length << 1];
			System.arraycopy(h_d, 0, NaNY, 0, h_d.length);
			h_d = NaNY;
		}

		VM_Double h = h_d[cur];
		if (h == null) {
			h = checkFreeD(cur);
		} else {
			stat_c_hit.getAndIncrement();
		}

		h.value = v;
		return h;
	}

	private VM_Double checkFreeD(int cur) {
		VM_Double[] h_d = this.h_d;
		int e = usage_d - 1;
		for (int i = usage_d - delta_d; i < e; i++) {
			VM_Double vd = h_d[i];
			if (vd.ref <= 0) {
				usage_d--;
				delta_d--;

				stat_c_ref.getAndIncrement();

				return vd;
			}
		}
		return h_d[cur] = new VM_Double(this);
	}

	static final class Usage {
		int usage_f, usage_d, usage_i, usage_l, keepAmount;
	}
}
