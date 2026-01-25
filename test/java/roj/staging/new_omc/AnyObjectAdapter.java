package roj.staging.new_omc;

import java.util.List;

/**
 * @author Roj234
 * @since 2026/02/07 05:41
 */
final class AnyObjectAdapter implements TypeAdapter.MapAdapter {
	private static final State.Field ANY_OBJECT = State.Field.primitive(9);
	private final ObjectMapperImpl impl;

	public AnyObjectAdapter(ObjectMapperImpl impl) {
		this.impl = impl;
	}

	@Override
	public Object newContainerBuilder(int size) {return null;}

	@Override
	public Object build(Object container, ValueContainer value) {
		if (value.LValue == null) throw new IllegalStateException("<any> expecting type specifier '==', but found nothing");
		return value.LValue;
	}

	@Override
	public TypeAdapter set(ValueContainer container, ValueContainer key, ValueContainer value) {
		if (!(value.LValue instanceof String valueType)) {
			Object lValue = value.LValue;
			throw new IllegalStateException("<any> expecting type specifier to be a string, but found "+(lValue == null ? "nothing" : lValue.getClass()));
		}

		TypeAdapter adapter = impl.getAdapter(valueType);
		if (adapter == null) throw new UnsupportedOperationException();

		if (adapter.kind() == Kind.MAP || adapter.kind() == Kind.OBJECT) {
			return adapter;
		}

		return new MapAdapter() {
			@Override
			public void getTypes(String key, List<State.Field> fields) {
				if (!"value".equals(key)) throw new IllegalStateException("Type missing");
				fields.add(ANY_OBJECT);
				fields.add(new State.Field("value", adapter)); // value
			}

			@Override
			public TypeAdapter set(ValueContainer container, ValueContainer key, ValueContainer value) {
				container.LValue = value.LValue;
				return null;
			}

			@Override
			public Object newContainerBuilder(int size) {return null;}
		};
	}

	@Override
	public void getTypes(String key, List<State.Field> fields) {
		if (!"==".equals(key)) throw new IllegalStateException("<any> expecting type specifier '==', but found '"+key+"'");
		fields.add(ANY_OBJECT); // key (string)
		fields.add(ANY_OBJECT); // value (any)
	}
}
