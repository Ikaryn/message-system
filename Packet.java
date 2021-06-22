import java.io.Serializable;

public class Packet implements Serializable {
	
	private static final long serialVersionUID = -6212610998539622147L;
	String type;
	String payload;
	String dest;
	String sender;
	
	/**
	 * Object that is sent between the server and client
	 * @param type: type of message for the parties to take action on
	 * @param payload: message body
	 */
	public Packet (String type, String payload) {
		this.type = type;
		this.payload = payload;
		this.dest = null;
		this.sender = null;
	}
	
	/**
	 * Get the packet message type
	 */
	public String getType() {
		return type;
	}
	
	/**
	 * Get the packet destination username
	 */
	public String getDest() {
		return dest;
	}
	
	/**
	 * Get the packet sender username
	 */
	public String getSender() {
		return sender;
	}
	
	/**
	 * Get the packet message body
	 */
	public String getPayload() {
		return payload;
	}
	
	/**
	 * Set the destination user
	 */
	public void setDest(String user) {
		this.dest = user;
	}
	
	/**
	 * Set the message sender
	 */
	public void setSender(String user) {
		this.sender = user;
	}
	
	/**
	 * Set the message body
	 */
	public void setPayload(String payload) {
		this.payload = payload;
	}
	
	

}