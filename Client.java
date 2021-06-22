import java.io.*;
import java.net.*;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.locks.ReentrantLock;

public class Client extends Thread {
	
	static Socket s;
	static ObjectInputStream in;
	static ObjectOutputStream out;
	static Scanner scn;
	static private ReentrantLock syncLock = new ReentrantLock();
	static P2PHub peerConnections;
	static Client client;
	static boolean loggedIn;
	static boolean exitStatus;
	static String username;
	
	public static void main (String[] args) throws Exception {
			
			// Getting and starting sockets
			InetAddress ip = InetAddress.getByName(args[0]);
			int port = Integer.parseInt(args[1]);
			s = new Socket(ip, port);
			ServerSocket welcomeSocket = new ServerSocket(0);
			
			// Setting initial exit states
			loggedIn = false;
			exitStatus = false;
			
			// Initializing streams
			if (out == null) out = new ObjectOutputStream(s.getOutputStream());
			if (in == null) in = new ObjectInputStream(s.getInputStream());
			scn = new Scanner(System.in);
			
			// Initializing threads
			client = new Client();
			client.start();
			peerConnections = new P2PHub(welcomeSocket);
			peerConnections.start();
			
			// Main function receives packets
			while (true) {
				try {
					
					if (!loggedIn) System.out.println("Pleast enter your username:");
					
					// Receive a packet and extract general data
					Packet packetIn = (Packet) in.readObject();
					syncLock.lock();
					String type = packetIn.getType();
					String payload = packetIn.getPayload();
					
					// Do not process any other packets than login and logout if the user has not logged in
					if (!(type.equals("LOGIN") || type.equals("LOGOUT")) && !loggedIn) {
						continue;
					}
					
					switch(type) {
					
					// Receive and process login attempt status
					case "LOGIN":
						String loginStatus = payload;
						switch (loginStatus) {
						case "SUCCESS":
							System.out.println("Welcome to the greatest messaging application ever!");
							int portNo = welcomeSocket.getLocalPort();
							// Send the client's port number for private messaging
							Packet welcome = new Packet("WELCOMEPORT", Integer.toString(portNo));
							out.writeObject(welcome);
							loggedIn = true;
							break;
						case "BLOCK":
							System.out.println("Invalid Password. Your account has been blocked. Please try again later");
							break;
						case "BLOCKED":
							System.out.println("Your account is blocked due to multiple login failures. Please try again later");
							break;
						case "USERNAME":
							System.out.println("Invalid username. Please try again");
							break;
						case "PASSWORD":
							System.out.println("Invalid password. Please try again");
							break;
						case "ONLINE":
							System.out.println("This user is already online, please try another account");
							break;
						default:
							System.out.println("Something went wrong, please try again");
							break;
						}
						break;
						
					// Receive a message from another user
					case "MESSAGE":
						System.out.println(packetIn.getSender() + ": " + payload);
						break;
						
					// Make a new private connection
					// Payload contains all parameters to make the new connection
					case "STARTPRIVATE":
						String[] socketInfo = payload.split(" ");
						String source = socketInfo[0];
						String target = socketInfo[1];
						int targetPort = Integer.parseInt(socketInfo[2]);
						
						if (peerConnections.makeConnection(source, target, targetPort)) {
							System.out.println("Start private messaging with " + target);
						} else {
							System.out.println("Error: Failed to make private connection with " + target);
						}
						break;
					
					// Acknowledgement for logout command
					case "LOGOUT":
						System.out.println("You have been logged out");
						loggedIn = false;
						break;	
						
					// Acknowledgement for exit command
					case "EXIT":
						System.out.println("You have been logged out");
						System.out.println("Goodbye");
						exitStatus = true;
						loggedIn = false;
						break;
					
					// Timed out due to inactivity
					case "TIMEOUT":
						System.out.println("You timed out due to inactivity, please log back in again");
						loggedIn = false;
						Thread.sleep(10);
						break;
						
					// Server message including login/logout and error notifications
					case "SERVER":
						System.out.println(payload);
						break;
					}
					
					// If the user logs out, close all private connections
					if (!loggedIn) {
						peerConnections.closeConnections();
						Thread.sleep(100);
					}
					
					// If the user exits, then close all streams and sockets
					if (exitStatus) {
						scn.close();
						in.close();
						out.close();
						s.close();
						welcomeSocket.close();
						break;
					}
					syncLock.unlock();
					
				} catch (Exception e) {
					e.printStackTrace();
				}
			}		
	}
	
