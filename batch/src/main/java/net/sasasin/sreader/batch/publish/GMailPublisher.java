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
package net.sasasin.sreader.batch.publish;

import net.sasasin.sreader.commons.entity.ContentViewId;

import org.apache.commons.mail.DefaultAuthenticator;
import org.apache.commons.mail.Email;
import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.SimpleEmail;

/**
 * @author sasasin
 * 
 */
public class GMailPublisher extends AbstractPublisher {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		new GMailPublisher().run();
	}

	@Override
	public void publish(ContentViewId content) {
		try {
			Email email = new SimpleEmail();
			email.setHostName("smtp.gmail.com");
			email.setSmtpPort(587);
			email.setStartTLSEnabled(true);
			email.setCharset("UTF-8");

			email.setAuthenticator(new DefaultAuthenticator(content.getEmail(),
					content.getPassword()));

			email.setFrom(content.getEmail());
			email.addTo(content.getEmail());
			email.setSubject(content.getTitle());
			email.setMsg(content.getUrl() + "\n" + content.getFullText());

			email.send();
			log(content);
		} catch (EmailException e) {
			e.printStackTrace();
		}
	}

}
