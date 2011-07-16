/**
 * 
 */
package net.sasasin.sreader.ormap;

import net.sasasin.sreader.util.Md5Util;

/**
 * @author sasasin
 * 
 */
public class FeedUrl {

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((id == null) ? 0 : id.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		FeedUrl other = (FeedUrl) obj;
		if (id == null) {
			if (other.id != null)
				return false;
		} else if (!id.equals(other.id))
			return false;
		return true;
	}

	private String id;
	private String url;
	private String authId;
	private String authPassword;
	private String accountId;

	private FeedUrl() {

	}

	public FeedUrl(String url, String auth_id, String auth_password,
			String accont_id) {
		this.setUrl(url);
		this.setAuthId(auth_id);
		this.setAuthPassword(auth_password);
	}

	public FeedUrl(String url) {
		this.setUrl(url);
	}

	public String getAuthId() {
		return authId;
	}

	public void setAuthId(String authId) {
		this.authId = authId;
	}

	public String getAuthPassword() {
		return authPassword;
	}

	public void setAuthPassword(String authPassword) {
		this.authPassword = authPassword;
	}

	public String getUrl() {
		return url;
	}

	private void setUrl(String url) {
		this.url = url;
		setId();
	}

	public String getId() {
		return id;
	}

	private void setId() {
		// idはURLから生成
		this.id = Md5Util.crypt(getUrl());
	}
}
