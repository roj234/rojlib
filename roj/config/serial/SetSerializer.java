package roj.config.serial;

import roj.collect.MyHashSet;
import roj.config.data.CEntry;
import roj.config.data.Type;

import java.util.List;
import java.util.Set;

/**
 * @author Roj233
 * @since 2022/1/11 17:45
 */
class SetSerializer extends WrapSerializer {
	public SetSerializer(Serializers owner) {
		super(owner);
	}

	@Override
	public Set<?> deserializeRc(CEntry o) {
		if (o.getType() == Type.NULL) return null;
		List<CEntry> list = o.asList().raw();
		MyHashSet<Object> set = new MyHashSet<>(list.size());
		for (int i = 0; i < list.size(); i++) set.add(list.get(i).unwrap());
		return set;
	}
}
