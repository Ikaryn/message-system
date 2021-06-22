import java.io.*;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.net.*;

class ClientHandler extends Thread {
	
	private Server server;
	private Socket s;
	private ObjectInputStream in;
	private ObjectOutputStream out;
	private String username;
	private User user;
	private int welcomePort;
	private Debug debug = new Debug();
	
	/**
	 * Thread to handle 1 client for the server
	 * @param server: server running this thread
	 * @param s: socket to connect to client
	 * @param in: inputStream
	 * @param out: outputStream
	 */
	public ClientHandler (Server server, Socket s, ObjectInputStream in, ObjectOutputStream out) {
		this.server = server;
		this.s = s;
		this.in = in;
		this.out = out;
		this.username = null;
		this.user = null;
		debug.set(server.getDebug());
	}
	
	@Override
	public void run() {
		Packet received = null;
		Packet toReturn;
		String[] tokens;
		String target;
		boolean exitStatus = false;
		
		while (!exitStatus) {
			
			try {
				
				// Receive the packet and extract general data
				received = (Packet) in.readObject();
				server.lock();
				String type = received.getType();
				debug.print("Received packet type: " + type);
				String payload = received.getPayload();
				
				switch(type) {
				
				case "LOGIN":
					tokens = payload.split(" ");
					
					// Get the appropriate attempt login status
					String loginStatus = checkCredentials(tokens[0], tokens[1]);
					toReturn = new Packet("LOGIN", loginStatus);
					out.writeObject(toReturn);
					
					// If the login succeeded, initialize timeout and send offline messages
					if (loginStatus.equals("SUCCESS")) {
						s.setSoTimeout((int)server.getTimeout()*1000);
						user = server.getUser(username);
						user.goOnline();
						server.broadcast(username, username + " logged in", "SERVER");
						List<Packet> messages = user.getMessages();
						for (Packet p : messages) out.writeObject(p);
						user.clearMessges();
					}
					break;
					
				case "WELCOMEPORT":
					// For registering the port number of a client
					int portNo = Integer.parseInt(payload);
					welcomePort = portNo; 
					break;
				
				case "MESSAGE":
					target = received.getDest();
					User destination = server.getUser(target);
					
					// Check all other variables before sending message to user
					if (target.equals(username)) {
						toReturn = new Packet("SERVER", "Error: Cannot message yourself");
						out.writeObject(toReturn);
					} else if (destination == null) {
						toReturn = new Packet("SERVER", "Error: Invalid User");
						out.writeObject(toReturn);
					} else if (!destination.isOnline()) {
						toReturn = new Packet("MESSAGE", received.getPayload());
						toReturn.setSender(username);
						destination.addMessage(toReturn);
					} else if (destination.hasBlocked(username)) {
						toReturn = new Packet("SERVER", "Your message could not be delivered as the recipient has blocked you");
						out.writeObject(toReturn);
					} else {
						server.getClient(target).sendMessage(username, payload, "MESSAGE");;
					}
					break;
				
				case "BROADCAST":
					broadcast("MESSAGE", payload);
					break;
					
				case "WHOELSE":
					String onlineUsers = server.getOnlineUsers(username, null);
					out.writeObject(new Packet("SERVER", onlineUsers));
					break;
					
				case "WHOELSESINCE":
					long diff = Long.parseLong(payload);
					LocalDateTime then = LocalDateTime.now().minus(diff, ChronoUnit.SECONDS);
					String pastOnlineUsers = server.getOnlineUsers(username, then);
					out.writeObject(new Packet("SERVER", pastOnlineUsers));
					break;
					
				case "BLOCK":
					target = payload;
					toReturn = new Packet("SERVER", null);
					
					// Check other variables first and send error if needed
					if (!server.userExists(target)) {
						toReturn.setPayload("Error: Invalid User");
					} else if (target.equals(username)) {
						toReturn.setPayload("Error: Cannot block/unblock self");
					} else if (user.hasBlocked(target)) {
						toReturn.setPayload("Error: " + target + " is already blocked");
					} else {
						user.blockUser(target);
						toReturn.setPayload(target + " is blocked");
					}
					out.writeObject(toReturn);
					break;
					
				case "UNBLOCK":
					target = payload;
					toReturn = new Packet("SERVER", null);
					
					// Check other variables first and send error if needed
					if (!server.userExists(target)) {
						toReturn.setPayload("Error: Invalid user");
					} else if (target.equals(username)) {
						toReturn.setPayload("Error: Cannot block/unblock self");
					} else if (!user.hasBlocked(target)) {
						toReturn.setPayload("Error: " + target + " was not blocked");
					} else {
						user.unblockUser(target);
						toReturn.setPayload(target + " is unblocked");
					}
					out.writeObject(toReturn);
					break;
					
				case "STARTPRIVATE":
					target = payload;
					// Get the port and username data from the server to send
					int port = server.getPort(target);
					String socketInfo = username + " " + target + " " + Integer.toString(port);
					
					// If the user has blocked the requester, cannot initialise private messagning
					if (target.equals(username)) {
						toReturn = new Packet("SERVER", "Error: Cannot private message self");
					}else if (server.hasBlocked(username, target)) {
						toReturn = new Packet("SERVER", "Error: " + target + " has blocked you. Cannot start private messaging");
					} else {
						toReturn = new Packet ("STARTPRIVATE", socketInfo);
					}
					out.writeObject(toReturn);
					break;	
				
					
				case "LOGOUT":
					user.goOffline();
					// Sending logout acknowledgement
					out.writeObject(new Packet("LOGOUT", null));
					// Notify other users
					server.broadcast(username, username + " logged out", "SERVER");
					this.username = null;
					this.user = null;
					// Turn off the timeout
					s.setSoTimeout(0);
					break;
					
				case "EXIT":
					debug.print("Client " + this.s + " sends exit...");
					debug.print("Closing this connection");
					user.goOffline();
					// Sending exit acknowledgement
					out.writeObject(new Packet("EXIT", null));
					Thread.sleep(100);
					this.s.close();
					Thread.sleep(100);
					debug.print("Connection closed");
					// Notify other users
					server.broadcast(username, username + " logged out", "SERVER");
					exitStatus = true;
					this.username = null;
					this.user = null;
					break;	

				default:
					out.writeObject(new Packet("ERROR", null));
					break;
				}
				server.unlock();
				
			} catch (SocketTimeoutException timeout) {
				// Timeout due to client inactivity
				user.goOffline();
				server.broadcast(username, username + " logged out", "SERVER");
				toReturn = new Packet("TIMEOUT", null);
				this.username = null;
				this.user = null;
				try {
					s.setSoTimeout(0);
					out.writeObject(toReturn);
				} catch (IOException e) {
					debug.print(e.getMessage());
				}
				
			} catch (SocketException forceClose) {
				if (username == null) {
					debug.print("Client force closed");
				} else {
					debug.print("User " + username + " force closed");
					user.goOffline();
				}
				this.username = null;
				this.user = null;
				break;
				
			} catch (Exception e) {
				debug.print(e.getMessage());
			}
		}
		
		try {
			this.in.close();
			this.out.close();
		} catch (Exception e) {
			debug.print(e.getMessage());
		}
	}
	
