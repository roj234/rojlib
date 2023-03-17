package roj.mapper.obf.policy;

import roj.mapper.util.Desc;

import java.util.Random;
import java.util.Set;

/**
 * Randomly select one
 *
 * @author Roj233
 * @since 2021/7/18 19:45
 */
public final class RandomDispatch implements NamingFunction {
	final NamingFunction[] functions;

	public RandomDispatch(NamingFunction... functions) {
		this.functions = functions;
	}

	@Override
	public NamingFunction setKeepPackage(boolean keepPackage) {
		for (NamingFunction fn : functions)
			fn.setKeepPackage(keepPackage);
		return this;
	}

	@Override
	public final String obfClass(String origName, Set<String> noDuplicate, Random rand) {
		return functions[rand.nextInt(functions.length)].obfClass(origName, noDuplicate, rand);
	}

	@Override
	public final String obfName(Set<String> noDuplicate, Desc param, Random rand) {
		return functions[rand.nextInt(functions.length)].obfName(noDuplicate, param, rand);
	}
}
