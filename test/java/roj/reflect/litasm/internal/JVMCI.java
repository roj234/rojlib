package roj.reflect.litasm.internal;

import jdk.vm.ci.code.site.DataPatch;
import jdk.vm.ci.code.site.Site;
import jdk.vm.ci.hotspot.HotSpotCompiledCode;
import jdk.vm.ci.hotspot.HotSpotCompiledNmethod;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaMethod;
import jdk.vm.ci.meta.Assumptions;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.runtime.JVMCIBackend;
import jdk.vm.ci.runtime.JVMCICompiler;
import roj.reflect.litasm.CodeInjector;

import java.lang.reflect.Method;

/**
 * @author Roj234
 * @since 2024/10/12 16:16
 */
public class JVMCI implements CodeInjector {
	private static final JVMCIBackend jvmci = jdk.vm.ci.runtime.JVMCI.getRuntime().getHostJVMCIBackend();

	@Override
	public void injectCode(Method method, byte[] asm, int len) throws Exception {
		var resolved = jvmci.getMetaAccess().lookupJavaMethod(method);

		var nmethod = new HotSpotCompiledNmethod(
			method.getName().concat("[LitASM]"),
			asm,
			len,
			new Site[0],
			new Assumptions.Assumption[0],
			new ResolvedJavaMethod[0],
			new HotSpotCompiledCode.Comment[0],
			new byte[0],
			1,
			new DataPatch[0],
			true,
			0,
			null,
			(HotSpotResolvedJavaMethod) resolved,
			JVMCICompiler.INVOCATION_ENTRY_BCI,
			1,
			0,
			false
		);

		jvmci.getCodeCache().setDefaultCode(resolved, nmethod);
	}
}
