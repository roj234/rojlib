package roj.asm.mapper.obf.policy;

import roj.io.IOUtil;
import roj.text.SimpleLineReader;
import roj.text.TextUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Random;

/**
 * Confusing char arrays
 *
 * @author Roj233
 * @since 2021/7/18 19:29
 */
public final class StringList extends SimpleNamer {
    public CharSequence[] chars;

    public StringList() {}

    public StringList(File file) throws IOException {
        this.chars = new SimpleLineReader(IOUtil.readUTF(new FileInputStream(file))).toArray();
    }

    public static StringList newJavaKeyword() {
        StringList mix = new StringList();
        mix.chars = TextUtil.split("for,while,do,continue,break,case,if,else,goto," +
                "return,this,new,switch,true,false,null,try,catch,finally," +
                "throw,default,public,private,protected,int,float,double,long,boolean,throws,char,short,class" +
                ",enum,extends,implements,static,synchronized,transient,strictfp,interface,byte", ',');

        return mix;
    }

    public static StringList newJavaKeywordExtended() {
        StringList mix = new StringList();
        mix.chars = TextUtil.split("for,while,do,continue,break,case,if,else,goto," +
                "return,this,new,switch,true,false,null,try,catch,finally," +
                "throw,default,public,private,protected,int,float,double,long,boolean,throws,char,short,class" +
                ",enum,extends,implements,static,synchronized,transient,strictfp,interface,byte," +
                "Infinity,NaN,Double,Integer,Character,String,Long,Short,Byte,CharSequence,Collections," +
                "Arrays,List,Set,Map,HashMap,TreeMap,ByteBuffer,File,Path,Random,StringBuilder,IOException,InterruptedException,System,Runtime,Finalizer", ',');

        return mix;
    }

    @Override
    public String obfName0(Random rand) {
        buf.append(chars[rand.nextInt(chars.length)]);
        String s = buf.toString();
        buf.clear();
        return s;
    }
}
