package roj.plugins.obfuscator.naming;

import roj.asm.ClassNode;
import roj.asm.FieldNode;
import roj.asm.MethodNode;
import roj.asm.Opcodes;
import roj.asm.type.Desc;
import roj.asm.util.ClassUtil;
import roj.asm.util.Context;
import roj.asmx.mapper.Mapper;
import roj.collect.MyHashMap;
import roj.collect.MyHashSet;
import roj.compiler.context.GlobalContext;
import roj.compiler.context.LibraryClassLoader;
import roj.compiler.resolve.ComponentList;
import roj.concurrent.TaskHandler;
import roj.crypt.MT19937;
import roj.gui.Profiler;
import roj.io.IOUtil;
import roj.plugins.obfuscator.ObfuscateTask;
import roj.text.CharList;
import roj.text.logging.Level;
import roj.util.ArrayUtil;
import roj.util.PermissionSet;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * @author Roj233
 * @since 2021/7/18 19:06
 */
public class NameObfuscator implements ObfuscateTask {
	public static final int EX_CLASS = 1, EX_FIELD = 2, EX_METHOD = 4;

	private final MyHashSet<String> tempF = new MyHashSet<>(), tempM = new MyHashSet<>();

	public Random rand = new MT19937();
	public NamingPolicy clazz, method, field, param;
	public PermissionSet exclusions = new PermissionSet("/");

	public final Mapper m = new Mapper(true);;
	private Map<String, ClassNode> named;

	public NameObfuscator() {
		m.flag = Mapper.FLAG_FIX_INHERIT|Mapper.MF_FIX_SUBIMPL;
	}

	@Override public boolean isMulti() {return true;}
	@Override public void apply(Context ctx, Random rand) {}

	@Override
	public void forEach(List<Context> arr, MT19937 rand, TaskHandler executor) {
		if (this.rand != null) ArrayUtil.shuffle(arr, this.rand);

		Mapper.LOGGER.setLevel(Level.ALL);

		if (named == null) named = new MyHashMap<>(arr.size());
		else named.clear();

		for (int i = 0; i < arr.size(); i++) {
			ClassNode data = arr.get(i).getData();
			named.put(data.name(), data);
		}

		Profiler.startSection("makeMap");
		Mapper m = this.m; m.clear();

		var gc = new GlobalContext();
		gc.addLibrary(new LibraryClassLoader(null));
		gc.addLibrary(new LibraryClassLoader(NameObfuscator.class.getClassLoader()));
		gc.addLibrary(name -> named.get(name));

		for (int i = 0; i < arr.size(); i++) generateObfuscationMap(gc, arr.get(i));

		try {
			m.saveMap(new File("test.map"));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		Profiler.endStartSection("loadLibraries");
		// TODO other Libraries??
		m.loadLibraries(Collections.singletonList(arr));
		Profiler.endStartSection("packup");
		m.packup();

		Profiler.endStartSection("mapper");
		m.map(arr, executor);
		Profiler.endSection();
	}

	private void generateObfuscationMap(GlobalContext gc, Context c) {
		ClassNode data = c.getData();

		String from = data.name();

		int exclusion = exclusions.get(from, 0);
		if (data.modifier() == Opcodes.ACC_MODULE) return;

		tempF.clear(); tempM.clear();

		String to = clazz == null || (exclusion&EX_CLASS)!=0 ? from : clazz.obfClass(from, m.getClassMap().flip().keySet(), rand);

		Mapper m = this.m;
		if (to != null && m.getClassMap().putIfAbsent(from, to) != null) {
			System.out.println("重复的class name " + from);
		}

		Desc d = ClassUtil.getInstance().sharedDC;
		d.owner = from;
		CharList sb = IOUtil.getSharedCharBuf();

		List<? extends MethodNode> methods = (exclusion&EX_METHOD)!=0 ? Collections.emptyList() : data.methods;
		inheritCheck:
		for (int i = 0; i < methods.size(); i++) {
			MethodNode method = methods.get(i);
			if ((d.name = method.name()).charAt(0) == '<') continue; // clinit, init
			if ((method.modifier&Opcodes.ACC_BRIDGE) != 0) continue;

			int acc = method.modifier;
			if (0 == (acc & (Opcodes.ACC_STATIC | Opcodes.ACC_PRIVATE))) {
				ComponentList ml = gc.getMethodList(c.getData(), method.name());
				List<MethodNode> parentMethods = ml.getMethods();
				for (int j = 0; j < parentMethods.size(); j++) {
					MethodNode parentMethod = parentMethods.get(j);
					if (parentMethod.rawDesc().equals(method.rawDesc()) && ml.isOverriddenMethod(j)) {
						continue inheritCheck;
					}
				}
			}

			d.param = method.rawDesc();
			d.modifier = (char) acc;

			sb.clear();
			if (this.method != null && (exclusions.getBits(sb.append(d.owner).append("//").append(d.name))&EX_METHOD) == 0) {
				String name = this.method.obfName(tempM, d, rand);
				if (name != null) m.getMethodMap().putIfAbsent(d.copy(), name);
			}
		}

		List<? extends FieldNode> fields = (exclusion&EX_FIELD)!=0 ? Collections.emptyList() : data.fields;
		for (int i = 0; i < fields.size(); i++) {
			FieldNode field = fields.get(i);

			d.name = field.name();
			d.param = field.rawDesc();
			d.modifier = field.modifier;

			sb.clear();
			if (this.field != null && (exclusions.getBits(sb.append(d.owner).append("//").append(d.name))&EX_FIELD) == 0) {
				String name = this.field.obfName(tempF, d, rand);
				if (name != null) m.getFieldMap().putIfAbsent(d.copy(), name);
			}
		}
	}
}