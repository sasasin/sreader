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
package net.sasasin.sreader.commons.dao;

import net.sasasin.sreader.commons.entity.FeedUrl;
import net.sasasin.sreader.commons.entity.Subscriber;

/**
 * @author sasasin
 * 
 */
public interface SubscriberDao extends GenericDao<Subscriber, String> {

	/**
	 * FeedUrlと紐付くSubscriberのうち、ログイン情報がNULLではないデータから、任意の一件を取得する。
	 * 
	 * @param f
	 * @return
	 */
	public Subscriber getByFeedUrl(FeedUrl f);

}
