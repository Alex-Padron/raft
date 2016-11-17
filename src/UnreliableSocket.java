import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.util.Random;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class UnreliableSocket implements RaftSocket {
	private final UnreliableSocketNetwork parent;
	private final int port;
	private final Random rand;
	private final Lock lock;
	
	private static final float DROP_PERCENTAGE = 5;
	private static final long MAX_DELAY = 100; // ms
	private static final float DELAY_THRESHOLD = 0.9f;
	
	public UnreliableSocket(UnreliableSocketNetwork parent, int port) {
		this.parent = parent;
		this.port = port;
		this.rand = new Random();
		this.lock = new ReentrantLock();
	}

	@Override
	public void send(DatagramPacket packet) throws IOException {
		new Thread(new Runnable() {
			public void run() {
				float next = rand.nextFloat();
				if (next < (DROP_PERCENTAGE / 100)) return;
				if (next > DELAY_THRESHOLD) {
					try {
						Thread.sleep((long) (MAX_DELAY * (1 - next)));
					} catch (InterruptedException e) {
						return;
					}
				}
				lock.lock();
				parent.send(packet);
				lock.unlock();
			}
		}).start();
	}

	@Override
	public void receive(DatagramPacket packet) throws IOException {
		while (!parent.recv(packet, port));
	}

	@Override
	public InetSocketAddress get_addr() {
		return new InetSocketAddress("localhost", port);
	}
}
