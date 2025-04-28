package roj.compiler.resolve;

import roj.asm.attr.AnnotationDefault;
import roj.config.data.CEntry;
import roj.config.data.Type;
import roj.config.serial.CVisitor;

/**
 * @author Roj234
 * @since 2024/6/21 0021 17:39
 */
final class LazyAnnotationValue extends CEntry {
	public final AnnotationDefault ref;

	public LazyAnnotationValue(AnnotationDefault ref) {this.ref = ref;}

	@Override public Type getType() {return ref.val.getType();}
	@Override public char dataType() {return ref.val.dataType();}
	@Override public void accept(CVisitor visitor) {ref.val.accept(visitor);}
	@Override public Object raw() {return ref.val.raw();}
	@Override public String toString() {return ref.val.toString();}
}