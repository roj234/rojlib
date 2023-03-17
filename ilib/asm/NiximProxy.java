package ilib.asm;

import ilib.Config;
import ilib.api.ContextClassTransformer;
import roj.asm.Parser;
import roj.asm.TransformException;
import roj.asm.nixim.NiximSystem;
import roj.asm.tree.MoFNode;
import roj.asm.tree.anno.AnnValString;
import roj.asm.tree.attr.Attribute;
import roj.asm.tree.attr.InnerClasses;
import roj.asm.util.AccessFlag;
import roj.asm.util.Context;
import roj.asm.util.TransformUtil;
import roj.collect.MyHashSet;
import roj.io.FastFailException;
import roj.io.IOUtil;
import roj.reflect.ClassDefiner;
import roj.text.CharList;
import roj.util.ByteList;
import roj.util.Helpers;

import net.minecraftforge.common.ForgeVersion;

import javax.annotation.Nonnull;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * @author Roj234
 * @since 2021/6/18 9:51
 */
public final class NiximProxy extends NiximSystem implements ContextClassTransformer {
	public static final NiximProxy instance = new NiximProxy();
	private static final MyHashSet<String> accessWidener = new MyHashSet<>();

	static void Nx(String file) {
		if (file.charAt(0) == '!') {
			CharList sb = IOUtil.getSharedCharBuf();
			sb.append("ilib/asm/nx/").append(file, 1, file.length());
			if (!file.endsWith(".class")) sb.append(".class");
			file = sb.toString();
		}

		if (Config.controlViaFile) {
			if (!Config.instance.getConfig().getOrCreateMap("每文件控制.Nixim").putIfAbsent(file, true)) return;
		}

		try {
			InputStream in = NiximProxy.class.getClassLoader().getResourceAsStream(file);
			if (in == null) throw new FileNotFoundException(file);

			ByteList buf = IOUtil.getSharedByteBuf();
			Context ctx = new Context("", buf.readStreamFully(in));

			instance.loadCtx(ctx);
			postProcess(ctx);
		} catch (TransformException | IOException e) {
			Helpers.athrow(e);
		}
	}

	public static void Nx(@Nonnull byte[] data) {
		try {
			Context ctx = new Context("", data);
			instance.loadCtx(ctx);
			postProcess(ctx);
		} catch (TransformException e) {
			Helpers.athrow(e);
		}
	}

	public static void niximUser(String file, boolean isIgnoredBy) {
		if (file.charAt(0) == '!') {
			CharList sb = IOUtil.getSharedCharBuf();
			sb.append("ilib/asm/nx/").append(file, 1, file.length());
			if (!file.endsWith(".class")) sb.append(".class");
			file = sb.toString();
		}

		if (!isIgnoredBy) {
			accessWidener.add(file);
		} else {
			accessWidener.remove(file);
			try {
				InputStream in = NiximProxy.class.getClassLoader().getResourceAsStream(file);
				if (in == null) throw new FileNotFoundException(file);

				ByteList buf = IOUtil.getSharedByteBuf();
				Context ctx = new Context("", buf.readStreamFully(in));

				transformUsers(ctx, instance.byParent);
				TransformUtil.makeAccessible(ctx.getData(), new MyHashSet<>("<$extend>", "*"));

				String name = ctx.getData().name.replace('/', '.');
				ByteList data = ctx.get();
				Class<?> clz = ClassDefiner.INSTANCE.defineClassC(name, data.list, 0, data.wIndex());
			} catch (IOException e) {
				Helpers.athrow(e);
			} catch (LinkageError e) {
				Helpers.athrow(new FastFailException("不要安装两份ImpLib"));
			}
		}
	}

	private static void postProcess(Context ctx) {
		Attribute attr = ctx.getData().attrByName("InnerClasses");
		if (attr != null) {
			List<InnerClasses.InnerClass> list = InnerClasses.parse(Parser.reader(attr), ctx.getData().cp);
			for (int i = 0; i < list.size(); i++) {
				InnerClasses.InnerClass ic = list.get(i);
				if ((ic.flags & AccessFlag.PUBLIC) == 0) {
					accessWidener.add(ic.self);
				}
			}
		}
	}

	@Override
	public boolean shouldApply(String annotation, MoFNode node, List<AnnValString> argument) {
		String id = argument.get(0).value;
		switch (id) {
			case "forge": return ForgeVersion.getBuildVersion() >= Integer.parseInt(argument.get(1).value);
			case "optifine": return (argument.size() < 2 || Boolean.parseBoolean(argument.get(1).value)) == Loader.hasOF;
			case "client": return Loader.testClientSide();
			case "server": return !Loader.testClientSide();
			case "coremod_flag": return !Config.hasIncompatibleCoremod(Integer.parseInt(argument.get(1).value));

			case "_nixim_not_custom_font": return Config.customFont.isEmpty();
		}
		throw new UnsupportedOperationException();
	}

	private static void findMods() {

	}

	public static boolean removeByClass(String target) {
		return instance.remove(target);
	}

	@Override
	public String map(String owner, MoFNode name, String desc) {
		if (desc.isEmpty()) {
			if (name.name().startsWith("func_") || name.name().startsWith("field_")) return name.name();
			Helpers.athrow(new TransformException("运行期工具已经胎死腹中，编译期没找到的话就乖乖的自己填写吧"));
		}
		return desc;
	}

	private NiximProxy() {}

	@Override
	public void transform(String trName, Context ctx) {
		try {
			CharList r = IOUtil.getSharedCharBuf().append(trName).replace('.', '/');

			if (accessWidener.remove(r)) {
				TransformUtil.makeAccessible(ctx.getData(), new MyHashSet<>("<$extend>", "*"));
				transformUsers(ctx, byParent);
			}

			NiximData data = registry.get(r);
			if (data != null) {
				nixim(ctx, data, NO_FIELD_MODIFIER_CHECK | NO_METHOD_MODIFIER_CHECK);
			}
		} catch (TransformException e) {
			Helpers.athrow(e);
		}
	}
}