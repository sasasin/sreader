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

import java.util.Date;
import java.util.List;

import net.sasasin.sreader.dao.ContentViewDao;
import net.sasasin.sreader.dao.PublishLogDao;
import net.sasasin.sreader.dao.impl.ContentViewDaoHibernateImpl;
import net.sasasin.sreader.dao.impl.PublishLogDaoHibernateImpl;
import net.sasasin.sreader.orm.ContentView;
import net.sasasin.sreader.orm.ContentViewId;
import net.sasasin.sreader.orm.PublishLog;
import net.sasasin.sreader.util.Md5Util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractPublisher {

	private static Logger logger = LoggerFactory.getLogger("net.sasasin.sreader");
	
	private ContentViewDao contentViewDao = new ContentViewDaoHibernateImpl();
	private PublishLogDao publishLogDao = new PublishLogDaoHibernateImpl();
	
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
		
		List<ContentView> l = contentViewDao.findAll();

		return l;

	}

	public void log(ContentViewId content) {
		
		PublishLog log = new PublishLog();

		log.setId(Md5Util.crypt(content.getAccountId()
				+ content.getContentHeaderId()));
		log.setAccountId(content.getAccountId());
		log.setContentHeaderId(content.getContentHeaderId());
		log.setPublishDate(new Date());

		publishLogDao.save(log);

	}

	public Logger getLogger(){
		return logger;
	}
}
