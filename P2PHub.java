import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.List;

public class P2PHub extends Thread {
	
	private ServerSocket welcomeSocket;
	private List<P2PThread> connections;
	
	/**
	 * Thread for accepting and messaging peer to peer sockets
	 * @param welcomeSocket: welcome socket to accept sockets
	 */
	P2PHub (ServerSocket welcomeSocket) {
		this.welcomeSocket = welcomeSocket;
		this.connections = new ArrayList<>();
	}
	
	@Override
	public void run() {
		
		Socket s = null;
		while (true) {
			try {
				// When accepting a socket, receive 1 message containing the other
				// user's name for reference
				s = welcomeSocket.accept();
				DataInputStream in = new DataInputStream(s.getInputStream());
				DataOutputStream out = new DataOutputStream(s.getOutputStream());
				String sender = in.readUTF();
				P2PThread t = new P2PThread(s, sender, in, out);
				connections.add(t);
				t.start();
			} catch (Exception e) {
				if (welcomeSocket.isClosed()) break;
			}
		}
		
	}
	
	/**
	 * Start a peer to peer connection with another user
	 * @param source: this client user
	 * @param dest: user to connect to
	 * @param port: other user's port to connect to
	 * @return true if the connection was successfully made
	 */
	public boolean makeConnection (String source, String dest, int port) {	
		try {
			Socket s = new Socket(InetAddress.getLocalHost(), port);
			DataOutputStream out = new DataOutputStream(s.getOutputStream());
			DataInputStream in = new DataInputStream(s.getInputStream());
			out.writeUTF(source);
			P2PThread t = new P2PThread(s, dest, in, out);
			connections.add(t);
			t.start();
			return true;
		} catch (Exception e) {
			return false;
		}
	}
	
	/**
	 * Check if this client has a private connection to another user
	 * @param user: target user
	 * @return true is there is an active private connection
	 */
	public boolean isConnectedTo (String user) {
		for (P2PThread t : connections) {
			if (user.equals(t.getSender()) && t.isAlive()) {
				return true;
			}
		}
		return false;
	}
	
	/**
	 * Send a private message to a certain user
	 * @param dest: target user
	 * @param message: body
	 */
	public void sendMessage (String dest, String message) {
		for (P2PThread t : connections) {
			if (dest.equals(t.getSender()) && t.isAlive()) {
				t.sendMessage(message);
				return;
			}
		}
		System.out.println("Error: Private messaging to " + dest + " not enabled");
	}
	
	/**
	 * Close all private connections
	 * Used for logging out and exiting
	 */
	public void closeConnections() {
		for (P2PThread t : connections) {
			if (t.isAlive()) t.sendMessage("stopprivate");
		}
	}
	
}