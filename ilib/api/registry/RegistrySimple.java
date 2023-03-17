package ilib.api.registry;

import ilib.ImpLib;
import ilib.util.ForgeUtil;
import roj.util.Helpers;

import net.minecraftforge.fml.common.LoaderState;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.IntFunction;

/**
 * @author Roj234
 * @since 2020/8/21 0:12
 */
public class RegistrySimple<T extends Indexable> implements IRegistry<T> {
	List<T> values;
	T[] arr;
	IntFunction<T[]> arrayGet;
	final boolean checkStage;

	public RegistrySimple(IntFunction<T[]> arrayGet) {
		this(arrayGet, false);
	}

	public RegistrySimple(IntFunction<T[]> arrayGet, boolean checkStage) {
		this.values = new ArrayList<>();
		this.arrayGet = arrayGet;
		this.checkStage = checkStage;
	}

	private RegistrySimple(RegistrySimple<T> wrapper, T[] ts) {
		this.values = new ArrayList<>(wrapper.values);
		Collections.addAll(values, ts);
		this.arrayGet = wrapper.arrayGet;
		this.checkStage = wrapper.checkStage;
	}

	@SafeVarargs
	public final RegistrySimple<T> with(T... ts) {
		return new RegistrySimple<>(this, ts);
	}

	@SafeVarargs
	public static <T extends Indexable> RegistrySimple<T> from(T... ts) {
		if (ts.length == 0) ImpLib.logger().error("ts.length == 0", new Error());

		RegistrySimple<T> registry = new RegistrySimple<>(Helpers.cast((IntFunction<Object[]>) Object[]::new));
		registry.arr = ts;
		registry.values = Arrays.asList(ts);
		return registry;
	}

	public T[] values() {
		if (arr == null) arr = values.toArray(arrayGet.apply(values.size()));
		return arr;
	}

	public int appendValue(T t) {
		if (checkStage) {
			if (ForgeUtil.hasReachedState(LoaderState.INITIALIZATION)) throw new IllegalStateException("Already registered.");
		}

		this.values.add(t);
		this.arr = null;

		return this.values.size() - 1;
	}

	public T byId(int id) {
		if (id > -1 && id < values.size()) {
			return values.get(id);
		}
		return null;
	}

	@Override
	@SuppressWarnings("unchecked")
	public Class<T> getValueClass() {
		return (Class<T>) values.get(0).getClass();
	}
}
