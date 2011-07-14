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
	private String feedUrlId;

	private ContentHeader(){
		
	}
	public ContentHeader(String url, String feed_url_id){
		setUrl(url);
		setFeedUrlId(feed_url_id);
	}
	
	public String getUrl() {
		return url;
	}

	private void setUrl(String url) {
		this.url = url;
		setId();
	}

	public String getFeedUrlId() {
		return feedUrlId;
	}

	private void setFeedUrlId(String feedUrlId) {
		this.feedUrlId = feedUrlId;
	}

	public String getId() {
		return id;
	}

	private void setId() {
		this.id = Md5Util.crypt(getUrl());
	}


}
