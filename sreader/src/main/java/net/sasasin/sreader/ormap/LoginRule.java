/*
 * SReader is RSS/Atom feed reader with full text.
 *
 * Copyright (C) 2011, Shinnosuke Suzuki <sasasin@sasasin.net>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as 
 * published by the Free Software Foundation, either version 3 of
 * the License, or any later version.
 *	
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program.
 * If not, see <http://www.gnu.org/licenses/>.
 */
package net.sasasin.sreader.ormap;

public class LoginRule {
	private String hostName;

	private String postUrl;

	private String idBoxName;

	private String passwordBoxName;

	@SuppressWarnings("unused")
	private LoginRule(){
		
	}

	public LoginRule(String hostName, String postUrl, String idBoxName, String passWordBoxName){
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
