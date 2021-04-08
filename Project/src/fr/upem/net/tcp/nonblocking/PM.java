package fr.upem.net.tcp.nonblocking;

public class PM {
	private String login_sender;
	private String login_target;
	private String str;
	
	public void setLoginSender(String login) {
		this.login_sender = login;
	}
	
	public void setLoginTarget(String login) {
		this.login_target = login;
	}
	
	public void setStr(String str) {
		this.str = str;
	}
	
	public String getLoginSender() {
		return this.login_sender;
	}
	
	public String getLoginTarget() {
		return this.login_target;
	}
	
	public String getStr() {
		return this.str;
	}
	
}
