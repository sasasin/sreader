package net.sasasin.sreader.ormap;

public class LoginUrl {
	private String hostName;

	private String postUrl;

	private String idBoxName;

	private String passwordBoxName;

	@SuppressWarnings("unused")
	private LoginUrl(){
		
	}

	public LoginUrl(String hostName, String postUrl, String idBoxName, String passWordBoxName){
		setHostName(hostName);
		setPostUrl(postUrl);
		setIdBoxName(idBoxName);
		setPasswordBoxName(passWordBoxName);
	}

	public String getHostName() {
		return hostName;
	}

	public String getIdBoxName() {
		return idBoxName;
	}

	public String getPasswordBoxName() {
		return passwordBoxName;
	}
	public String getPostUrl() {
		return postUrl;
	}
	private void setHostName(String hostName) {
		this.hostName = hostName;
	}
	private void setIdBoxName(String idBoxName) {
		this.idBoxName = idBoxName;
	}
	
	private void setPasswordBoxName(String passwordBoxName) {
		this.passwordBoxName = passwordBoxName;
	}
	
	private void setPostUrl(String postUrl) {
		this.postUrl = postUrl;
	}
}
