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

import net.sasasin.sreader.orm.ContentViewId;
import net.sasasin.sreader.orm.GmailLoginInfo;
import net.sasasin.sreader.util.DbUtil;

import org.apache.commons.mail.DefaultAuthenticator;
import org.apache.commons.mail.Email;
import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.SimpleEmail;
import org.hibernate.Session;
import org.hibernate.Transaction;

/**
 * @author sasasin
 * 
 */
public class GMailPublisher extends AbstractPublisher {

	private Email email = new SimpleEmail();

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		new GMailPublisher().run();
	}

	public void init() {
		GmailLoginInfo m = getLoginInfo();
		email.setHostName("smtp.gmail.com");
		email.setSmtpPort(587);
		email.setAuthenticator(new DefaultAuthenticator(m.getAddress(), m
				.getPassword()));
		email.setTLS(true);
		email.setCharset("UTF-8");
		try {
			email.setFrom(m.getAddress());
			email.addTo(m.getAddress());
		} catch (EmailException e) {
			e.printStackTrace();
		}
	}

	public void publish(ContentViewId content) {
		try {
			email.setSubject(content.getTitle());
			email.setMsg(content.getUrl() + "\n"
					+ clobToString(content.getFullText()));
			email.send();
			log(content);
		} catch (EmailException e) {
			e.printStackTrace();
		}
	}

	private GmailLoginInfo getLoginInfo() {
		Session ses = DbUtil.getSessionFactory().openSession();
		Transaction tx = ses.beginTransaction();
		GmailLoginInfo gli = (GmailLoginInfo) ses.createCriteria(
				GmailLoginInfo.class).uniqueResult();
		tx.commit();
		return gli;
	}

}
