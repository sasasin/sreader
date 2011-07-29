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

import net.sasasin.sreader.util.Md5Util;

/**
 * @author sasasin
 * 
 */
public class ContentHeader {

	private String id;

	private String url;

	private String title;

	private String feedUrlId;

	@SuppressWarnings("unused")
	private ContentHeader() {

	}

	public ContentHeader(String url, String title, String feedUrlId) {
		setUrl(url);
		setTitle(title);
		setFeedUrlId(feedUrlId);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ContentHeader other = (ContentHeader) obj;
		if (id == null) {
			if (other.id != null)
				return false;
		} else if (!id.equals(other.id))
			return false;
		return true;
	}

	public String getFeedUrlId() {
		return feedUrlId;
	}

	public String getId() {
		return id;
	}

	public String getTitle() {
		return title;
	}

	public String getUrl() {
		return url;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((id == null) ? 0 : id.hashCode());
		return result;
	}

	private void setFeedUrlId(String feedUrlId) {
		this.feedUrlId = feedUrlId;
	}

	private void setId() {
		this.id = Md5Util.crypt(getUrl());
	}

	private void setTitle(String title) {
		this.title = title;
	}

	private void setUrl(String url) {
		this.url = url;
		setId();
	}

}
