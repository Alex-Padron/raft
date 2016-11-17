import org.junit.Test;

import java.io.IOException;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class TestConsistency {
	private final long ELECTION_TIMEOUT = 5 * 1000;
	private final long CONSISTENCY_TIMEOUT = 20 * 1000;
	
	private int elect_leader(ArrayList<Raft<Double>> cluster) throws InterruptedException {
		Thread.sleep(ELECTION_TIMEOUT);
		int leader = -1;
		for (int i = 0; i < cluster.size(); i++) {
			if (cluster.get(i).is_leader()) {
				assert(leader == -1);
				leader = i;
			}
		}
		assert(leader != -1);
		return leader;
	}
	
	private void check_consistency(ArrayList<Raft<Double>> cluster, 
								   ArrayList<Queue<ApplyMsg<Double>>> apply_channels,
								   final int leader_index) throws InterruptedException {
		final int num_threads = 20;
		final int inserts_per_thread = 50;
		for (int i = 0; i < num_threads; i++) {
			new Thread(new Runnable() {
				public void run() {
					for (double k = 0; k < inserts_per_thread; k++) {
						cluster.get(leader_index).start(k);
					}
				}
			}).start();
		}
		int expected_index = 1;
		final Map<Integer, Double> index_to_cmd = new HashMap<>();
		final Map<Double, Integer> occurrences = new HashMap<>();
		int seen_messages = 0;
		final long start_time = System.currentTimeMillis();
		while (true) {
			if (apply_channels.get(0).peek() != null) {
				ApplyMsg<Double> entry = apply_channels.get(0).poll();
				assert(entry.index == expected_index);
				expected_index++;
				index_to_cmd.put(entry.index, entry.command);
				if (!occurrences.containsKey(entry.command)) 
					occurrences.put(entry.command, 0);
				occurrences.put(entry.command, occurrences.get(entry.command) + 1);
				seen_messages++;
			}
			if (seen_messages >= num_threads * inserts_per_thread) break;
			if (System.currentTimeMillis() > start_time + CONSISTENCY_TIMEOUT) {
				System.out.println("...Failed - timeout");
				assert(false);
			}
		}	
		// check that all servers have linear indexing and agree on order
		for (int i = 1; i < cluster.size(); i++) {
			seen_messages = 0;
			expected_index = 1;
			while (true) {
				if (apply_channels.get(i).peek() != null) {
					ApplyMsg<Double> entry = apply_channels.get(i).poll();
					assert(index_to_cmd.get(entry.index).equals(entry.command));
					assert(entry.index == expected_index);
					expected_index++;
					seen_messages++;
				}
				if (seen_messages >= num_threads * inserts_per_thread) break;
				if (System.currentTimeMillis() > start_time + CONSISTENCY_TIMEOUT) {
					System.out.println("...Failed - timeout");
					assert(false);
				}
			}
		}
		// check that the correct values were sent through on the apply channels
		for (double i = 0; i < inserts_per_thread; i++) {
			if (!occurrences.containsKey(i)) 
				System.out.println(" value " + i + " not in occurrences");
			assert(occurrences.get(i) == num_threads);
		}
	}
	
	// @Test
	public void test() throws InterruptedException, UnknownHostException, IOException {
		System.out.println("Testing Election no Failures...");
		
		final int size = 5;
		RaftSocket[] peers = new DefaultSocket[size];
		for (int i = 0; i < size; i++) {
			peers[i] = new DefaultSocket("localhost", 8000 + i);
		}
		ArrayList<Queue<ApplyMsg<Double>>> apply_channels = new ArrayList<>();
		for (int i = 0; i < size; i++) {
			apply_channels.add(new ConcurrentLinkedQueue<>());
		}
		ArrayList<Raft<Double>> cluster = new ArrayList<>();
		for (int i = 0; i < size; i++) {
			cluster.add(new Raft<Double>(peers, i, apply_channels.get(i), true));
		}
		
		for (int i = 0; i < size; i++) {
			Thread t = new Thread(cluster.get(i));
			t.start();
		}
		
		int leader_index = elect_leader(cluster);
		
		System.out.println("...passed");
		
		System.out.println("Testing Consistency no Failures...");
		
		check_consistency(cluster, apply_channels, leader_index);

		System.out.println("...passed");
	}

	@Test
	public void test2() throws SocketException, InterruptedException {
		final int size = 5;
		RaftSocket[] peers = new UnreliableSocket[size];
		UnreliableSocketNetwork net = new UnreliableSocketNetwork();
		for (int i = 0; i < size; i++) {
			peers[i] = new UnreliableSocket(net, i);
		}
		
		ArrayList<Queue<ApplyMsg<Double>>> apply_channels = new ArrayList<>();
		for (int i = 0; i < size; i++) {
			apply_channels.add(new ConcurrentLinkedQueue<>());
		}
		ArrayList<Raft<Double>> cluster = new ArrayList<>();
		for (int i = 0; i < size; i++) {
			cluster.add(new Raft<Double>(peers, i, apply_channels.get(i), true));
		}
		
		for (int i = 0; i < size; i++) {
			Thread t = new Thread(cluster.get(i));
			t.start();
		}
		
		System.out.println("Testing Election with Network Failures...");
		int leader_index = elect_leader(cluster);
		
		System.out.println("...passed");
		
		System.out.println("Testing Consistency with Network Failures...");
		
		check_consistency(cluster, apply_channels, leader_index);

		System.out.println("...passed");
	}
	
	
}






