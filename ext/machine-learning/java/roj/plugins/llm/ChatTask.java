package roj.plugins.llm;

import roj.collect.ArrayList;
import roj.config.ConfigMaster;
import roj.text.ParseException;
import roj.http.HttpClient;
import roj.http.HttpRequest;
import roj.text.CharList;
import roj.util.DynByteBuf;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.List;

/**
 * @author Roj234
 * @since 2025/3/12 7:24
 */
public class ChatTask {
	public transient URI endpointUrl = URI.create("http://127.0.0.1:5001/v1/chat/completions");

	public List<OAIChatCompletionRequest.Message> messages = new ArrayList<>();
	private transient int lastContextPos;
	public boolean contextAware;
	public int contextOffset, contextRingSize;
	public float temperature;

	public transient int totalInputTokens, totalOutputTokens;
	private transient int initInputTokens;

	public static ChatTask fromFile(File file) throws IOException, ParseException {return ConfigMaster.fromExtension(file).readObject(ChatTask.class, file).resolve();}
	public ChatTask() {}

	private transient OAIChatCompletionRequest request;
	private ChatTask resolve() {
		request = new OAIChatCompletionRequest();
		request.messages = new ArrayList<>(messages);
		request.temperature = temperature;
		return this;
	}
	public ChatTask reset() {return resolve();}

	@SuppressWarnings("unchecked")
	static <T> DynByteBuf encode(T o) throws IOException {return ConfigMaster.JSON.writeObject(/*(Serializer<T>) SerializerFactory.POOLED.serializer(o.getClass()), */o, DynByteBuf.allocate());}
	static <T> T decode(HttpClient client, Class<T> response) throws IOException, ParseException {return ConfigMaster.JSON.readObject(response, client.stream());}

	public String eval(String input) throws IOException {
		List<OAIChatCompletionRequest.Message> messages = request.messages;
		if (contextAware) {
			while (messages.size() - contextOffset > contextRingSize) {
				while (messages.get(contextOffset).role == OAIChatCompletionRequest.Role.user) messages.remove(contextOffset);
				while (messages.get(contextOffset).role != OAIChatCompletionRequest.Role.user) messages.remove(contextOffset);
			}
		} else {
			messages.remove(messages.size()-1);
		}
		lastContextPos = messages.size();

		request.add(OAIChatCompletionRequest.Role.user, input);

		var ob = new CharList();
		while (true) {
			OAIChatCompletionResponse response;
			try {
				response = decode(HttpRequest.builder().body(encode(request)).url(endpointUrl).executePooled(300000), OAIChatCompletionResponse.class);
			} catch (ParseException e) {
				throw new IOException("响应格式错误", e);
			}
			if (response == null) throw new IOException("响应格式错误");

			totalInputTokens += response.usage.prompt_tokens - initInputTokens;
			if (initInputTokens == 0) initInputTokens = response.usage.prompt_tokens;
			totalOutputTokens += response.usage.completion_tokens;

			var choice = response.choices.get(0);
			ob.append(choice.message.content);
			request.add(OAIChatCompletionRequest.Role.assistant, choice.message.content);
			if (choice.finish_reason.equals("stop")) break;
		}

		return ob.toStringAndFree();
	}

	public void rewindContext() {
		while (request.messages.size() > lastContextPos) {
			request.messages.remove(request.messages.size()-1);
		}
	}
}
