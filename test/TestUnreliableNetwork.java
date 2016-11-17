import java.io.IOException;
import java.net.DatagramPacket;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.junit.Test;

public class TestUnreliableNetwork {

	@Test
	public void test() throws IOException {
		final Charset charset = StandardCharsets.UTF_8;
		final int size = 5;
		UnreliableSocketNetwork net = new UnreliableSocketNetwork();
		RaftSocket[] peers = new UnreliableSocket[size];
		for (int i = 0; i < size; i++) {
			//peers[i] = net.add_socket(7000 + i);
		}
		byte[] buffer = new byte[1024];
		DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
		packet.setData("Hello World".getBytes());
		packet.setSocketAddress(peers[1].get_addr());
		for (int i = 0; i < 5; i++) 
			peers[0].send(packet);
		byte[] buffer2 = new byte[1024];
		DatagramPacket packet2 = new DatagramPacket(buffer2, buffer2.length);
		peers[1].receive(packet2);
		System.out.println(new String(packet.getData(), charset).trim());
	}

}
