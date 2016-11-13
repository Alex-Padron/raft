import java.io.IOException;
import java.lang.reflect.Type;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class Raft<T> implements Runnable {
	private enum Status {
		FOLLOWER,
		CANDIDATE,
		LEADER,
	}
	// given state
	private final InetSocketAddress[] peers;
	private final int me;
	private final Lock mu;
	private Persister persister;
	private final Queue<T> apply_channel;
	
	// volatile state
	private int commit_index;
	public int last_applied;
	private Status status;
	
	// leader state 
	private int[] next_index;
	private int[] match_index;
	private Map<Integer, Set<Integer>> replicated_count;
	private Map<Integer, Boolean> votes_recieved;
	private int election_number;
	
	// persisted state
	private int current_term;
	private int voted_for;
	private Log<T> log;
	
	// stateless helper objects
	private long next_heartbeat_time;
	private long next_election_time;
	private final Random rand;
	private final Gson parser;
	private final DatagramSocket client_socket;
	
	private final Queue<Boolean> send_snapshot;
	private final Queue<Boolean> send_apply_chan;
	
	private final Queue<Message> to_send;
	
	private final Queue<Message> request_vote_requests;
	private final Queue<Message> request_vote_replies;
	private final Queue<Message> append_entry_requests;
	private final Queue<Message> append_entry_replies;
	private final Queue<Message> snapshot_requests;
	private final Queue<Message> snapshot_replies;
	
	private final Type append_entry_arg_typ;
	private final Type append_entry_rep_typ;
	
	private static final Charset charset = StandardCharsets.UTF_8;
	private static final long HEARTBEAT_TIMEOUT = 60; // ms
	private static final long ELECTION_TIMEOUT = 250; // ms
	private static final long ELECTION_RANDOMIZER = 250; // ms
	
	public Raft(InetSocketAddress[] peers, int me, Queue<T> apply_channel) throws SocketException {
		this.peers = peers;
		this.client_socket = new DatagramSocket(peers[me]);
		this.me = me;
		this.status = Status.FOLLOWER;
		this.mu = new ReentrantLock();
		this.apply_channel = apply_channel;
		this.persister = new Persister();
		this.rand = new Random();
		this.parser = new Gson();
		this.log = new Log<>();
		this.send_apply_chan = new ConcurrentLinkedQueue<>();
		this.to_send = new ConcurrentLinkedQueue<>();
		this.request_vote_requests = new ConcurrentLinkedQueue<>();
		this.request_vote_replies = new ConcurrentLinkedQueue<>();
		this.append_entry_requests = new ConcurrentLinkedQueue<>();
		this.append_entry_replies = new ConcurrentLinkedQueue<>();
		this.snapshot_requests = new ConcurrentLinkedQueue<>();
		this.snapshot_replies = new ConcurrentLinkedQueue<>();
		this.append_entry_arg_typ = new TypeToken<AppendEntryArgs<T>>(){}.getType();
		this.append_entry_rep_typ = new TypeToken<AppendEntryReply<T>>(){}.getType();
		this.send_snapshot = new ConcurrentLinkedQueue<>();
		reset_timers();
	}
	
	public void run() {
		// set up spinning socket threads
		new Thread(new Runnable() {
			public void run() {
				try {
					spin_socket_recvs();
				} catch (IOException e) {
					System.out.println("Server " + me + " stopping reading socket");
					e.printStackTrace();
				}
			}
		}).start();
		new Thread(new Runnable() {
			public void run() {
				try {
					spin_socket_sends();
				} catch (IOException e) {
					System.out.println("Server " + me + " stopping sending socket");
					e.printStackTrace();
				}
			}
		}).start();
		
		while (true) {
			// check timers
			long current_time = System.currentTimeMillis();
			if (current_time > next_election_time && status != Status.LEADER)
				start_election(); 
			if (current_time > next_heartbeat_time && status == Status.LEADER) {
				send_heartbeats();
			}
			// check message queues
			if (request_vote_requests.peek() != null) {
				Message msg = request_vote_requests.poll();
				RequestVoteArgs args = parser.fromJson(msg.body, RequestVoteArgs.class);
				RequestVoteReply reply = handle_request_vote_request(args, msg.from);
				String reply_string = parser.toJson(reply, RequestVoteReply.class);
				to_send.add(new Message(Message.T.REQUEST_VOTE_REPLY, reply_string, msg.from, me));
			}
			if (request_vote_replies.peek() != null) {
				Message msg = request_vote_replies.poll();
				RequestVoteReply reply = parser.fromJson(msg.body, RequestVoteReply.class);
				handle_request_vote_reply(reply, msg.from);
			}
			if (append_entry_requests.peek() != null) {
				Message msg = append_entry_requests.poll();
				AppendEntryArgs<T> args = parser.fromJson(msg.body, append_entry_arg_typ);
				AppendEntryReply<T> reply = handle_append_entry_request(args, msg.from);
				String reply_string = parser.toJson(reply, append_entry_rep_typ);
				to_send.add(new Message(Message.T.APPEND_ENTRY_REPLY, reply_string, msg.from, me));
			}
			if (append_entry_replies.peek() != null) {
				Message msg = append_entry_replies.poll();
				AppendEntryReply<T> reply = parser.fromJson(msg.body, append_entry_rep_typ);
				handle_append_entry_reply(reply, msg.from);
			}
			if (snapshot_requests.peek() != null) {
				Message msg = snapshot_requests.poll();
				SnapshotArgs args = parser.fromJson(msg.body, SnapshotArgs.class);
				SnapshotReply reply = handle_snapshot_request(args, msg.from);
				String reply_string = parser.toJson(reply, SnapshotReply.class);
				to_send.add(new Message(Message.T.SNAPSHOT_REPLY, reply_string, msg.from, me));
			} 
			if (snapshot_replies.peek() != null) {
				Message msg = snapshot_replies.poll();
				SnapshotReply reply = parser.fromJson(msg.body, SnapshotReply.class);
				handle_snapshot_reply(reply, msg.from);
			}
			if (send_snapshot.peek() != null) {
				
			}
			// check internal queues 
			if (send_apply_chan.peek() != null) {
				send_apply_chan.poll();
				send_commits_to_apply_channel();
			}
		}
	}
	
	private void spin_socket_recvs() throws IOException {
		while (true) {
			byte[] buffer = new byte[1024];
			DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
			client_socket.receive(packet);
			String recv_s = new String(packet.getData(), charset).trim();
			Message recieved_message = parser.fromJson(recv_s, Message.class);
			assert(recieved_message.to == me);
			switch (recieved_message.type) {
			case REQUEST_VOTE_ARGS:
				request_vote_requests.add(recieved_message);
				break;
			case REQUEST_VOTE_REPLY:
				request_vote_replies.add(recieved_message);
				break;
			case APPEND_ENTRY_ARGS:
				append_entry_requests.add(recieved_message);
				break;
			case APPEND_ENTRY_REPLY:
				append_entry_replies.add(recieved_message);
				break;
			case SNAPSHOT_ARGS:
				snapshot_requests.add(recieved_message);
				break;
			case SNAPSHOT_REPLY:
				snapshot_replies.add(recieved_message);
				break;
			default: 
				throw new IOException("invalid message type " + recieved_message.type);
			}
		}
	}

	private void spin_socket_sends() throws IOException {
		byte[] buffer = new byte[1024];
		DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
		while (true) {
			Message msg = to_send.poll();
			if (msg == null) continue;
			packet.setData(parser.toJson(msg, Message.class).getBytes(charset));
			String recv_s = new String(packet.getData(), charset);
			packet.setSocketAddress(peers[msg.to]);
			client_socket.send(packet);
		}
	}

	// ------ MESSAGE HANDLERS -----------------------------------------------------------------
	
	private RequestVoteReply handle_request_vote_request(RequestVoteArgs args, int from) {
		if (args.term > current_term) {
			current_term = args.term;
			status = Status.FOLLOWER;
			voted_for = -1;
			reset_timers();
		}
		boolean vote_granted;
		if (args.term < current_term) {
			vote_granted = false;
		} else if (voted_for == -1 || voted_for == args.candidate_id) {
			if ((log.real_log_length() == 0 && log.last_included_index == 0) ||
				(args.last_log_entry_term > log.last_log_entry_term()) ||
				(args.last_log_entry_term == log.last_log_entry_term() && args.last_log_index >= log.real_log_length())) {
				vote_granted = true;
				voted_for = args.candidate_id;
				reset_timers();
			} else {
				vote_granted = false; // TODO clean up logic here
			}
		} else {
			vote_granted = false;
		}
		return new RequestVoteReply(current_term,
									vote_granted,
									me,
									args.candidate_id,
									args.term,
									args.election_number);
	}
	
	private void handle_request_vote_reply(RequestVoteReply reply, int from) {
		if (reply.term > current_term) {
			current_term = reply.term;
			status = Status.FOLLOWER;
			voted_for = -1;
			persist();
			reset_timers();
		} else if (reply.vote_granted &&
				   reply.election_number == election_number &&
				   status == Status.CANDIDATE) {
			votes_recieved.put(reply.voter_id, true);
			if (votes_recieved.size() > (peers.length / 2)) {
				initialize_as_leader();
			}
		}
	}
	
	private AppendEntryReply<T> handle_append_entry_request(AppendEntryArgs<T> args, int from) {
		if (args.term > current_term) {
			current_term = args.term;
			status = Status.FOLLOWER;
			voted_for = -1;
			reset_timers();
		}
		AppendEntryReply<T> reply = new AppendEntryReply<>();
		reply.prev_log_index = args.prev_log_index;
		reply.entries = args.entries; // might need to copy here
		if (args.term < current_term) {
			reply.success = false;
			reply.term = current_term;
			return reply;
		}
		if (args.prev_log_index <= 0) {
			reply.success = true;
			reply.term = current_term;
		} else if (args.prev_log_index > log.real_log_length()) {
			reply.success = false;
			reply.term = current_term;
			reply.conflicting_term_first_index = log.real_log_length() + 1;
			return reply;
		} else {
			int term_to_compare = log.get_term_at_index(args.prev_log_index);
			if (term_to_compare == -1) {
				reply.success = false;
				reply.term = current_term;
				reply.conflicting_term_first_index = log.last_included_index + 1;
				return reply;
			} else if (term_to_compare != args.prev_log_term) {
				reply.success = false;
				reply.term = current_term;
				reply.conflicting_entry_term = log.get_term_at_index(args.prev_log_index);
				for (int i = args.prev_log_index; i >= log.last_included_index; i--) {
					if (log.get_term_at_index(i) == reply.conflicting_entry_term) {
						reply.conflicting_term_first_index = i;
					} else {
						break;
					}
				}
				return reply;
			} else {
				reply.success = true;
				reply.term = current_term;
			}
		}
		int conflict_index = -1;
		for (int i = 0; i < args.entries.size(); i++) {
			if (args.entries.get(i).log_index >= log.last_included_index) {
				if (conflict_index == -1) conflict_index = i;
				if (args.entries.size() > log.real_log_length()) break;
				if (args.entries.get(i).term != log.get_term_at_index(args.entries.get(i).log_index)) {
					// clear up the log if there is a conflict
					ArrayList<LogEntry<T>> new_log = new ArrayList<>();
					for (int j = 0; j < args.entries.get(i).log_index - log.last_included_index; j++) {
						new_log.add(log.log.get(j));
					}
					log.log = new_log;
					break;
				}
			}
		}
		if (conflict_index != -1) {
			for (int i = conflict_index; i < args.entries.size(); i++) {
				if (args.entries.get(i).log_index > log.real_log_length()) {
					log.log.add(args.entries.get(i));
				}
			}
		}
		
		reset_timers();
		
		if (args.leader_commit > commit_index) {
			commit_index = Math.min(args.leader_commit, log.real_log_length());
			send_apply_chan.add(true);
		}
		persist();
		return reply;
	}
	
	private void handle_append_entry_reply(AppendEntryReply<T> reply, int from) {
		if (reply.term > current_term) {
			current_term = reply.term;
			status = Status.FOLLOWER;
			voted_for = -1;
			persist();
			reset_timers();
			return;
		}
		if (reply.success) {
			if (match_index[from] < reply.prev_log_index + reply.entries.size()) {
				match_index[from] = reply.prev_log_index + reply.entries.size();
				next_index[from] = match_index[from] + 1;
				for (int i = 0; i < reply.entries.size(); i++) {
					int log_index = reply.entries.get(i).log_index;
					if (!replicated_count.containsKey(log_index)) {
						replicated_count.put(log_index, new HashSet<>());
					}
					replicated_count.get(log_index).add(from);
					if (replicated_count.get(log_index).size() > (peers.length / 2) &&
						log_index > commit_index && 
						log.get_term_at_index(log_index) == current_term) 
					{
						commit_index = log_index;
						send_to_apply_chan();
					}
				}
			}
		} else {
			next_index[from] = 1;
		}
		persist();
	}
	
	private SnapshotReply handle_snapshot_request(SnapshotArgs args, int from) {
		if (args.term < current_term) {
			return new SnapshotReply(current_term, false);
		} else {
			current_term = args.term;
			status = Status.FOLLOWER;
			voted_for = -1;
			reset_timers();
		}
		if (log.get_term_at_index(args.last_included_index) == args.last_included_term) {
			save_snapshot(args.snapshot);
			remove_old_log_entries(args.last_included_index);
			send_snapshot.add(true);
			return new SnapshotReply(current_term, true);
		} else {	
			save_snapshot(args.snapshot);
			log.last_included_index = args.last_included_index;
			log.last_included_term = args.last_included_term;
			if (args.last_included_index > last_applied) {
				last_applied = args.last_included_index;
			}
			if (args.last_included_index > commit_index) {
				commit_index = args.last_included_index;
			}
			log.wipe();
			persist();
			send_snapshot.add(true);
			return new SnapshotReply(current_term, true);
		}
	}
	
	private void handle_snapshot_reply(SnapshotReply reply, int from) {
		
	}
	
	// -----------------------------------------------------------------------------------
	
	private void start_election() {
		votes_recieved = new HashMap<>();
		status = Status.CANDIDATE;
		current_term++;
		election_number++;
		votes_recieved.put(me, true);
		voted_for = me;
		System.out.println("Server "+ me + " starting election for term " + current_term);
		reset_timers();
		int last_log_entry_term;
		if (log.real_log_length() == 0) {
			last_log_entry_term = 0;
		} else {
			last_log_entry_term = log.last_log_entry_term();
		}
		
		RequestVoteArgs args = 
				new RequestVoteArgs(current_term, 
									me,
									log.real_log_length(),
									last_log_entry_term,
									election_number);
		for (int i = 0; i < peers.length; i++) {
			if (i == me) continue;
			String arg_string = parser.toJson(args, RequestVoteArgs.class);
			to_send.add(new Message(Message.T.REQUEST_VOTE_ARGS, arg_string, i, me));
		}
	}
	
	private void send_heartbeats() {
		if (status != Status.LEADER) return;
		for (int i = 0; i < peers.length; i++) {
			if (i == me) continue;
			int prev_log_index = next_index[i] - 1;
			if (prev_log_index <= 0) prev_log_index = 0;
			int potential_prev_log_term = log.get_term_at_index(prev_log_index);
			if (potential_prev_log_term == -1) {
				send_snapshot_heartbeat();
			} else {
				send_append_entry_heartbeat(i, potential_prev_log_term);
			}
		}
	}
	
	private void send_snapshot_heartbeat() {
		
	}

	private void send_append_entry_heartbeat(int i, int prev_log_term) {
		AppendEntryArgs<T> args = new AppendEntryArgs<>();
		args.term = current_term;
		args.leader_id = me;
		args.leader_commit = commit_index;
		args.prev_log_term = prev_log_term;
		args.prev_log_index = next_index[i] - 1;
		args.entries = new ArrayList<>();
		for (int j = next_index[i]; j < log.real_log_length(); j++) {
			if (j > 0)
				args.entries.add(log.get_entry_at(j));
		}
		String arg_string = parser.toJson(args, AppendEntryArgs.class);
		Message msg = new Message(Message.T.APPEND_ENTRY_ARGS, arg_string, i, me);
		to_send.add(msg);
	}
	
	private void send_to_apply_chan() {
		
	}
	private void initialize_as_leader() {
		System.out.println("Server " + me + 
				" became leader for term " + current_term);
		status = Status.LEADER;
		replicated_count = new HashMap<>();
		match_index = new int[peers.length];
		next_index = new int[peers.length];
		for (int i = 0; i < peers.length; i++) {
			next_index[i] = log.real_log_length() - 1;
		}
	}
	
	private void reset_timers() {
		next_election_time = System.currentTimeMillis() + ELECTION_TIMEOUT
				+ (long)(ELECTION_RANDOMIZER * rand.nextFloat());
		next_heartbeat_time = System.currentTimeMillis() + HEARTBEAT_TIMEOUT;
	}
	
	private void remove_old_log_entries(int new_last_included_index)  {
		
	}
	
	private void save_snapshot(byte[] snapshot) {
		
	}
	
	private void send_commits_to_apply_channel() {
		
	}
	
	public boolean is_leader() {
		return status == Status.LEADER;
	}
	
	
	public void persist() {
		
	}
}
