package roj.plugins.kscript.func;

import roj.config.data.CEntry;
import roj.config.data.CMap;
import roj.config.data.Type;
import roj.config.serial.CVisitor;
import roj.text.CharList;

/**
 * @author Roj234
 * @since 2021/6/16 20:28
 */
public class KSFunction extends CEntry {
    private KSObject self = new KSObject();
    protected String name, source;
    protected JavaFunc computer;

    public KSFunction(JavaFunc computer) {
        self.put("prototype", Constants.FUNCTION);
        this.computer = computer;
    }

    @Override public CMap asMap() {return self;}
    @Override public Type getType() {return Type.OTHER;}

    @Override public Object raw() {return this;}
    @Override public void accept(CVisitor ser) {throw new UnsupportedOperationException("无法序列化函数");}
    @Override protected CharList toJSON(CharList sb, int depth) {throw new UnsupportedOperationException("无法序列化函数");}

    @Override public CEntry __call(CEntry self, CEntry args) {return computer.invoke(self, args.asList());}

    /**
     * 函数名称 <BR>
     *     eg: func
     */
    public String getName() {
        return name == null ? "<anonymous>" : name;
    }

    /**
     * 文件名 <BR>
     *     eg: test.js
     */
    public String getSource() {
        return source == null ? getClass().getSimpleName() + ".java" : source;
    }

    public CEntry newInstance(Arguments args) {
        KSObject prototype = new KSObject(self.get("prototype").asMap());
        prototype.put("__CLASSNAME__", name);
        return prototype;
    }
}
