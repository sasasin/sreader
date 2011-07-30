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
package net.sasasin.sreader.publish;

import java.sql.Clob;
import java.sql.SQLException;
import java.util.List;

import org.hibernate.Transaction;
import org.hibernate.Session;

import net.sasasin.sreader.orm.ContentView;
import net.sasasin.sreader.orm.ContentViewId;
import net.sasasin.sreader.orm.PublishLog;
import net.sasasin.sreader.util.DbUtil;

public abstract class AbstractPublisher {

	public void run() {
		List<ContentView> contents = getContent();

		init();
		for (ContentView content : contents) {
			publish(content.getId());
		}
		finalize();
	}

	public void init() {
	}

	public void finalize() {
	}

	public void publish(ContentViewId content) {
	}

	public List<ContentView> getContent() {
		Session ses = DbUtil.getSessionFactory().openSession();
		Transaction tx = ses.beginTransaction();

		@SuppressWarnings("unchecked")
		List<ContentView> l = (List<ContentView>) ses.createCriteria(
				ContentView.class).list();

		tx.commit();
		return l;

	}

	public void log(ContentViewId content) {
		Session ses = DbUtil.getSessionFactory().openSession();
		Transaction tx = ses.beginTransaction();

		PublishLog log = new PublishLog();
		log.setContentHeaderId(content.getId());

		ses.save(log);

		tx.commit();

	}

	public String clobToString(Clob clob) {
		try {
			return clob.getSubString(1, (int) clob.length());
		} catch (SQLException e) {
			e.printStackTrace();
			return "";
		}
	}

}
