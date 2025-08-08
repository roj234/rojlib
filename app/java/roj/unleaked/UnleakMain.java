package roj.unleaked;

import roj.archive.zip.ZipArchive;
import roj.asm.AsmCache;
import roj.asm.ClassNode;
import roj.asm.Opcodes;
import roj.asm.attr.Attribute;
import roj.asm.insn.Code;
import roj.asm.insn.CodeWriter;
import roj.asm.insn.InsnNode;
import roj.asmx.Context;
import roj.asmx.MethodHook;
import roj.config.ConfigMaster;
import roj.io.IOUtil;
import roj.util.DynByteBuf;

import javax.crypto.Cipher;
import java.io.File;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * @author Roj234
 * @since 2024/8/14 13:18
 */
public class UnleakMain extends MethodHook {
	static final UnleakMain instance = new UnleakMain();

	static boolean hijackEnabled;
	static ClassNode payloadData;
	static MethodHandle lastHandle;
	static Object theArgument;

	public static void main(String[] args) {
		var dir = new File(args[0]);
		for (File file : dir.listFiles()) {
			if (file.isFile()) UnleakMain.run(file);
		}
	}

	public static void run(File thePlugin) {
		try(var zipArchive = new ZipArchive(thePlugin)) {
			String main = ConfigMaster.YAML.parse(zipArchive.getStream("plugin.yml")).asMap().getString("main").replace('.', '/') + ".class";
			byte[] bytes = zipArchive.get(main);
			ClassNode mainClass = ClassNode.parseAll(bytes);

			Code code = mainClass.getMethodObj("<init>", "()V").getAttribute(mainClass.cp, Attribute.Code);
			for (InsnNode insn : code.instructions) {
				var s = insn.descOrNull();
				if (s != null && s.owner.indexOf('/') < 0 && s.owner.length() > 20) {
					var pcl = new Sandbox(zipArchive);
					hijackEnabled = false;

					// 尝试找到一开始的调用
					String decipherName = insn.prev().prev().constant().getEasyCompareValue();
					String loaderName = s.owner;

					Class<?> decipher = pcl.loadClass(decipherName);
					Class<?> loader = pcl.loadClass(loaderName);

					String theInitialization = insn.desc().name;

					Method m = loader.getDeclaredMethod(theInitialization, Object.class);
					m.invoke(null, decipher);

					// OK, we have assigned this field
					hijackEnabled = false;
					lastHandle.invokeWithArguments(theArgument);

					var newLoader = new ClassNode();
					newLoader.name(loaderName);
					CodeWriter c = newLoader.newMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, theInitialization, "(Ljava/lang/Object;)V");
					for (Field field : decipher.getDeclaredFields()) {
						Object o = field.get(null);
						if (o != null) {
							c.ldc(o.toString());
							c.field(Opcodes.PUTSTATIC, decipherName.replace('.', '/'), field.getName(), "Ljava/lang/String;");
						}
					}
					c.visitSize(2, 1);
					c.insn(Opcodes.RETURN);
					c.finish();

					IOUtil.copyFile(thePlugin, new File(thePlugin.getAbsolutePath() + ".bak"));
					zipArchive.put(loaderName+".class", DynByteBuf.wrap(AsmCache.toByteArray(newLoader)));
					zipArchive.save();
					return;
				}
			}


		} catch (Throwable e) {
			e.printStackTrace();
		}
	}

	public static byte[] hijackDoFinal(Cipher cipher, byte[] t) throws Exception {
		var ctx = new Context("name", cipher.doFinal(t));
		instance.transform("name", ctx);
		var classData = ctx.getClassBytes().toByteArray();
		payloadData = ClassNode.parseAll(classData);
		hijackEnabled = true;
		return classData;
	}

	public static MethodHandle hook_findStatic(MethodHandles.Lookup lookup, Class<?> type, String name, MethodType desc) throws Exception {
		var handle = lookup.findStatic(type, name, desc);
		if (hijackEnabled && lookup.lookupClass().getName().equals(payloadData.name().replace('/', '.'))) {
			lastHandle = handle;
			return MethodHandles.lookup().findStatic(UnleakMain.class, "doNothing", desc);
		}
		return handle;
	}

	public static MethodHandle hook_findVirtual(MethodHandles.Lookup lookup, Class<?> type, String name, MethodType desc) throws Exception {
		if (type == Cipher.class && name.equals("doFinal")) return MethodHandles.lookup().findStatic(UnleakMain.class, "hijackDoFinal", MethodType.methodType(byte[].class, Cipher.class, byte[].class));
		return lookup.findVirtual(type, name, desc);
	}

	public static void doNothing() {}
	public static void doNothing(Object o) {theArgument = o;}
}
