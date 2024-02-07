package roj.dev;

import roj.net.URIUtil;
import roj.util.ByteList;

import javax.tools.SimpleJavaFileObject;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * @author solo6975
 * @since 2021/10/2 14:00
 */
public class ByteListOutput extends SimpleJavaFileObject {
	private final String name;
	private final ByteList output;

	protected ByteListOutput(String className, String basePath) throws URISyntaxException {
		super(new URI("file://"+URIUtil.encodeURIComponent(basePath)+className.replace('.', '/') + ".class"), Kind.CLASS);
		this.output = new ByteList();
		this.name = className.replace('.', '/') + ".class";
	}

	@Override
	public String getName() { return name; }
	@Override
	public OutputStream openOutputStream() { return output; }
	public ByteList getOutput() { return output; }
}