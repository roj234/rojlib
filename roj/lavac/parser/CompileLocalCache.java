package roj.lavac.parser;

import roj.asm.tree.IClass;
import roj.asm.type.IType;
import roj.collect.MyHashMap;
import roj.collect.MyHashSet;
import roj.collect.SimpleList;
import roj.lavac.asm.AnnotationPrimer;
import roj.lavac.asm.GenericPrimer;
import roj.lavac.block.BlockParser;
import roj.lavac.expr.ExprParser;
import roj.math.MutableInt;
import roj.text.CharList;

import java.util.List;

public class CompileLocalCache {
	private static final ThreadLocal<List<Object>> FTL = ThreadLocal.withInitial(() -> {
		SimpleList<Object> list = new SimpleList<>(2);
		list.add(new MutableInt(1));
		list.add(new CompileLocalCache());
		return list;
	});

	public static CompileLocalCache get() {
		List<Object> list = FTL.get();
		return (CompileLocalCache) list.get(((Number) list.get(0)).intValue());
	}

	public static void depth(int ud) {
		List<Object> list = FTL.get();
		MutableInt mi = (MutableInt) list.get(0);
		int v = mi.addAndGet(ud);
		if (v >= list.size()) list.add(new CompileLocalCache());
	}

	public List<GenericPrimer> genericDeDup = new SimpleList<>();
	public MyHashSet<IType> toResolve_unc = new MyHashSet<>();

	public MyHashSet<String> names = new MyHashSet<>();
	public SimpleList<AnnotationPrimer> annotationTmp = new SimpleList<>();
	public CharList tmpList = new CharList();

	public MyHashMap<String, IClass> importCache = new MyHashMap<>();
	public MyHashSet<String> annotationMissed = new MyHashSet<>();

	public ExprParser ep = new ExprParser(0);
	public BlockParser bp = new BlockParser(0);
}
