import org.junit.Test;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

public class TestElection {

	@Test
	public void test() throws SocketException, InterruptedException {
		System.out.println("Testing Election no Failures...");
		final long ELECTION_TIMEOUT = 1000;
		final long DISPERSION_TIMEOUT = 1000;
		
		final int size = 3;
		InetSocketAddress[] peers = new InetSocketAddress[size];
		for (int i = 0; i < size; i++) {
			peers[i] = new InetSocketAddress("localhost", 8000 + i);
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
		
		Thread.sleep(ELECTION_TIMEOUT);
		
		int leader_count = 0;
		int leader_index = 0;
		for (int i = 0; i < size; i++) {
			if (cluster.get(i).is_leader()) {
				leader_count += 1;
				leader_index = i;
			}
		}
		assert(leader_count == 1);
		
		Thread.sleep(2 * ELECTION_TIMEOUT);

		assert(cluster.get(leader_index).is_leader());
		
		System.out.println("...passed");
		
		System.out.println("Testing Agreement no Failures...");

		boolean resp = cluster.get(leader_index).start(1.0);
		assert(resp);
		resp = cluster.get(leader_index).start(2.0);
		assert(resp);
		resp = cluster.get(leader_index).start(3.0);
		assert(resp);
		resp = cluster.get((leader_index + 1) % size).start(3.0);
		assert(!resp);
	
		Thread.sleep(DISPERSION_TIMEOUT);
		for (int i = 0; i < size; i++) {
			for (int j = 0; j < 3; j++) {
				apply_channels.get(i).remove();
			}
			assert(apply_channels.get(i).peek() == null);
		}
		
		final int f_leader_index = leader_index;
		final int num_threads = 3;
		final int num_inserts = 2;
		for (int i = 0; i < num_threads; i++) {
			new Thread(new Runnable() {
				public void run() {
					for (double k = 0; k < num_inserts; k++) {
						assert(cluster.get(f_leader_index)).start(k);
					}
				}
			}).start();
		}
		
		Thread.sleep(DISPERSION_TIMEOUT);
		for (int i = 0; i < size; i++) {
			Map<Double, Integer> results = new HashMap<>();
			while(apply_channels.get(i).peek() != null) {
				double result = apply_channels.get(i).poll().command;
				if (!results.containsKey(result))
					results.put(result, 0);
				results.put(result, results.get(result) + 1);
			}
			for (double k = 0; k < num_inserts; k++) {
				assert(results.get(k) == num_threads);
			}
		}
		
		for (int i = 0; i < num_threads; i++) {
			new Thread(new Runnable() {
				public void run() {
					for (double k = 0; k < num_inserts; k++) {
						assert(cluster.get(f_leader_index)).start(k);
					}
				}
			}).start();
		}
		
		Thread.sleep(DISPERSION_TIMEOUT);
		
		Map<Integer, Double> index_to_cmd = new HashMap<>();
		int expected_index = 10; // TODO not nice to have to hard code this
		while(apply_channels.get(0).peek() != null) {
			ApplyMsg<Double> entry = apply_channels.get(0).poll();
			assert(entry.index == expected_index);
			expected_index++;
			index_to_cmd.put(entry.index, entry.command);
		}
		for (int i = 1; i < size; i++) {
			expected_index = 10;
			while(apply_channels.get(i).peek() != null) {
				ApplyMsg<Double> entry = apply_channels.get(i).poll();
				assert(index_to_cmd.get(entry.index).equals(entry.command));
				assert(entry.index == expected_index);
				expected_index++;
			}
		}
		System.out.println("...passed");
	}

}
