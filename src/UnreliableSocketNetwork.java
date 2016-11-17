import java.net.DatagramPacket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.google.gson.Gson;

public class UnreliableSocketNetwork {
	private final Map<Integer, ArrayList<DatagramPacket>> buffers;
	private final Lock lock;
	
	public UnreliableSocketNetwork() {
		buffers = new HashMap<>();
		lock = new ReentrantLock();
	}
	
	public void send(DatagramPacket packet) {
		lock.lock();
		int port_to_insert = packet.getPort();
		if (!buffers.containsKey(port_to_insert))
			buffers.put(port_to_insert, new ArrayList<>());
		buffers.get(port_to_insert).add(packet);
		lock.unlock();
	}
	
	public boolean recv(DatagramPacket packet, int port) {
		lock.lock();
		if ((!buffers.containsKey(port)) || buffers.get(port).size() == 0) {
			lock.unlock();
			return false;
		}
		DatagramPacket new_packet = buffers.get(port).remove(0);
		packet.setData(new_packet.getData());
		lock.unlock();
		return true;
	}
	
	// lock should be held calling this
	@SuppressWarnings("unused")
	private void check_rep() {
		for (int port : buffers.keySet()) {
			ArrayList<DatagramPacket> packets = buffers.get(port);
			for (DatagramPacket packet : packets) {
				String recv_s = new String(packet.getData(), StandardCharsets.UTF_8).trim();
				Message msg = new Gson().fromJson(recv_s, Message.class);
				if (msg.to != port)
					System.out.println("message to " + msg.to + " in buffer " + port);
				assert(msg.to == port);
			}
		}
	}
}





