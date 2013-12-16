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
package net.sasasin.sreader.dao.impl;

import java.util.List;

import org.hibernate.Session;

import net.sasasin.sreader.dao.FeedUrlDao;
import net.sasasin.sreader.orm.FeedUrl;

/**
 * @author sasasin
 * 
 */
public class FeedUrlDaoHibernateImpl extends
		GenericDaoHibernateImpl<FeedUrl, String> implements FeedUrlDao {

	@Override
	public List<FeedUrl> findIfExistsSubscriber() {

		// 購読者が一人でも存在するFeedUrlのみを取得
		String sql = "select f.* from feed_url f"
				+ " where exists (select 1 from subscriber s where s.feed_url_id = f.id)";

		Session ses = getSessionFactory().openSession();

		@SuppressWarnings("unchecked")
		List<FeedUrl> fs = (List<FeedUrl>) ses.createSQLQuery(sql)
				.addEntity(FeedUrl.class).list();

		// LAZYなので
		for (FeedUrl f : fs) {
			f.getId();
		}

		return fs;
	}

}
