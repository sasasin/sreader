package net.sasasin.sreader.commons.util;

import java.net.URL;

import net.sasasin.sreader.commons.entity.LoginRules;

public interface Wget {

	public void setUrl(URL url);

	public URL getUrl();

	public LoginRules getLoginInfo();

	public void setLoginInfo(LoginRules loginInfo);

	public String getLoginId();

	public void setLoginId(String loginId);

	public String getLoginPassword();

	public void setLoginPassword(String loginPassword);

	public String read();

	public URL getOriginalUrl();

}