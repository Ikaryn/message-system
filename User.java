import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

public class User {
	
	private String username;
	private String password;
	private boolean online;
	private boolean blocked;
	private LocalDateTime blockedTime;
	private List<String> blockedUsers;
	private int loginAttempts;
	private LocalDateTime lastLogin;
	private LocalDateTime lastLogout;
	private List<Packet> offlineMessages;
	
	/**
	 * Account to be used with a client in the server
	 * @param username: username of the user
	 * @param password: password of the user
	 */
	public User(String username, String password) {
		this.username = username;
		this.password = password;
		this.online = false;
		this.blocked = false;
		this.blockedUsers = new ArrayList<>();
		this.loginAttempts = 0;
		this.lastLogin = null;
		this.lastLogout = null;
		this.offlineMessages = new ArrayList<>();
	}
	
	/**
	 * Get the username of the user
	 */
	public String getUsername() {
		return username;
	}
	
	/**
	 * Get the current login attempts for the given account
	 * Used for checking if the account need to be locked
	 */
	public int getLoginAttempts() {
		return loginAttempts;
	}
	
	/**
	 * Change the status of the user as online
	 * Also set the lastLogin for whoelse and whoelsesince
	 */
	public void goOnline() {
		this.online = true;
		lastLogin = LocalDateTime.now();
	}
	
	/**
	 * Change the status of the user as offline
	 * Also set the lastLogout for whoelse and whoelsesince
	 */
	public void goOffline() {
		this.online = false;
		lastLogout = LocalDateTime.now();
	}
	
	/**
	 * Check if a user was online since a given time
	 */
	public boolean wasOnline(LocalDateTime time) {

		// User never logged in before
		if (lastLogin == null) return false;
		
		// The user is currently online
		if (lastLogout == null) return true;
		
		// Find the most recent loginTime that is before the time
		if (lastLogin.isBefore(time)) {
			if (lastLogout.isAfter(time)) return true;
			else return false;
		} else return true;

	}
	
	/**
	 * Check if a user is logged in/online
	 */
	public boolean isOnline() {
		return online;
	}
	
	/**
	 * Reset the number of login attempts for the account
	 */
	public void resetAttempts() {
		loginAttempts = 0;
	}
	
	/**
	 * Password check
	 * @param attempt: string to compared to the password
	 * @return true if the attempt is correct
	 */
	public boolean checkPassword(String attempt) {
		loginAttempts++;
		return (attempt.equals(password));
	}
	
	/**
	 * Lock the account from being successfully logged in to
	 * for block_duration seconds
	 */
	public void lockOut() {
		blocked = true;
		blockedTime = LocalDateTime.now();
	}
	
	/**
	 * Block another user from sending message to this user,
	 * starting private messages and seeing login/logout alerts
	 * @param user: user to be blocked
	 */
	public void blockUser(String user) {
		blockedUsers.add(user);
	}
	
	/**
	 * Unblock another user
	 * @param user: user to be unblocked
	 */
	public void unblockUser(String user) {
		if (blockedUsers.contains(user)) blockedUsers.remove(user);
	}
	
	/**
	 * Check if this user has blocked another user
	 * @param user: person to check if they have been blocked
	 * @return true if the user is blocked
	 */
	public boolean hasBlocked(String user) {
		return (blockedUsers.contains(user));
	}
	
	/**
	 * Unlock this account so it can be logged in to again
	 */
	public void unlock() {
		this.blocked = false;
		this.blockedTime = null;
		this.loginAttempts = 0;
	}
	
	/**
	 * Check if this account is locked out of due to 3 consecutive failed attempts
	 * @param block_duration: amount of time the account is to be locked for
	 * @return true if the account is locked
	 */
	public boolean isLockedOut(long block_duration) {
		if (blocked) {
			long diff = blockedTime.until(LocalDateTime.now(), ChronoUnit.SECONDS);
			if (diff >= block_duration) {
				this.unlock();
				return false;
			} else {
				return true;
			}
		}
		return false;
	}
	
	/**
	 * Add a message to the offline messages list
	 * @param packet: message to be sent to the user when they go online
	 */
	public void addMessage (Packet packet) {
		offlineMessages.add(packet);
	}
	
	/**
	 * Get the list of offline messages
	 */
	public List<Packet> getMessages () {
		return offlineMessages;
	}
	
	/**
	 * Clear the list of offline messages
	 */
	public void clearMessges () {
		offlineMessages.clear();
	}
	
}