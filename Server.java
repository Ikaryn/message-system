import java.io.*;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.net.*;
import java.time.*;

public class Server {
	
	private long block_duration;
	private long timeout;
	private static LocalDateTime startTime;
	
	private List<ClientHandler> clients;
	private List<String> usernames;
	private List<User> users;
	
	private ReentrantLock syncLock = new ReentrantLock();
	private static Debug debug = new Debug();
	
	/**
	 * Server object holding all important information
	 * @param serverPort: Port for the welcomeSocket
	 * @param block_duration: Time an account is locked for after 3 consecutive failed attempts
	 * @param timeout: Amount of available inactive time before user is automatically logged out
	 */
	public Server(long block_duration, long timeout) {
		this.block_duration = block_duration;
		this.timeout = timeout;
		clients = new ArrayList<>();
		usernames = new ArrayList<>();
		users = new ArrayList<>();
		generateUsers();
	}	
	
	/**
	 * Get the block duration of server
	 */
	public long getBlockDuration() {
		return block_duration;
	}
	
	/**
	 * Get the timeout of the server
	 */
	public long getTimeout() {
		return timeout;
	}

	/**
	 * Check if a user exists in the server (list made from credentials.txt)
	 * @param user: username to be checked
	 * @return true if the user exists
	 */
	public boolean userExists(String user) {
		return usernames.contains(user);
	}
	
	/**
	 * Get a user object
	 * @param user: username of the user to be retrieved
	 * @return the user object, null if it user doesn't exist
	 */
	public User getUser(String user) {
		int i = usernames.indexOf(user);
		if (i != -1) return users.get(i);
		else return null;
	}
	
	/**
	 * Check if a user is online/has logged in
	 * @param user: username of the user
	 * @return true if the user is currently online
	 */
	public boolean isOnline(String user) {
		return (getUser(user).isOnline());
	}
	
	/**
	 * Get all the online users either now or since a given time
	 * @param requester: username of the user that has requested it
	 * @param time: time since to check all logged in since then, null if only current is wanted
	 * @return a string of all valid users (ignoring requester) separated by a newline char
	 */
	public String getOnlineUsers(String requester, LocalDateTime time) {
		String result = "";
		if (time != null && time.isBefore(startTime)) time = startTime;
		for (User u : users) {
			debug.print("Checking user " + u.getUsername());
			if (u.getUsername().equals(requester)) continue;
			if (time == null) {
				if (u.isOnline()) result = result.concat(u.getUsername() + "\n");
			} else {
				if (u.wasOnline(time)) result = result.concat(u.getUsername() + "\n");
			}
		}
		return result;
	}
	
	/**
	 * Check if one user has blocked another user
	 * @param target: possible blocked user
	 * @param source: person possibly blocking the target
	 * @return true only if both exist and the source has blocked the target
	 */
	public boolean hasBlocked(String target, String source) {
		User blocker = getUser(source);
		User victim = getUser(target);
		if (blocker == null || victim == null) return false;
		if (blocker.hasBlocked(target)) return true;
		else return false;
	}
	
	/**
	 * Broadcast a message to all online users except the requester
	 * @param sender: person to who initialised the broadcast
	 * @param message: message to be broadcasted
	 * @param type: either a MESSAGE from a person or SERVER message e.g. login/logout
	 */
	public void broadcast (String sender, String message, String type) {
		lock();
		debug.print("Broadcasting a message");
		ClientHandler source = getClient(sender);
		boolean broadcastBlocked = false;
		for (ClientHandler client : clients) {
			debug.print("Checking " + client.getUsername() + " for broadcast");
			if (!client.isAlive()) continue;
			if (client.getUsername() != null && !client.getUsername().equals(sender)) {
				if (this.hasBlocked(sender, client.getUsername())) {
					broadcastBlocked = true;
				} else {
					client.sendMessage(sender, message, type);
				}		
			}
		}
		if (broadcastBlocked && type.equals("MESSAGE")) {
			source.sendMessage(sender, "Your message could not be delivered to some recipients", "SERVER");
		}
		unlock();
	}
	
	/**
	 * Get the port of the welcome socket of a user
	 * @param username: target user for port no to be retrieved from
	 * @return the port number
	 */
	public int getPort (String username) {
		for (ClientHandler c : clients) {
			if (username.equals(c.getUsername())) {
				return c.getWelcomePort();
			}
		}
		return -1;
	}
	
	/**
	 * Generate the username and user object list for the server
	 */
	public void generateUsers() {
		try {
			BufferedReader br = new BufferedReader (new FileReader("credentials.txt"));
			String line;
			while((line = br.readLine()) != null) {
				String[] contents = line.split(" ");
				if (usernames.contains(contents[0])) {
					System.out.println("Error: duplicate username " + contents[0] + " in credentials file");
				} else {
					usernames.add(contents[0]);
					users.add(new User(contents[0], contents[1]));
				}
			}
			br.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Synchronization lock
	 */
	public void lock() {
		syncLock.lock();
	}
	
	/**
	 * Synchronization unlock
	 */
	public void unlock() {
		syncLock.unlock();
	}
	
	/**
	 * Add a new thread to the client list
	 * @param s: new client to be added
	 */
	public void addClient(ClientHandler s) {
		clients.add(s);
	}
	
	/**
	 * Get the thread for a certain user
	 * @param username: desired user
	 * @return the active thread of the user
	 */
	public ClientHandler getClient(String username) {
		for (ClientHandler c : clients) {
			if (username.equals(c.getUsername())) return c;
		}
		return null;
	}
	
	/**
	 * For printing debugging information
	 */
	public boolean getDebug() {
		return debug.on;
	}
		 
	public static void main(String[] args) throws IOException {
		
		if (args.length == 4 && args[3].equals("-d")) debug.set(true);
		
		int serverPort = Integer.parseInt(args[0]);
		long block_duration = Integer.parseInt(args[1]);
		long timeout = Integer.parseInt(args[2]);
		startTime = LocalDateTime.now();
		
		Server server = new Server(block_duration, timeout);	
		
		@SuppressWarnings("resource")
		ServerSocket welcomeSocket = new ServerSocket(serverPort);
		debug.print("Server is ready at port: " + serverPort);
		
		while (true) {
			Socket s = null;
			try {	
				s = welcomeSocket.accept();
				debug.print("A new client is connected " + s);
				
				ObjectInputStream dis = new ObjectInputStream(s.getInputStream());
				ObjectOutputStream dos = new ObjectOutputStream(s.getOutputStream());
				
				debug.print("Assigning new thread for this client");
				
				ClientHandler t = new ClientHandler(server, s, dis, dos);
				server.clients.add(t);
				t.start();		
			} catch (Exception e) {
				s.close();
			}
		}
		
	}

}
