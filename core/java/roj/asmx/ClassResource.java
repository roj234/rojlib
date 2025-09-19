package roj.asmx;

import roj.asm.AsmCache;
import roj.asm.ClassDefinition;
import roj.util.ByteList;

/**
 * @author Roj234
 * @since 2025/2/12 13:22
 */
public interface ClassResource {
	String getFileName();
	ByteList getClassBytes();

	static ClassResource fromDefinition(ClassDefinition definition) {
		return new ClassResource() {
			@Override public String getFileName() {return definition.name()+".class";}
			@Override public ByteList getClassBytes() {return AsmCache.toByteArrayShared(definition);}
		};
	}
}
