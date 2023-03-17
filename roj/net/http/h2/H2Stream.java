package roj.net.http.h2;

import roj.collect.MyHashMap;
import roj.net.http.Headers;
import roj.net.http.HttpHead;
import roj.util.DynByteBuf;

/**
 * @author Roj234
 * @since 2022/10/7 0007 23:47
 */
public class H2Stream {
	public HttpHead header;
	public int rcvWindow;
	private MyHashMap<String, String> fake_header;
	private Headers headerField;

	public static final byte RESERVED=1,OPEN=2,PRE_CLOSE=3,CLOSED=4;
	public byte state, subState;
	public int id;

	public void local_error(int error) {
		state = CLOSED;
	}
	public void remote_error(int anInt) {
		state = CLOSED;
	}

	public void remote_priority(boolean exclusive, int before, int weight) {

	}

	public void header_data(DynByteBuf buf) {

	}
	public boolean more_header() {
		return false;
	}
	public void header_end() {
		// :status = 200 in response
		header = new HttpHead(true, fake_header.get("method"), fake_header.get("path"), "HTTP/2.0");
		header.putAll(headerField);
		//伪报头字段不是HTTP报头字段。端点不得生成本文档中定义的伪头字段以外的其他字段。请注意，一个扩展可以协商使用额外的伪头字段；见第5.5节。
		//
		//伪头字段只在它们被定义的环境中有效。为请求定义的伪头字段决不能出现在响应中；为响应定义的伪头字段决不能出现在请求中。伪头字段决不能出现在拖车部分。端点必须将包含未定义或无效的伪头字段的请求或响应视为畸形（第8.1.1节）。
		//
		//所有的伪头字段必须出现在一个字段块中，在所有常规字段行之前。任何包含伪头字段的请求或响应如果出现在常规字段行之后的字段块中，必须被视为畸形（第8.1.1节）。
		//
		//相同的伪头字段名不能在一个字段块中出现多次。一个HTTP请求或响应的字段块如果包含重复的伪头字段名，则必须被视为畸形（第8.1.1节）。

		//":scheme "伪标头字段包括请求目标的scheme部分。当直接生成一个请求时，方案取自目标URI（[RFC3986]第3.1节），或者取自翻译后的请求的方案（例如，见[HTTP/1.1]第3.3节）。对于CONNECT请求（第8.5节），scheme是省略的。
		//
		//":scheme "不限于 "http "和 "https "模式的URI。代理或网关可以翻译非HTTP方案的请求，使之能够使用HTTP与非HTTP服务交互。

		// ":authority "伪头域传达了目标URI（[HTTP]第7.1节）的授权部分（[RFC3986]第3.2节）。如果存在":authority"，HTTP/2请求的接收者不得使用Host头域来确定目标URI。
		//
		//直接生成HTTP/2请求的客户端必须使用":authority "伪标头字段来传达授权信息，除非没有授权信息需要传达（在这种情况下，它必须不生成":authority"）。
		//
		//客户端不得生成带有与":authority "伪标头字段不同的Host标头的请求。如果一个请求包含的Host头域标识的实体与":authority "伪头域中的实体不同，服务器应该将其视为畸形的请求。字段的值需要被规范化以进行比较（见[RFC3986]第6.2节）。原点服务器可以应用任何规范化方法，而其他服务器必须对这两个字段进行基于方案的规范化（见[RFC3986]第6.2.3节）。

		// 请注意，CONNECT或asterisk-form OPTIONS请求的目标绝不包括authority信息；见[HTTP]第7.1和7.2节。
	}

	public void data(DynByteBuf buf) {

	}

	public void close_input() {

	}

	public void changeWindow(int delta) {

	}

	public void tick() {

	}

	public void check_h2_header() {
		// 所有的HTTP/2请求都必须包括":method"、":scheme "和":path "伪头字段的一个有效值，除非它们是CONNECT请求（第8.5节）

		// key不得包含大写字母
		// key的冒号为保留字段，且仅能出现在第一个
		// key不得包含 Connection, Proxy-Connection, Keep-Alive, Transfer-Encoding, Upgrade...
		// 等在[HTTP]第7.6.1节中被列为具有连接特定语义的字段
		// 唯一的例外是TE头域，它可能出现在HTTP/2请求中；当它出现时，它必须不包含除 "trailer "以外的任何值。

		// 注意：HTTP/2特意不支持升级到其他协议。第3节中描述的握手方法被认为足以协商使用替代协议。

		// 8.2.3. 压缩Cookie头域
		//Cookie头字段[COOKIE]使用分号（"；"）来划分cookie对（或 "碎屑"）。这个头字段包含多个值，但不使用COMMA（","）作为分隔符，从而防止在多个字段行上发送cookie对（见[HTTP]第5.2条）。这可能会大大降低压缩效率，因为对单个cookie-pairs的更新会使存储在HPACK表中的任何字段行无效。
		//
		//为了实现更好的压缩效率，Cookie头字段可能被分割成单独的头字段，每个头字段有一个或多个cookie对。如果在解压缩后有多个Cookie头字段，在被传递到非HTTP/2上下文（如HTTP/1.1连接或普通HTTP服务器应用程序）之前，必须使用0x3b、0x20（ASCII字符串"；"）的两个八位数分隔符将这些字段连接成一个八位数字符串。


	}
}
