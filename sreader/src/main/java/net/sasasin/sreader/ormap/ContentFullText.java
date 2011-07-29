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
public class ContentFullText {
	private String id;

	private String fullText;

	private String contentHeaderId;

	@SuppressWarnings("unused")
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
