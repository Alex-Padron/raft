import org.junit.Test;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
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
		ArrayList<Queue<ApplyMsg<Integer>>> apply_channels = new ArrayList<>();
		for (int i = 0; i < size; i++) {
			apply_channels.add(new ConcurrentLinkedQueue<>());
		}
		ArrayList<Raft<Integer>> cluster = new ArrayList<>();
		for (int i = 0; i < size; i++) {
			cluster.add(new Raft<Integer>(peers, i, apply_channels.get(i), true));
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
		
		System.out.println("...passed");
		
		System.out.println("Testing Agreement no Failures...");
		boolean resp = cluster.get(leader_index).start(1);
		assert(resp);
		resp = cluster.get(leader_index).start(2);
		assert(resp);
		resp = cluster.get((leader_index + 1) % size).start(3);
		assert(!resp);
	
		Thread.sleep(DISPERSION_TIMEOUT);
		for (int i = 0; i < size; i++) {
			for (int j = 0; j < 2; j++) {
				apply_channels.get(i).remove();
			}
		}
		final int f_leader_index = leader_index;
		for (int i = 0; i < 2; i++) {
			new Thread(new Runnable() {
				public void run() {
					for (int k = 0; k < 3; k++) {
						assert(cluster.get(f_leader_index)).start(k);
					}
				}
			}).start();
		}
		
		Thread.sleep(DISPERSION_TIMEOUT);
		
		for (int i = 0; i < size; i++) {
			final int j = i;
			new Thread(new Runnable() {
				public void run() {
					Map<Integer, Integer> results = new HashMap<>();
					while(apply_channels.get(j).peek() != null) {
						int result = apply_channels.get(j).poll().command;
						if (!results.containsKey(result))
							results.put(result, 0);
						results.put(result, results.get(result) + 1);
					}
					for (int k = 0; k < 1000; k++) {
						assert(results.get(k) == 10);
					}
				}
			}).start();
		}
		System.out.println("...passed");
	}

}
