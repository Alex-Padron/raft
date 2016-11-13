import org.junit.Test;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class TestElection {

	@Test
	public void test() throws SocketException, InterruptedException {
		final long ELECTION_TIMEOUT = 2000;
		
		final int size = 5;
		InetSocketAddress[] peers = new InetSocketAddress[size];
		for (int i = 0; i < size; i++) {
			peers[i] = new InetSocketAddress("localhost", 8000 + i);
		}
		Queue<Integer> apply_channel = new ConcurrentLinkedQueue<>();
		ArrayList<Raft<Integer>> cluster = new ArrayList<>();
		for (int i = 0; i < size; i++) {
			cluster.add(new Raft<Integer>(peers, i, apply_channel));
		}
		
		for (int i = 0; i < size; i++) {
			Thread t = new Thread(cluster.get(i));
			t.start();
		}
		
		Thread.sleep(ELECTION_TIMEOUT);
		int leader_count = 0;
		for (int i = 0; i < size; i++) {
			if (cluster.get(i).is_leader()) {
				leader_count += 1;
			}
		}
		assert(leader_count == 1);
	}

}
