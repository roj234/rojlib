package roj.plugins.ddns;

import org.jetbrains.annotations.Nullable;
import roj.collect.ArrayList;
import roj.collect.BitSet;
import roj.collect.HashMap;
import roj.concurrent.Task;
import roj.concurrent.TaskThread;
import roj.config.JsonParser;
import roj.config.node.ListValue;
import roj.config.node.MapValue;
import roj.crypt.HMAC;
import roj.http.HttpClient;
import roj.http.HttpRequest;
import roj.io.IOUtil;
import roj.text.CharList;
import roj.text.DateFormat;
import roj.text.URICoder;
import roj.ui.Tty;
import roj.util.ByteList;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Map;
import java.util.Random;

import static roj.plugins.ddns.IpGetter.pooledRequest;

/**
 * @author Roj234
 * @since 2023/1/27 19:03
 */
final class Aliyun implements IpMapper {
	private static final String API_URL = "https://alidns.aliyuncs.com";
	private static final String DEFAULT_API_VERSION = "2015-01-09";
	private static final String DEFAULT_SIGNATURE_METHOD = "HMAC-SHA1";
	private static final String DEFAULT_SIGNATURE_VERSION = "1.0";

	private String AccessKeyId, AccessKeySecret;
	private final Random rnd = new SecureRandom();

	private String makeUrl(Map<String, String> param) {
		Map<String, String> queries = new HashMap<>(param);
		queries.put("Format", "JSON");
		queries.put("Version", DEFAULT_API_VERSION);
		queries.put("SignatureMethod", DEFAULT_SIGNATURE_METHOD);
		queries.put("SignatureVersion", DEFAULT_SIGNATURE_VERSION);
		queries.put("AccessKeyId", AccessKeyId);

		byte[] nonce = new byte[16];
		rnd.nextBytes(nonce);
		queries.put("SignatureNonce", IOUtil.encodeHex(nonce));
		queries.put("Timestamp", DateFormat.format(DateFormat.ISO8601_Seconds, System.currentTimeMillis()).toString());

		//计算签名
		String signature = makeSign(queries, AccessKeySecret);

		return API_URL+"/?"+makeQuery(queries)+"&Signature="+signature;
	}

