import java.net.Socket;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Raft<T> {
	private enum Status {
		FOLLOWER,
		CANDIDATE,
		LEADER,
	}
	// given state
	private final Socket[] peers;
	private final int me;
	private final Lock mu;
	private Persister persister;
	private final Queue<T> apply_channel;
	
	// volatile state
	private int commit_index;
	private int last_applied;
	private Status status;
	
	// leader state 
	private int[] next_index;
	private int[] match_index;
	private Map<Integer, Set<Integer>> replicated_count;
	
	// persisted state
	private int current_term;
	private int voted_for;
	private Log<T> log;
	
	// stateless helper objects
	private long heartbeat_time;
	private long election_time;
	
	public Raft(Socket[] peers, int me, Queue<T> apply_channel) {
		this.peers = peers;
		this.me = me;
		this.mu = new ReentrantLock();
		this.apply_channel = apply_channel;
		this.persister = new Persister();
	}
	
	private void sendHeartbeat() {
		
	}
}