	/**
	 * Get the username of this thread's account
	 */
	public String getUsername() {
		return username;
	}
	
	/**
	 * Send broadcast message to all other active users
	 * @param type: either MESSAGE or SERVER notifcation e.g. login
	 * @param message: message to be broadcasted
	 */
	public void broadcast(String type, String message) {
		server.broadcast(this.username, message, type);
	}
	
	/**
	 * Send a message to the client connected to this thread
	 * @param sender: message source
	 * @param message: body
	 * @param type: either MESSAGE from a person or a SERVER message
	 */
	public void sendMessage(String sender, String message, String type) {
		try {
			Packet packet = new Packet(type, message);
			packet.setSender(sender);			
			if (!server.hasBlocked(sender, username)) out.writeObject(packet);
		} catch (IOException e) {
			debug.print(e.getMessage());
		}
	}
	
	/**
	 * Check to see if a client can log into the message system
	 * @param username: name to be checked against in the user list
	 * @param password: password attempt
	 * @return a string of the status code from the attempt
	 * The codes are the following:
	 * USERNAME: Invalid username/Unknown username
	 * ONLINE: The user is already logged in
	 * BLOCKED: The user was already locked out
	 * SUCCESS: Successful login
	 * BLOCK: The the user failed to login and has been locked out
	 * PASSWORD: Incorrect password but they have not been locked out
	 */
	public String checkCredentials (String username, String password) {
		User u = server.getUser(username);
		if (u == null) return "USERNAME";
		else if (u.isOnline()) return "ONLINE";
		else if (u.isLockedOut(server.getBlockDuration())) return "BLOCKED";
		else {
			boolean valid = u.checkPassword(password);
			if (valid) {
				u.resetAttempts();
				u.goOnline();
				this.username = username;
				return "SUCCESS";	
			} else {
				if (u.getLoginAttempts() == 3) {
					u.lockOut();
					return "BLOCK";
				} else return "PASSWORD";
			}
		}		
	}
	
	/**
	 * Get the welcome socket port number of the client
	 */
	public int getWelcomePort () {
		return welcomePort;
	}

}