package roj.mapper.obf.policy;

import roj.io.IOUtil;
import roj.text.LineReader;
import roj.text.TextUtil;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Random;

/**
 * Confusing char arrays
 *
 * @author Roj233
 * @since 2021/7/18 19:29
 */
public final class StringList extends SimpleNamer {
	public List<? extends CharSequence> chars;

	public StringList() {}

	public StringList(File file) throws IOException {
		List<String> list = LineReader.slrParserV2(IOUtil.readUTF(file), true);
		for (int i = list.size() - 1; i >= 0; i--) {
			if (!isValid(list.get(i))) {
				System.out.println("Not a valid class name: " + list.get(i));
				list.remove(i);
			}
		}
		this.chars = list;
	}

	static boolean isValid(String s) {
		for (int i = 0; i < s.length(); i++) {
			switch (s.charAt(i)) {
				case ';':
				case '[':
				case '%':
				case '.':
				case '/':
					return false;
			}
		}
		return true;
	}

	public static StringList newJavaKeyword() {
		StringList mix = new StringList();
		mix.chars = TextUtil.split(
			"for,while,do,continue,break,case,if,else,goto," + "return,this,new,switch,true,false,null,try,catch,finally," + "throw,default,public,private,protected,int,float,double,long,boolean,throws,char,short,class" + ",enum,extends,implements,static,synchronized,transient,strictfp,interface,byte",
			',');

		return mix;
	}

	public static StringList newJavaKeywordExtended() {
		StringList mix = new StringList();
		mix.chars = TextUtil.split(
			"for,while,do,continue,break,case,if,else,goto," + "return,this,new,switch,true,false,null,try,catch,finally," + "throw,default,public,private,protected,int,float,double,long,boolean,throws,char,short,class" + ",enum,extends,implements,static,synchronized,transient,strictfp,interface,byte," + "Infinity,NaN,Double,Integer,Character,String,Long,Short,Byte,CharSequence,Collections," + "Arrays,List,Set,Map,HashMap,TreeMap,ByteBuffer,File,Path,Random,StringBuilder,IOException,InterruptedException,System,Runtime,Finalizer",
			',');

		return mix;
	}

	@Override
	public String obfName0(Random rand) {
		buf.append(chars.get(rand.nextInt(chars.size())));
		String s = buf.toString();
		buf.clear();
		return s;
	}
}
