/**
 * 
 */
package net.sasasin.sreader.ormap;

import net.sasasin.sreader.util.Md5Util;

/**
 * @author sasasin
 * 
 */
public class ContentFullText {
	private String id;

	private String fullText;

	private String contentHeaderId;

	private ContentFullText() {
	}

	public ContentFullText(String text, String contentHeaderId) {
		setId(text);
		setFullText(text);
		setContentHeaderId(contentHeaderId);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ContentFullText other = (ContentFullText) obj;
		if (id == null) {
			if (other.id != null)
				return false;
		} else if (!id.equals(other.id))
			return false;
		return true;
	}

	public String getContentHeaderId() {
		return contentHeaderId;
	}

	public String getFullText() {
		return fullText;
	}

	public String getId() {
		return id;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((id == null) ? 0 : id.hashCode());
		return result;
	}

	public void setContentHeaderId(String contentHeaderId) {
		this.contentHeaderId = contentHeaderId;
	}

	public void setFullText(String fullText) {
		this.fullText = fullText;
	}

	public void setId(String id) {
		this.id = Md5Util.crypt(id);
	}

}
