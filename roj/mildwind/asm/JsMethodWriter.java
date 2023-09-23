package roj.mildwind.asm;

import roj.asm.tree.ConstantData;
import roj.asm.tree.MethodNode;
import roj.asm.tree.attr.LineNumberTable;
import roj.asm.util.AccessFlag;
import roj.asm.util.TryCatchEntry;
import roj.asm.visitor.AttrCodeWriter;
import roj.asm.visitor.CodeWriter;
import roj.asm.visitor.Segment;
import roj.collect.MyBitSet;
import roj.collect.SimpleList;
import roj.config.word.LineHandler;

import java.util.List;

import static roj.asm.Opcodes.ARETURN;
import static roj.asm.Opcodes.GETSTATIC;

/**
 * @author Roj233
 * @since 2020/9/28 12:44
 */
public final class JsMethodWriter extends CodeWriter implements LineHandler {
	public int currentMethodId;

	public AsmClosure variables;

	public ConstantData data;
	JsWriter klass;

	LineNumberTable lines = new LineNumberTable();
	List<TryCatchEntry> exceptions = new SimpleList<>();
	MyBitSet varid = new MyBitSet();

	public JsFunctionCompiled compile() {
		return klass.compile();
	}

	public int getTmpVar() {
		int pos = varid.nextFalse(2);
		varid.add(pos);
		return pos;
	}

	public void delTmpVar(int id) {
		varid.remove(id);
	}

	private final String chain;

	private JsMethodWriter(String file) {
		klass = new JsWriter(file);
		data = klass.data;
		chain = "$";
		init();
	}
	private JsMethodWriter(JsMethodWriter parent) {
		klass = parent.klass;
		data = parent.data;
		chain = parent.mn.name();
		init();
	}
	private void init() {
		MethodNode mn = new MethodNode(AccessFlag.PRIVATE|AccessFlag.FINAL, data.name, chain, "(Lroj/mildwind/type/JsObject;Lroj/mildwind/api/Arguments;)Lroj/mildwind/type/JsObject;");
		mn.putAttr(new AttrCodeWriter(data.cp, mn, this));
		data.methods.add(mn);
		visitSizeMax(99,99);

		TryCatchEntry e = new TryCatchEntry();
		//exceptions.add(e);
		//e.type = "roj/mildwind/util/TCOException";
		//e.start = new Label();
		//e.handler = label();
		//_segment(new TCOSegment(e));
		//label(e.start);
	}

	public static JsMethodWriter builder(String file) { return new JsMethodWriter(file); }
	public static JsMethodWriter builder(JsMethodWriter parent) { return new JsMethodWriter(parent); }

	public void funcName(String name) { this.mn.name(chain+'.'+name); }

	@Override
	public void handleLineNumber(int line) {
		lines.list.add(new LineNumberTable.Item(label(), line));
	}

	public TryCatchEntry exception() {
		TryCatchEntry entry = new TryCatchEntry();
		exceptions.add(entry);
		return entry;
	}

	public int sync(Object t) { return klass.sync(t); }

	public void load(int fid) {
		field(GETSTATIC, data, fid);  // TODO: copy on write
		invokeV("roj/mildwind/type/JsObject", "deepcopy", "()Lroj/mildwind/type/JsObject;");
	}

	public void end() {
		field(GETSTATIC, "roj/mildwind/type/JsNull", "UNDEFINED", "Lroj/mildwind/type/JsNull;");
		one(ARETURN);
		visitSizeMax(999,999);
		visitExceptions();
		for (TryCatchEntry exception : exceptions) {
			visitException(exception.start,exception.end,exception.handler,exception.type);
		}
		visitAttributes();
		visitAttribute(lines);
		finish();
	}

	@Override
	public String toString() { return "<ast serializer for '"+data.name+"', current method '"+mn.name()+"', constructor '"+data+"'>"; }

	public static class TCOSegment extends Segment {
		public TCOSegment(TryCatchEntry e) {

		}

		@Override
		protected boolean put(CodeWriter to) {
			return false;
		}
	}
	public void useTCO() {
		// TODO invoke: reimplement in CodeWriter
//		for(;;) {
//			try {
//				JsObject rv = fn._invoke($this, argList);
//				JsContext.releaseArgList(argList);
//				return rv;
//			} catch (TCOException e) {
//				JsContext.releaseArgList(argList);
//
//				$this = e.$this;
//				argList = e.args;
//				fn = e.fn;
//			} catch (Throwable e) {
//				JsContext.releaseArgList(argList);
//				throw e;
//			}
//		}

		// prepare this and arg
		// 把控制权返回调用者
		// throw JsContext.context().localTCOInit.reset(f.$this, argList, fn.asFunction(), flag);
	}
}
