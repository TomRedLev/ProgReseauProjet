package client.frame;

public class Request implements Frame {
	private String login_requester;
	private String login_target;
	
	public void setLoginRequester(String login) {
		this.login_requester = login;
	}
	
	public void setLoginTarget(String login) {
		this.login_target = login;
	}
	
	public String getLoginRequester() {
		return this.login_requester;
	}
	
	public String getLoginTarget() {
		return this.login_target;
	}
	
}
