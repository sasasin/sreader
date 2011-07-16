/**
 * 
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
