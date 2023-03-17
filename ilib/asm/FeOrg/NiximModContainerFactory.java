package ilib.asm.FeOrg;

import org.objectweb.asm.Type;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;

import net.minecraftforge.fml.common.FMLLog;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.discovery.ModCandidate;
import net.minecraftforge.fml.common.discovery.asm.ASMModParser;

import java.io.File;
import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Map;

/**
 * @author Roj234
 * @since 2021/6/18 9:51
 */
@Nixim("net.minecraftforge.fml.common.ModContainerFactory")
public class NiximModContainerFactory {
	@Shadow("/")
	public static Map<Type, Constructor<? extends ModContainer>> modTypes;

	@Inject("/")
	public ModContainer build(ASMModParser mp, File modSource, ModCandidate container) {
		String cn = mp.getASMType().getClassName();
		List<TypeHelper> house = ((FastParser) mp).getClassAnnotations();

		for (int i = 0; i < house.size(); i++) {
			Constructor<? extends ModContainer> con = modTypes.get(house.get(i).type);
			if (con != null) {
				FMLLog.log.debug("检测到mod {} - 开始加载", cn);

				try {
					ModContainer ret = con.newInstance(cn, container, house.get(i).val);
					if (ret.shouldLoadInEnvironment()) return ret;

					FMLLog.log.debug("放弃加载 {}, 环境不适合", cn);
				} catch (Exception e) {
					FMLLog.log.error("无法为{}构建ModContainer, 位于{}", house.get(i).type.getClassName(), modSource, e);
				}
			}
		}
		return null;
	}
}
