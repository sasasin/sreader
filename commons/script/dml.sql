/*
 * SReader is RSS/Atom feed reader with full text.
 *
 * Copyright (C) 2011-2013, Shinnosuke Suzuki <sasasin@sasasin.net>
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

-- ログインルールのサンプル。
delete from login_rules;

insert into login_rules(host_name, post_url, id_box_name, password_box_name, submit_button_name) 
values('jp.wsj.com', 'https://id.wsj.com/auth/log-in', 'loginUserOrEmail', 'password', 'loginSubmit');

insert into login_rules(host_name, post_url, id_box_name, password_box_name, submit_button_name)
values('jbpress.ismedia.jp', 'https://jbpress.ismedia.jp/auth/dologin', 'login', 'password','login-btn');

commit;
