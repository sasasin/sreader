drop view content_view;

drop table account;

create table account(
	id char primary key,
	name char not null
);

-- 収集するRSSのURL一覧。
-- 全文取得にログインが必要なら、ログインIDとパスワードをここに、
-- ログインページについての情報はlogin_urlに入れておく。
drop table feed_url;

create table feed_url(
	id char primary key,
	url char not null,
	auth_name char,
	auth_password char,
	account_id char not null
);

/*
alter table feed_url
add constraint feed_url_fkey01
foreign key (account_id)
references account(id)
on delete cascade;
*/

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
drop table publish_log;

create table publish_log(
	content_header_id char not null
);

-- AbstractPublisher用の補助ビュー。
-- 未配信の記事のみ抽出。
create or replace view content_view as
select h.id
, h.url
, h.title
, f.full_text 
from content_header h
inner join content_full_text f
	on h.id = f.content_header_id
where h.id not in (
	select content_header_id
	from publish_log
);


-- ログインルールマスター。POSTメソッドによるログイン情報の渡し方を蓄積。
drop table login_rules;

create table login_rules(
	host_name char primary key,
	post_url char not null,
	id_box_name char not null,
	password_box_name char not null
);

-- 全文抽出ルール。ExtractContentで全文を切り出すルールを、URL毎に蓄積。
drop table eft_rules;

create table eft_rules(
	url char primary key,
	extract_rule char not null
);

-- GMailログイン情報。GMailPublisher用。
drop table gmail_login_info;

create table gmail_login_info(
	address char primary key,
	password char,
	account_id char not null
);

-- GMailログイン情報のサンプルデータ。
insert into gmail_login_info(address, password, account_id)
values('fugafuga@gmail.com', 'piyopiyo', 'hoge');

-- ログインルールのサンプル。
insert into login_rules(host_name, post_url, id_box_name, password_box_name) 
values('jp.wsj.com', 'http://jp.wsj.com/user/login', 'Login', 'Password');

insert into login_rules(host_name, post_url, id_box_name, password_box_name)
values('jbpress.ismedia.jp', 'https://jbpress.ismedia.jp/auth/dologin', 'login', 'password');

commit;

-- 全文抽出ルールの中身。
-- エスケープシーケンスに注意。SQLとJavaで通るよう調整する。
insert into eft_rules(url, extract_rule)
values('http://jp.wsj.com/', '<div class=\"articlePage\">(.*?)</div><!--article_story_body-->');

insert into eft_rules(url, extract_rule)
values('http://jp.wsj.com/japanrealtime/','<div class="articlePage">(.*?)<h3 class=\"byline\">');

insert into eft_rules(url, extract_rule)
values('http://www3.nhk.or.jp/news', '<p id=\"news_textbody\">(.*?)<!-- _ria_tail_ -->');

insert into eft_rules(url, extract_rule)
values('http://blog.livedoor.jp/minnanohimatubushi/archives', '<div class=\"article-body entry-content\">(.*?)<!-- articleBody End -->');

commit;

