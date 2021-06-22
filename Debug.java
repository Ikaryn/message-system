public class Debug {
	
	public boolean on;
	
	/**
	 * For printing debugging information
	 */
	public Debug () {
		this.on = false;
	}
	
	public void set(boolean setting) {
		this.on = setting;
	}
	
	public void print(String s) {
		if (on) System.out.println(s);
	}
	
}