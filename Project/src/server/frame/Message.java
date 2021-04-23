package server.frame;

public class Message implements Frame {
	private String login;
	private String str;
	
	public void setLogin(String login) {
		this.login = login;
	}
	
	public void setStr(String str) {
		this.str = str;
	}
	
	public String getLogin() {
		return this.login;
	}
	
	public String getStr() {
		return this.str;
	}
	
}