	// Thread for sending packets
	@Override
	public void run() {

		Packet packetOut;
		String command = null;
		String target = null;
		String message = null;
		
		while (true) {
			
			String tosend = scn.nextLine();
			String[] tokens = tosend.split(" ");
			syncLock.lock();
			
			try {
				// If the user is not logged in, they can only perform a login
				// sequence, they do not have access to other commands
				if (!loggedIn) {
					System.out.println("Please enter your password:");
					username = tosend;
					String password = scn.nextLine();
					out.writeObject(new Packet("LOGIN", username + " " + password));
				} else {
					// Get the command
					command = tokens[0];
					if (tokens.length >= 2) target = tokens[1];
					if (!checkValidFormat(tokens)) {
						syncLock.unlock();
						continue;
					}
					
					switch(command) {
					
					// Message another user
					case "message":		
						packetOut = new Packet("MESSAGE", null);
						// Set the destination user and remove the 'message user' part of string
						packetOut.setDest(target);
						message = tosend.replaceFirst(command + " " + target + " ", "");
						
						packetOut.setPayload(message);
						out.writeObject(packetOut);
						break;
	
					// Broadcast a message
					case "broadcast":
						message = tosend.replaceFirst("broadcast ", "");
						packetOut = new Packet ("BROADCAST", message);
						out.writeObject(packetOut);
						break;
						
					// whoelse command
					case "whoelse":
						packetOut = new Packet("WHOELSE", null);
						out.writeObject(packetOut);
						break;
					
					// whoelsesince command
					case "whoelsesince":
						long diff = Long.parseLong(target);
						packetOut = new Packet("WHOELSESINCE", Long.toString(diff));
						out.writeObject(packetOut);
						break;
					
					// Block a user
					case "block":
						packetOut = new Packet("BLOCK", target);
						out.writeObject(packetOut);
						if (peerConnections.isConnectedTo(target)) {
							peerConnections.sendMessage(target, "stopprivate");
						}
						break;
					
					// Unblock a user
					case "unblock":
						packetOut = new Packet("UNBLOCK", target);
						out.writeObject(packetOut);
						break;
					
					// Send request for starting a private connection with another user
					case "startprivate":
						if (target.equals(username)) {
							System.out.println("Error: Cannot start private messaging self");
							break;
						}
						packetOut = new Packet("STARTPRIVATE", target);
						out.writeObject(packetOut);
						break;
					
					// Private message a user
					case "private":
						if (target.equals(username)) {
							System.out.println("Error: Cannot private message self");
							break;
						}
						message = tosend.replaceFirst(command + " " + target + " ", "");
						peerConnections.sendMessage(target, message);
						break;
					
					// Close a private connection with a user
					case "stopprivate":
						if (target.equals(username)) {
							System.out.println("Error: Cannot start private messaging self");
						} else if (peerConnections.isConnectedTo(target)) {
							peerConnections.sendMessage(target, "stopprivate");
						} else {
							System.out.println("Error: No private connection with " + target + " yet");
						}
						break;			
										
					// Logout of the system, but not close the program
					case "logout":
						System.out.println("Logging out...");
						packetOut = new Packet("LOGOUT", null);
						out.writeObject(packetOut);
						loggedIn = false;
						break;
						
					// Start the sequence to exit the program
					case "exit":
						System.out.println("Exiting the system...");
						System.out.println("Logging out...");
						// Send exit to server to close their end
						packetOut = new Packet("EXIT", null);
						out.writeObject(packetOut);
						syncLock.unlock();
						Thread.sleep(50);
						exitStatus = true;
						loggedIn = false;
						break;
					
					default:
						System.out.println("Error: Invalid command");
						break;
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
			
			// If exit is called, stop running 
			// exit has it's own unlock, so don't want to call it twice
			if (exitStatus) break;
			else syncLock.unlock();
		}


	}
	
	/**
	 * Check if the given command is in the right format
	 * @param tokens: command split into words
	 * @return true if the command is the right format
	 */
	public boolean checkValidFormat(String[] tokens) {
		String command = tokens[0];
		int length = tokens.length;
		boolean valid;
		
		List<String> commands = Arrays.asList("whoelse", "logout", "exit", 
												"whoelsesince", "block", "unblock", 
												"startprivate", "stopprivate",
												"message", "private", "broadcast");
		List<String> format = Arrays.asList("whoelse", "logout", "exit", 
											"whoelsesince <seconds>", 
											"block <user>",
											"unblock <user>",
											"startprivate <user>",
											"stopprivate <user>",
											"message <user> <message>",
											"private <user> <message>",
											"broadcast <message>");
		
		int index = commands.indexOf(command);
		
		if (index == -1) {
			System.out.println("Error: Invalid command");
			return false;
		} else if (index <= 2) {
			valid = (length == 1);
		} else if (index <= 7) {
			valid = (length == 2);
		} else if (index <= 9) {
			valid = (length >= 3);
		} else {
			valid = (length >= 2);
		}
		if (!valid) {
			System.out.println("Error: Invalid use of " + command + ": " + format.get(index));
			return false;
		} else {
			return true;
		}
 	}
}