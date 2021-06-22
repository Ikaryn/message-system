import java.io.*;
import java.net.*;

public class P2PThread extends Thread {
	
	private Socket s;
	private String sender;
	private DataInputStream in;
	private DataOutputStream out;
	
	/**
	 * Thread for peer to peer messaging with another user
	 * @param s: socket for communication
	 * @param sender: username of other user
	 * @param in: inputStream
	 * @param out: outputStream
	 */
	public P2PThread (Socket s, String sender, DataInputStream in, DataOutputStream out) {
		this.s = s;
		this.sender = sender;
		this.in = in;
		this.out = out;
	}
	
	@Override
	public void run () {
		
		while (true) {
			
			// If a stopprivate message is received, send one back and start close this thread
			// Else print the message as normal
			try {
				String data = in.readUTF();
				if (data.startsWith("stopprivate")) {
					System.out.println("Stopping private messaging with " + sender);
					out.writeUTF("stopprivate");
					break;
				} else {
					System.out.println(sender + " (private): " + data);
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		try {
			out.close();
			s.close();
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	
	}
	
	/**
	 * Send a message to the other user
	 * @param message: body to send
	 */
	public void sendMessage (String message) {
		try {
			out.writeUTF(message);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Get the other user being messaged with
	 */
	public String getSender() {
		return sender;
	}
}