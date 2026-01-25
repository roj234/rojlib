package roj.staging.new_omc;

import roj.collect.HashMap;

import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

/**
 * @author Roj234
 * @since 2026/02/07 05:43
 */
final class MapAdapterImpl implements TypeAdapter.MapAdapter {
	private final State.Field keyType, valueType;
	private final IntFunction<Object> containerFactory;

	MapAdapterImpl(ObjectMapperImpl impl) {
		keyType = State.Field.primitive(9); // key (string)
		valueType = new State.Field(null, impl.getAdapter("java/lang/Object"));
		containerFactory = null;
	}

	MapAdapterImpl(State.Field keyType, State.Field valueType, IntFunction<Object> containerFactory) {
		this.keyType = keyType;
		this.valueType = valueType;
		this.containerFactory = containerFactory;
	}

	@Override
	public Object newContainerBuilder(int size) {
		return containerFactory != null
					   ? containerFactory.apply(size)
					   : new HashMap<>(size < 0 ? 16 : size);
	}

	@Override
	@SuppressWarnings("unchecked")
	public TypeAdapter set(ValueContainer container, ValueContainer key, ValueContainer value) {
		((Map<Object, Object>) container.LValue).put(key.LValue, value.LValue);
		return null;
	}

	public void getTypes(String key, List<State.Field> fields) {
		fields.add(keyType);
		fields.add(valueType);
	}
}
