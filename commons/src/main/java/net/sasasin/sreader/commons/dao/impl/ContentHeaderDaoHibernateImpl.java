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
package net.sasasin.sreader.commons.dao.impl;

import java.util.List;

import net.sasasin.sreader.commons.dao.ContentHeaderDao;
import net.sasasin.sreader.commons.entity.ContentHeader;

import org.hibernate.Session;

public class ContentHeaderDaoHibernateImpl extends
		GenericDaoHibernateImpl<ContentHeader, String> implements
		ContentHeaderDao {

	@Override
	public List<ContentHeader> findByConditionOfFullTextNotFetched() {

		Session ses = getSessionFactory().openSession();

		// 本文未取得で、未配信のもの
		// TODO これでは、誰か一人でも配信されてたら、金輪際配信されなくなる
		// TODO 正しくは、誰か一人でも配信されていなければ、取得を試みるようにしないと
		String queryString = "select h.*"
				+ " from content_header h left outer join content_full_text f"
				+ " on h.id = f.content_header_id"
				+ " inner join feed_url fu"
				+ " on h.feed_url_id = fu.id"
				+ " where f.id is null"
				+ " and h.id not in (select content_header_id from publish_log)";

		@SuppressWarnings("unchecked")
		List<ContentHeader> listOfContentHeader = (List<ContentHeader>) ses
				.createSQLQuery(queryString).addEntity(ContentHeader.class)
				.list();
		// LAZYなので
		for (ContentHeader h : listOfContentHeader) {
			h.getFeedUrl();
		}
		return listOfContentHeader;
	}

}
