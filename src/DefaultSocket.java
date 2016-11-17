import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;

public class DefaultSocket implements RaftSocket {
	private final DatagramSocket socket;
	private final InetSocketAddress addr;
	
	public DefaultSocket(String hostname, int port) throws SocketException {
		this.socket = new DatagramSocket(port);
		this.addr = new InetSocketAddress(hostname, port);
	}
	
	@Override
	public void send(DatagramPacket packet) throws IOException {
		socket.send(packet);	
	}

	@Override
	public void receive(DatagramPacket packet) throws IOException {
		socket.receive(packet);
	}

	@Override
	public InetSocketAddress get_addr() {
		return addr;
	}

}
