package ilib.api.registry;

import com.google.common.base.Optional;
import com.google.common.collect.Maps;

import net.minecraft.block.properties.IProperty;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Roj234
 * @since 2021/6/2 23:42
 */
public class BlockPropTyped<T extends Propertied<T>> implements IProperty<T>, IRegistry<T> {
	protected final List<T> allowedValues = new ArrayList<>();
	protected T[] allowValues = null;
	protected final Map<String, T> nameToValue = Maps.newHashMap();
	private final String name;
	private final Class<T> propClass;

	protected BlockPropTyped(String name, Class<T> propClass) {
		this.name = name;
		this.propClass = propClass;
	}

	public BlockPropTyped(String name, IRegistry<T> wrapper) {
		this(name, wrapper.getValueClass(), wrapper.values());
	}

	public BlockPropTyped(IRegistry<T> wrapper) {
		this("type", wrapper.getValueClass(), wrapper.values());
	}

	@SuppressWarnings("unchecked")
	public BlockPropTyped(T... allowValues) {
		this("type", (Class<T>) (allowValues.length == 0 ? throwException() : allowValues[0].getClass()), allowValues);
	}

	public static Class<?> throwException() {
		throw new IllegalArgumentException("No allow values");
	}

	public BlockPropTyped(String name, Class<T> propClass, T[] allowValues) {
		this.name = name;
		this.propClass = propClass;

		if (allowValues.length == 0) throw new IllegalArgumentException("No allow values");
		this.allowValues = allowValues;
		for (T oneEnum : allowValues) {
			if (oneEnum == null) throw new NullPointerException("allowValues[i]");

			String _name = oneEnum.getName();
			if (this.nameToValue.containsKey(_name)) {
				throw new IllegalArgumentException("Multiple values have the same name '" + _name + "'");
			}
			this.allowedValues.add(oneEnum);
			this.nameToValue.put(_name, oneEnum);
		}
	}

	@Nonnull
	@Override
	public String getName() {
		return this.name;
	}

	@Nonnull
	@Override
	public Class<T> getValueClass() {
		return this.propClass;
	}

	@Nonnull
	@Override
	public List<T> getAllowedValues() {
		return this.allowedValues;
	}

	@Override
	public T byId(int id) {
		if (id < 0 || id > allowedValues.size()) return null;
		return allowedValues.get(id);
	}

	@Override
	public T[] values() {
		return allowValues;
	}

	@Nonnull
	@Override
	public Optional<T> parseValue(@Nonnull String p_parseValue_1_) {
		return Optional.fromNullable(this.nameToValue.get(p_parseValue_1_));
	}

	@Nonnull
	@Override
	public String getName(T p_getName_1_) {
		return p_getName_1_.getName();
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder().append('[').append(getName()).append(']').append('{');
		for (T t : allowedValues) {
			sb.append(t.getName()).append(",");
		}

		return sb.append('}').toString();
	}

	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj instanceof BlockPropTyped) {
			BlockPropTyped<?> lvt_2_1_ = (BlockPropTyped<?>) obj;
			return (this.name.equals(lvt_2_1_.name)) && (this.propClass == lvt_2_1_.propClass) && (this.allowedValues.equals(lvt_2_1_.allowedValues)) && (this.nameToValue.equals(
				lvt_2_1_.nameToValue));
		}
		return false;
	}

	public int hashCode() {
		int code = 31 * this.name.hashCode();
		code = 31 * code + this.propClass.hashCode();
		return code;
	}
}