package fr.upem.net.tcp.nonblocking;

public class Message {
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