	private static String makeQuery(Map<String, String> queries) {
		var sb = IOUtil.getSharedCharBuf();
		for (Map.Entry<String, String> p : queries.entrySet()) {
			sb.append("&").append(URICoder.encodeURIComponent(p.getKey()))
			  .append("=").append(URICoder.encodeURIComponent(p.getValue()));
		}
		return sb.substring(1);
	}
	private static String makeSign(Map<String, String> queries, String accessSecret) {
		ArrayList<Map.Entry<String, String>> sorted = new ArrayList<>(queries.entrySet());
		sorted.sort((o1, o2) -> o1.getKey().compareTo(o2.getKey()));

		ByteList sb = new ByteList();
		String s = "";
		for (Map.Entry<String, String> p : sorted) {
			sb.putAscii(s).putAscii(encodeSignature(p.getKey()))
			  .putAscii("=").putAscii(encodeSignature(p.getValue()));
			s = "&";
		}
		String signStr = "GET&%2F&"+encodeSignature(sb);

		try {
			HMAC hmac = new HMAC(MessageDigest.getInstance("SHA-1"));
			hmac.init(accessSecret.concat("&").getBytes(StandardCharsets.UTF_8));

			hmac.update(IOUtil.encodeUTF8(signStr));
			return encodeSignature(IOUtil.encodeBase64(hmac.digestShared())).toString();
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	private static final BitSet aliyun_pass = BitSet.from("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_.~");
	private static CharList encodeSignature(CharSequence src) {return URICoder.pEncodeW(IOUtil.getSharedCharBuf(), src instanceof ByteList b ? b : IOUtil.getSharedByteBuf().putUTFData(src), aliyun_pass);}

	private final Map<String, DomainRecord> domainRecords = new HashMap<>();
	static final class DomainRecord {
		String domain, RR;
		InetAddress v4Addr, v6Addr;
		Object v4Id, v6Id;
	}

	private final TaskThread th = new TaskThread();

	@Override
	public void init(MapValue config) {
		AccessKeyId = config.getString("AccessKey");
		AccessKeySecret = config.getString("AccessSecret");

		th.start();

		Map<String, String> params = new HashMap<>();
		params.put("Action", "DescribeDomainRecords");
		// *域名名称
		// string DomainName
		// 当前页数，起始值为1，默认为1
		// int PageNumber = 1;
		// 分页查询时设置的每页行数，最大值500，默认为20
		// int PageSize = 20;
		// 主机记录的关键字，按照”%RRKeyWord%”模式搜索，不区分大小写
		// string RRKeyWord
		// 解析类型的关键字，按照全匹配搜索，不区分大小写
		// string TypeKeyWord = "A";
		// 记录值的关键字，按照”%ValueKeyWord%”模式搜索，不区分大小写
		// string ValueKeyWord

		for (var entry : config.getMap("Domains").entrySet()) {
			String zone = entry.getKey();

			params.put("DomainName", zone);
			params.put("PageSize", "100");

			var subDomains = entry.getValue().asList().raw();
			String subDomain = null;
			for (int i = 0; i < subDomains.size(); i++) {
				subDomain = subDomains.get(i).asString();

				int pos = subDomain.lastIndexOf(zone)-1;
				if (pos < 0) System.err.println("Invalid sub domain "+subDomain);
				else {
					DomainRecord record = new DomainRecord();
					record.domain = zone;
					record.RR = subDomain.substring(0, pos);
					domainRecords.put(subDomain, record);
				}
			}

			if (subDomains.size() == 1) {
				params.put("RRKeyWord", domainRecords.get(subDomain).RR);
			} else {
				params.remove("RRKeyWord");
			}

			params.put("TypeKeyWord", "A");
			th.executeUnsafe(_init(params));

			params.put("TypeKeyWord", "AAAA");
			th.executeUnsafe(_init(params));
		}

		th.awaitTermination();
		th.shutdown();
	}


	@Override
	public void update(@Nullable InetAddress addr4, @Nullable InetAddress addr6) {
		for (var record : domainRecords.values()) {
			if (addr4 != null && !addr4.equals(record.v4Addr)) {
				record.v4Addr = addr4;
				if (record.v4Id == null) {
					_add(record.domain, record.RR, addr4.getHostAddress(), "A");
				} else {
					_update(record.v4Id, record.RR, addr4.getHostAddress(), "A");
				}
			}

			if (addr6 != null && !addr6.equals(record.v6Addr)) {
				record.v6Addr = addr6;
				if (record.v6Id == null) {
					_add(record.domain, record.RR, addr6.getHostAddress(), "AAAA");
				} else {
					_update(record.v6Id, record.RR, addr6.getHostAddress(), "AAAA");
				}
			}
		}

		// ==DeleteDomainRecord==
		// 解析记录的ID，此参数在添加解析时会返回，在获取域名解析列表时会返回
		// string RecordId

		// ==SetDomainRecordStatus==
		// 解析记录的ID，此参数在添加解析时会返回，在获取域名解析列表时会返回
		// string RecordId
		// 是否启用, Enable: 启用解析 Disable: 暂停解析
		// string Enable
	}

	@Override
	public void close() {
		th.shutdown();
	}

	// ==AddDomainRecord==
	// 解析记录的ID，此参数在添加解析时会返回，在获取域名解析列表时会返回
	// string DomainName
	// 其余参数同上
	private void _add(String domain, String RR, String addr, String type) {
		Map<String, String> par = new HashMap<>();
		par.put("Action", "AddDomainRecord");
		par.put("DomainName", domain);
		par.put("RR", RR);
		par.put("Value", addr);
		par.put("Type", type);

		try {
			HttpClient shc = HttpRequest.builder().url(makeUrl(par)).executePooled();
			MapValue cfg = _parse(shc);
		} catch (Exception e) {
			Tty.error("请求参数: " + par, e);
		}
	}

	// ==UpdateDomainRecord==
	// 解析记录的ID，此参数在添加解析时会返回，在获取域名解析列表时会返回
	// string RecordId
	// 主机记录，如果要解析@.exmaple.com，主机记录要填写"@”，而不是空
	// string RR
	// 解析记录类型，参见解析记录类型格式(https://help.aliyun.com/document_detail/29805.html)
	// string Type = "A";
	// 记录值
	// string Value { get; set; }
	// 生存时间，默认为600秒（10分钟），参见TTL定义说明(https://help.aliyun.com/document_detail/29806.html)
	// int TTL { get; set; } = 600;
	// MX记录的优先级，取值范围[1,10]，记录类型为MX记录时，此参数必须
	// int Priority { get; set; }
	// 解析线路，默认为default。参见解析线路枚举(https://help.aliyun.com/document_detail/29807.html)
	// string Line
	private void _update(Object id, String RR, String addr, String type) {
		Map<String, String> par = new HashMap<>();
		par.put("Action", "UpdateDomainRecord");
		par.put("RecordId", id.toString());
		par.put("RR", RR);
		par.put("Value", addr);
		par.put("Type", type);

		try {
			MapValue cfg = _parse(pooledRequest(makeUrl(par)));
		} catch (Exception e) {
			Tty.error("请求参数: " + par, e);
		}
	}

	private MapValue _parse(HttpClient shc) throws Exception {
		MapValue map = new JsonParser().parse(shc.stream()).asMap();
		if (map.containsKey("Message"))
			throw new IllegalArgumentException("API错误: " + map.getString("Message"));
		return map;
	}

	private Task _init(Map<String, String> param) {
		var url = makeUrl(param);
		return () -> {
			MapValue cfg = _parse(pooledRequest(url));
			ListValue list = cfg.query("DomainRecords.Record").asList();
			for (int i = 0; i < list.size(); i++) {
				MapValue data = list.get(i).asMap();
				String name = data.getString("RR")+"."+data.getString("DomainName");

				DomainRecord record = domainRecords.get(name);
				if (record != null) {
					switch (data.getString("Type")) {
						case "A":
							record.v4Addr = InetAddress.getByName(data.getString("Value"));
							record.v4Id = data.getString("RecordId");
							break;
						case "AAAA":
							record.v6Addr = InetAddress.getByName(data.getString("Value"));
							record.v6Id = data.getString("RecordId");
							break;
					}
				}
			}
		};
	}
}