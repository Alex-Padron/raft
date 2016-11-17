import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetSocketAddress;

public interface RaftSocket {
	public void send(DatagramPacket packet) throws IOException;
	
	public void receive(DatagramPacket packet) throws IOException;
	
	public InetSocketAddress get_addr();
}
