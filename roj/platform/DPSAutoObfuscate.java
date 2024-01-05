package roj.platform;

import roj.asm.Opcodes;
import roj.asm.tree.ConstantData;
import roj.asm.util.Context;
import roj.asmx.ITransformer;
import roj.asmx.TransformException;
import roj.util.ArrayUtil;

import java.security.SecureRandom;

/**
 * @author Roj234
 * @since 2023/12/31 0031 1:20
 */
public class DPSAutoObfuscate implements ITransformer {
	@Override
	public boolean transform(String name, Context ctx) throws TransformException {
		if (name.equals("roj/platform/PluginDescriptor") || name.equals("roj/platform/DPSSecurityManager")) {
			ConstantData da = ctx.getData();
			SecureRandom rnd = new SecureRandom();
			for (int i = rnd.nextInt(4); i < 12; i++) da.newField(Opcodes.ACC_PRIVATE, "f"+i, "Ljava/lang/Object;");
			for (int i = 0; i < 16; i++) da.newField(Opcodes.ACC_PRIVATE|Opcodes.ACC_STATIC, "sf"+i, "Ljava/lang/Object;");
			for (int i = rnd.nextInt(20)+12; i < 36; i++) da.newField(Opcodes.ACC_PRIVATE, "f"+i, "B");
			ArrayUtil.shuffle(da.fields, rnd);
			return true;
		}
		return false;
	}
}