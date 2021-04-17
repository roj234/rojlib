package roj.mapper;

import roj.mapper.util.Desc;
import roj.ui.UIUtil;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Roj233
 * @since 2022/2/23 21:21
 */
public class FixTrace {
	static final Pattern PATTERN = Pattern.compile(" *at ([0-9a-zA-Z_.]+)\\((Unknown Source|([0-9a-zA-Z_]+)\\.java:(\\d+))\\)");

	public static void main(String[] args) throws IOException {
		//Exception in thread "main" java.lang.ArrayIndexOutOfBoundsException: 0
		//        at roj.kscript.Test.main(Test.java:147)
		if (args.length < 1) {
			System.out.println("FixTrace <mapping>");
			return;
		}
		Mapping map = new Mapping();
		map.loadMap(new File(args[0]), false);
		BufferedReader in = UIUtil.in;
		PrintStream out = System.out;

		String line;
		Matcher m = PATTERN.matcher("");
		do {
			line = in.readLine();
			if (line == null) break;
			if (m.reset(line).matches()) {
				// class name + method name
				String s = m.group(0);
				int i = s.lastIndexOf('.');

				String cls = s.substring(0, i);
				cls = map.getClassMap().getOrDefault(cls.replace('.', '/'), cls).replace('/', '.');
				String method = s.substring(i + 1);
				for (Map.Entry<Desc, String> entry : map.getMethodMap().entrySet()) {
					Desc d = entry.getKey();
					if (d.name.equals(method) && d.owner.equals(cls)) {
						method = entry.getValue();
						break;
					}
				}
				out.println(line.substring(0, m.start()) + cls + '.' + method + "(" + "Line:Unsupported at this time" + ")" + line.substring(m.end()));
				// source file

				// line number
			} else {
				out.println(line);
			}
		} while (true);
	}
}
