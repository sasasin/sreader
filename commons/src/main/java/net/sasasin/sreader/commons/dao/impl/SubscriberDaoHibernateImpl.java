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

import org.hibernate.Session;
import org.hibernate.criterion.Restrictions;

import net.sasasin.sreader.commons.dao.SubscriberDao;
import net.sasasin.sreader.commons.entity.FeedUrl;
import net.sasasin.sreader.commons.entity.Subscriber;

/**
 * @author sasasin
 * 
 */
public class SubscriberDaoHibernateImpl extends
		GenericDaoHibernateImpl<Subscriber, String> implements SubscriberDao {

	@Override
	public Subscriber getByFeedUrl(FeedUrl f) {
		Session ses = getSessionFactory().openSession();

		// TODO 「有効なアカウントの」という条件を付与する。
		// TODO 「有効なアカウント」をチェックする仕組みを作る。
		Subscriber sub = (Subscriber) ses.createCriteria(Subscriber.class)
				.add(Restrictions.eq("feedUrl", f))
				.add(Restrictions.isNotNull("authName"))
				.add(Restrictions.isNotNull("authPassword")).setMaxResults(1)
				.uniqueResult();
		return sub;
	}

}
