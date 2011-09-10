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

drop view content_view;

--アカウント。
drop table account;

create table account(
       id char primary key,
       email char not null,
       password char not null       
);


-- 収集するRSSのURL一覧。
drop table feed_url;

create table feed_url(
	id char primary key,
	url char not null
);


-- RSSの購読者。必要ならログイン情報も。
-- 全文取得にログインが必要なら、ログインIDとパスワードをここに、
-- ログイン方法についての情報はlogin_urlに入れておく。
drop table subscriber;

create table subscriber(
       id char primary key,
       account_id char not null,
       feed_url_id char not null,
       subscribe_date date,
       auth_name char,
       auth_password char,
       auth_check_date date
);

alter table subscriber
add constraint subscriber_fkey01
foreign key (account_id)
references account(id)
on delete cascade;

alter table subscriber
add constraint subscriber_fkey02
foreign key (feed_url_id)
references feed_url(id)
on delete cascade;


-- RSSから取得した記事。記事の本文以外の情報を蓄積。
drop table content_header;

create table content_header(
	id char primary key,
	url char not null,
	title char,
	feed_url_id char not null
);

alter table content_header
add constraint content_header_fkey01
foreign key (feed_url_id)
references feed_url(id)
on delete cascade;

-- 記事本文を蓄積。
drop table content_full_text;

create table content_full_text(
       id char primary key,
       full_text clob,
       content_header_id char not null
);

alter table content_full_text
add constraint content_full_text_fkey01
foreign key (content_header_id)
references content_header(id)
on delete cascade;

-- 配信履歴。配信済みのcontent_headerのidを記録。
--drop table publish_log;

create table publish_log(
       id char primary key,
       account_id char not null,
       content_header_id char not null,
       publish_date date not null
);

-- AbstractPublisher用の補助ビュー。
-- 未配信の記事のみ抽出。
create or replace view content_view as
select a.id as account_id
, a.email
, a.password
, ch.id as content_header_id
, ch.url
, ch.title
, cf.full_text
from account a
inner join subscriber sub
      on a.id = sub.account_id
inner join content_header ch
      on sub.feed_url_id = ch.feed_url_id
inner join content_full_text cf
      on ch.id = cf.content_header_id
where not exists (
      select 1
      from publish_log p
      where p.account_id = a.id
      and p.content_header_id = ch.id
);

-- ログインルール。POSTメソッドによるログイン情報の渡し方を蓄積。
drop table login_rules;

create table login_rules(
       host_name char primary key,
       post_url char not null,
       id_box_name char not null,
       password_box_name char not null
);

-- 全文抽出ルール。ExtractContentで全文を切り出すルールを蓄積。
drop table eft_rules;

create table eft_rules(
       id char primary key,
       url char not null,
       extract_rule char not null
);
