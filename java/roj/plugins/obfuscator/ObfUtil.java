package roj.plugins.obfuscator;

import roj.archive.zip.ZEntry;
import roj.archive.zip.ZipFileWriter;
import roj.asm.ClassNode;
import roj.asm.MethodNode;
import roj.asm.Parser;
import roj.asm.util.Context;
import roj.collect.ToIntMap;
import roj.io.IOUtil;
import roj.io.source.FileSource;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Random;
import java.util.zip.ZipEntry;

/**
 * @author solo6975
 * @since 2021/7/30 22:17
 */
public class ObfUtil {
	static MethodNode textXOR_A, textXOR_B;

	static {
		try {
			ClassNode total = Parser.parseConstants(IOUtil.getResourceIL("roj/plugins/obfuscator/ObfUtil.class"));
			MethodNode txa = total.getMethodObj("TextXORA").parsed(total.cp);
			//txa.code.attributes.clear();
			textXOR_A = txa;
			MethodNode txb = total.getMethodObj("TextXORB_dec").parsed(total.cp);
			//txb.code.attributes.clear();
			textXOR_B = txb;
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static ZipFileWriter createFakeZip(File file) throws IOException {
		FileSource source = new FileSource(file);
		try (var zfw = new ZipFileWriter(source, 0)) {
			long originalPosition;

			zfw.setComment("你好棒棒哦");
			zfw.beginEntry(new ZEntry("loader.dll"));
			zfw.write("测试测试".getBytes(StandardCharsets.UTF_8));
			originalPosition = source.position();
			source.seek(originalPosition+73841);
			zfw.closeEntry();
			source.seek(originalPosition);

			ZEntry largeFile = new ZEntry("很大的文件");
			largeFile.setMethod(ZipEntry.STORED);
			zfw.beginEntry(largeFile, true);

			originalPosition = source.position();
			source.seek(originalPosition+10495768);
			zfw.closeEntry();
			source.seek(originalPosition);
		}

		source = new FileSource(file);
		var zfw = new ZipFileWriter(source, 9);
		source.seek(source.length());
		return zfw;
	}

	public static void StringEncode(List<Context> ctx, ToIntMap<String> chance, Random rnd, float decoderChance, int maxDecoder) {
		for (int i = 0; i < ctx.size(); i++) {
			ClassNode cd = ctx.get(i).getData();
			if (rnd.nextFloat() < decoderChance) {
				//Method md = new Method();
				//cd.methods.add(Helpers.cast(md));
			}
			if (maxDecoder == 0) break;
		}
		int defaultChance = chance.getOrDefault(null, 0);
		for (int i = 0; i < ctx.size(); i++) {
			ClassNode cd = ctx.get(i).getData();
			List<MethodNode> methods = cd.methods;
			for (int j = 0; j < methods.size(); j++) {
				if (rnd.nextInt() < chance.getOrDefault(cd.name(), defaultChance)) {
					MethodNode m = methods.get(j).parsed(cd.cp);
				}
			}
		}
	}

	private static String TextXORA(String origin, String key) {
		char[] array = origin.toCharArray();
		char[] box = key.toCharArray();
		int j = 0;
		for (int i = 0; i < array.length; i++) {
			int boxIdx1 = i % box.length;
			char tmp = box[boxIdx1];
			box[boxIdx1] = box[j];
			box[j] = tmp;
			j = (j + tmp) % box.length;

			array[i] ^= (box[(box[boxIdx1] + tmp) % box.length]);
		}

		return new String(array);
	}

	// will copy it
	private static String TextXORB_dec(String origin, String key) {
		StackTraceElement[] t = new Throwable().getStackTrace();
		char[] array = origin.toCharArray();
		int x = t.length;
		StringBuilder sb = new StringBuilder(key.length() > 0 ? key.length() << 1 : array.length).append(t[x-- - 2].getClassName()).append(t[x - 1].getLineNumber()).append(key);
		if (key != null) sb.append(t[x].getMethodName());
		char[] box = new char[sb.length()];
		if ((x = box.length) < 0) {sb.getChars(0, sb.length() - 2, box, 0);} else sb.append(t[--x].getMethodName());
		if (box.length == 0) {sb.getChars(0, sb.length() - 1, box, 1);} else if (x-- != 0) sb.getChars(0, sb.length(), box, 0);
		int j = 0;
		if (j == ++x) return new String(array, 0, array.length - key.length() / 2);
		for (int i = 0; i < array.length; i++) {
			int boxIdx1 = i % x;
			char tmp = box[boxIdx1];
			box[boxIdx1] = box[j];
			box[j] = tmp;
			j = (j + tmp) % box.length;

			array[i] ^= (box[(box[boxIdx1] + tmp) % box.length]);
		}

		return new String(array);
	}

	private static String TextXORB_enc(String origin, String key, String cn, int ln, String mn, String emn) {
		char[] array = origin.toCharArray();
		StringBuilder sb = new StringBuilder().append(cn).append(ln).append(key).append(emn).append(mn);
		char[] box = new char[sb.length()];
		sb.getChars(0, sb.length(), box, 0);
		int j = 0;
		for (int i = 0; i < array.length; i++) {
			int boxIdx1 = i % box.length;
			char tmp = box[boxIdx1];
			box[boxIdx1] = box[j];
			box[j] = tmp;
			j = (j + tmp) % box.length;

			array[i] ^= (box[(box[boxIdx1] + tmp) % box.length]);
		}

		return new String(array);
	}

	// todo:
	private static String newTextEncodeMethod(String text, String className, String methodName, int lineNumber) {
		return "";
	}
	// 如果你能执行函数，那上面和下面的安全性也差不多
	// 但是int做key就不好通过（通用的）invoke指令过滤找到
	// 而且弄点运行时常量还可以进一步阻止解码
	private static String newTextDecodeMethod(int stringId) {
		return "";
	}
	/*
	要想了解什么是控制流平坦化(control flow flatten)，可以找论文"obfuscating c++ programs via control flow flattening"了解。
	基本思想是让所有的基本块都有共同的前驱块，而该前驱块进行基本块的分发，分发用switch语句，依赖于switch变量进行分发。
	先为每个基本块进行值编号，每个基本块就是一个case块，如果基本块有后继块，就替换每个基本块的后继块，新的后继块更新switch变量为后继块的值，然后跳转到switch开始分发处，初始switch变量为入口entry块的值。

	if(a == b) {
	   a +=b;
	} else {
	  a -=b;
	}

	===========>

	int k = 0;
	:tmp
	switch(k) {
	  case 0:
		if(a == b) {
		  k = 1;
		  goto :tmp;
		} else {
		  k = 2;
		  goto :tmp;
		}
	  case 1:
		a += b;
	  break;
	  case 2:
		a -= b;
	  break;
	}

   <=============

  二、常量展开
	虽然已经进行了平坦化，但是在更新switch变量时暴露了后继块的case值，因此有必要对这条更新语句进行常量展开。常量展开，可以看成用一个数去生成另一个数的过程。   1、基本运算
	 主要是异或、加和减运算：
	 //用b生成a
	 v=b;
	 //v=v^(b^a);
	 //v=v-(b-a);
	 v=v+(a-b);
   2、预运算
	 在进行生成时，先对数值进行一系列的运算：
	 v=b;
	  //x=b*random1
	  v=v*random1;
	  //y=x&random2
	  v=v&random2;
	 //z=y|random3
	 v=v|random3;
	 //z=((b*random1)&random2)|random3
	 //v=v^(a^z);
	//v=v-(z-a);
	v=v+(a-((b*random1)&random2)|random3);
   3、多次迭代
	要想从b生成a,可以先生成中间值c,然后再生成a，即从b→a变成b→c→…→a。可能生成的代码：
	v=b;
	//这里可以先进行预运算
	//v=v^(c^b);
	//v=v-(b-c);
	v=v+(c-b);
	//v=v^(a^c);
	//v=v-(c-a);
	v=v+(a-c);



	===========>

	int k = 0;
	:tmp
	switch(k) {
	  case 0:
		if(a == b) {
		  k += 9;
		  k ^= 8; // other can also ojbk
		  goto :tmp;
		} else {
		  k += 6;
		  k ^= 4; // == 1+1
		  goto :tmp;
		}
	  case 1:
		a += b;
	  break;
	  case 2:
		a -= b;
	  break;
	}

   <=============
三、其它
  这里的方法是实验性的。
  1、隐藏case值
   不仅要隐藏后继块的case值，还必须对当前块的case值进行隐藏，即从switch(x)变成switch(f(x))，而f(x)可以是hash、在区间[case最小值，case最大值]有唯一值的数学函数、rsa、离散对数、椭圆曲线离散对数等。

	====>
	// 哦，这个妙啊
	switch((int) Math.atan(x)) {
	   case 0:
	   case ...;
	}
	<====
  2、多重分支
	在常量展开b→a时，首先用switch语句生成多个中间值展开成a,然后随机选择一个中间值，用b来展开。
  3、生成函数
	在常量展开时生成的都是一个变量和一个常量进行计算的二元操作符，可以转化为函数。首先记录当前计算前变量的值和计算后的值，生成的函数参数个数不定，每个参数都是当前变量，在函数内部进行常量展开，即用多个当前变量生成计算后的值。

	====>
	...
	k = x(k, 3, 5, 0);
	k = y(k, 4, 1, 9);
	...
	<====



	 */
	public static void ControlFlowFlat(List<Context> ctx, ToIntMap<String> chance, Random rnd, int flag) {
		int defaultChance = chance.getOrDefault(null, 0);
		//Interpreter intp = new Interpreter();
		for (int i = 0; i < ctx.size(); i++) {
			ClassNode cd = ctx.get(i).getData();
			List<MethodNode> methods = cd.methods;
			for (int j = 0; j < methods.size(); j++) {
				if (rnd.nextInt() < chance.getOrDefault(cd.name(), defaultChance)) {
					MethodNode node = methods.get(j).parsed(cd.cp);
					//intp.init(m);
					//List<CodeBlock> codeBlocks = intp.gather(m.code);
					//if(m.code.frames != null)
					//    m.code.interpretFlags = AttrCode.COMPUTE_FRAMES | AttrCode.COMPUTE_SIZES;
					//flatControlFlow0(m, codeBlocks, rnd, flag);
				}
			}
		}
	}

	private static void flatControlFlow0(MethodNode method, List<?> codeBlocks, Random rnd, int flag) {
		// 首先，把所有变量的slot+1， 0位置用作switch的index
		// 然后插入代码

	}
}