package roj.platform;

import roj.asm.TransformException;
import roj.asm.tree.ConstantData;
import roj.asm.util.AccessFlag;
import roj.asm.util.Context;
import roj.crypt.MT19937;
import roj.launcher.ITransformer;
import roj.util.ArrayUtil;

/**
 * @author Roj234
 * @since 2023/12/31 0031 1:20
 */
public class DPSAutoObfuscate implements ITransformer {
	@Override
	public boolean transform(String mappedName, Context ctx) throws TransformException {
		if (mappedName.equals("roj/platform/PluginDescriptor") || mappedName.equals("roj/platform/DPSSecurityManager")) {
			ConstantData da = ctx.getData();
			MT19937 rnd = new MT19937();
			for (int i = rnd.nextInt(4); i < 12; i++) da.newField(AccessFlag.PRIVATE, "f"+i, "Ljava/lang/Object;");
			for (int i = 0; i < 16; i++) da.newField(AccessFlag.PRIVATE|AccessFlag.STATIC, "sf"+i, "Ljava/lang/Object;");
			for (int i = rnd.nextInt(20)+12; i < 36; i++) da.newField(AccessFlag.PRIVATE, "f"+i, "B");
			ArrayUtil.shuffle(da.fields, rnd);
			return true;
		}
		return false;
	}
}