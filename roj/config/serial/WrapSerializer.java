package roj.config.serial;

import roj.config.Wrapping;
import roj.config.data.CEntry;

/**
 * @author Roj233
 * @since 2022/1/11 17:45
 */
public class WrapSerializer implements Serializer<Object> {
	private Serializers owner;

	public WrapSerializer(Serializers owner) {this.owner = owner;}

	WrapSerializer() {}

	@Override
	public Object deserializeRc(CEntry o) {
		return o.unwrap();
	}

	@Override
	public CEntry serializeRc(Object o) {
		return Wrapping.wrap(o, owner);
	}
}
