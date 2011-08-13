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
package net.sasasin.sreader;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Date;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.hibernate.Session;
import org.hibernate.Transaction;

import net.sasasin.sreader.orm.Account;
import net.sasasin.sreader.orm.FeedUrl;
import net.sasasin.sreader.orm.Subscriber;
import net.sasasin.sreader.util.DbUtil;
import net.sasasin.sreader.util.Md5Util;

public class SingleAccountFeedReader {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		new SingleAccountFeedReader().run(args);
	}

	public void run(String[] args) {

		File path = new File(System.getProperty("user.home")
				+ File.separatorChar + "sreader.txt");

		if (!path.exists() || !path.isFile() || !path.canRead()) {
			System.out.println("FAIL;" + path.getPath() + " can not proc.");
			return;
		}

		// import path to feed_url table.
		importSubscribe(path);

	}

	private void importSubscribe(File path) {
		Session ses = DbUtil.getSessionFactory().openSession();
		Transaction tx = ses.beginTransaction();

		Account a = (Account) ses.createCriteria(Account.class)
				.setMaxResults(1).uniqueResult();
		if (a == null) {
			return;
		}

		List<String> lines = null;
		try {
			lines = IOUtils.readLines(new FileReader(path));
		} catch (IOException e) {
			e.printStackTrace();
		}

		for (String line : lines) {
			String[] str = line.split("\t");

			FeedUrl f = (FeedUrl) ses.get(FeedUrl.class, Md5Util.crypt(str[0]));
			if (f == null) {
				f = new FeedUrl();
				f.setId(Md5Util.crypt(str[0]));
				f.setUrl(str[0]);
				ses.save(f);
			}

			Subscriber s = (Subscriber) ses.get(Subscriber.class,
					Md5Util.crypt(a.getId() + f.getId()));
			if (s == null) {
				s = new Subscriber();
				s.setId(Md5Util.crypt(a.getId() + f.getId()));
				s.setAccount(a);
				s.setFeedUrl(f);
				s.setSubscribeDate(new Date());
			}
			if (str.length == 3) {
				s.setAuthName(str[1]);
				s.setAuthPassword(str[2]);
				s.setAuthCheckDate(new Date());
			}
			ses.save(s);

		}

		tx.commit();
	}
}
