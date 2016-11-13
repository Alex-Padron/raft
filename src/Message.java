
public class Message {
	public enum T {
		REQUEST_VOTE_ARGS,
		REQUEST_VOTE_REPLY,
		APPEND_ENTRY_ARGS,
		APPEND_ENTRY_REPLY,
		SNAPSHOT_ARGS,
		SNAPSHOT_REPLY,
	}
	public final T type;
	public final String body;
	public final int to;
	public final int from;
	
	public Message(T type, String body, int to, int from) {
		this.type = type;
		this.body = body;
		this.to = to;
		this.from = from;
	}
}
