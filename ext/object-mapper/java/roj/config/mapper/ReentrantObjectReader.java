package roj.config.mapper;

import roj.collect.ArrayList;

/**
 * @author Roj234
 * @since 2023/3/19 19:16
 */
final class ReentrantObjectReader extends MappingContext {
	ArrayList<Object> objectsR = new ArrayList<>();

	public ReentrantObjectReader(TypeAdapter root) {super(root);}

	private boolean capture;
	final void captureRef() {
		if (capture) throw new IllegalStateException();
		capture = true;
	}

	@Override
	public void setRef(Object o) {
		ref = o;
		if (capture) {
			capture = false;
			objectsR.add(o);
		}
	}
	public final ObjectReader<Object> reset() {objectsR.clear();return super.reset();}

	@Override
	public ObjectWriter<Object> getWriter() {return new ReentrantObjectWriter(root);}
}