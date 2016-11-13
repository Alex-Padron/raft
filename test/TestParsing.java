import org.junit.Test;

import com.google.gson.Gson;

public class TestParsing {

	@Test
	public void test() {
		Gson parser = new Gson();
		RequestVoteArgs args = new RequestVoteArgs(0, 0, 0, 0, 0);
		String s = parser.toJson(args, RequestVoteArgs.class);
		System.out.println(s);
		parser.toJson(args, RequestVoteArgs.class);
		Message m = new Message(Message.T.REQUEST_VOTE_ARGS, s, 1, 2);
		s = parser.toJson(m, Message.class);
		m = parser.fromJson(s, Message.class);
		System.out.println(s);
	}

}
