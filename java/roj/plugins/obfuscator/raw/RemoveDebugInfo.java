package roj.plugins.obfuscator.raw;

import roj.asm.AsmCache;
import roj.asm.FieldNode;
import roj.asm.MethodNode;
import roj.asm.attr.AttributeList;
import roj.asm.attr.UnparsedAttribute;
import roj.asm.cp.ConstantPool;
import roj.asm.cp.CstUTF;
import roj.asmx.Context;
import roj.collect.MyHashSet;
import roj.plugins.obfuscator.ObfuscateTask;
import roj.text.CharList;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.util.List;
import java.util.Random;

/**
 * @author Roj234
 * @since 2025/3/18 14:16
 */
class RemoveDebugInfo implements ObfuscateTask {
	public MyHashSet<String> removeAttributes = new MyHashSet<>();
	public CharList mangleLine;

	@Override
	public void apply(Context ctx, Random rand) {
		var data = ctx.getData();

		AttributeList al = data.attributesNullable();
		if (al != null)
			for (String attribute : removeAttributes)
				al.removeByName(attribute);

		List<? extends MethodNode> methods = data.methods;
		for (int i = 0; i < methods.size(); i++) {
			MethodNode m = methods.get(i);
			al = m.attributesNullable();
			if (al != null)
				for (String attribute : removeAttributes)

					m.unparsed(data.cp);

			UnparsedAttribute au = (UnparsedAttribute) m.getRawAttribute("Code");
			if (au == null) continue;

			DynByteBuf r = AsmCache.reader(au);
			ByteList w = new ByteList(r.readableBytes());
			r.rIndex += 4; // stack size
			int codeLen = r.readInt();
			r.rIndex += codeLen; // code

			int len1 = r.readUnsignedShort(); // exception
			r.rIndex += len1 << 3;
			w.put(r.slice(0, r.rIndex));

			ConstantPool pool = data.cp;
			len1 = r.readUnsignedShort();

			int count = 0;
			int countIdx = w.wIndex();
			w.putShort(0);

			while (len1-- > 0) {
				String name = ((CstUTF) pool.get(r)).str();
				int len = r.readInt();
				int end = len + r.rIndex;
				switch (name) {
					default:
					case "LocalVariableTable":
					case "LocalVariableTypeTable":
						if (removeAttributes.contains(name)) {
							r.rIndex = end;
							continue;
						} else {
							count++;
							w.put(r.slice(r.rIndex - 6, len + 6));
						}
						break;
					case "LineNumberTable":
						if (mangleLine != null) {
							count++;
							int tableLen = r.readUnsignedShort();
							w.putShort(data.cp.getUtfId(name)).putInt(len).putShort(tableLen);

							mangleLine.append(' ').append(m.name()).append(' ').append(m.rawDesc()).append('\n');
							for (int k = 0; k < tableLen; k++) {
								int index = r.readUnsignedShort();
								int line = r.readUnsignedShort();

								int v = rand.nextInt(65536);
								mangleLine.append("  ").append(line).append(' ').append(v).append('\n');

								w.putShort(index).putShort(v);
							}
						} else if (removeAttributes.contains(name)) {
							r.rIndex = end;
							continue;
						} else {
							count++;
							w.put(r.slice(r.rIndex - 6, len + 6));
						}
						break;
					case "StackMapTable":
						count++;
						w.put(r.slice(r.rIndex - 6, len + 6));
						break;
				}
				r.rIndex = end;
			}
			w.putShort(countIdx, count);

			au.setRawData(w);
		}

		List<? extends FieldNode> fields = data.fields;
		for (int i = 0; i < fields.size(); i++) {
			al = fields.get(i).attributesNullable();
			if (al != null)
				for (String attribute : removeAttributes)
					al.removeByName(attribute);
		}
	}
}
