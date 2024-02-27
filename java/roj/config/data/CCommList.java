package roj.config.data;

import roj.collect.IntMap;
import roj.config.serial.CVisitor;

import java.util.List;

/**
 * @author Roj233
 * @since 2022/1/14 20:07
 */
public class CCommList extends CList {
	IntMap<String> comments = new IntMap<>();

	public CCommList() {}
	public CCommList(List<CEntry> map) { super(map); }

	@Override
	public void accept(CVisitor ser) {
		List<CEntry> l = list;
		ser.valueList(l.size());
		for (int i = 0; i < l.size(); i++) {
			String s = comments.get(i);
			if (s != null) ser.comment(s);
			l.get(i).accept(ser);
		}
		ser.pop();
	}

	public IntMap<String> getComments() {return comments;}

	public final boolean isCommentSupported() {return true;}
	public String getComment(int key) {return comments.get(key);}
	public void putComment(int key, String val) {comments.putInt(key, val);}
	public final CList withComments() {return this;}
	public void clearComments() {comments.clear();}
}