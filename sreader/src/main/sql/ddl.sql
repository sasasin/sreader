drop view content_view;

drop table account;

create table account(
	id char primary key,
	name char not null
);

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

drop table login_url;

create table login_url(
	host_name char primary key,
	post_url char not null,
	id_box_name char not null,
	password_box_name char not null
);

create or replace view content_view as
select h.id, h.url,h.title,f.full_text 
from content_header h
inner join content_full_text f
on h.id = f.content_header_id;

-- login_url sample data
insert into login_url(host_name, post_url, id_box_name, password_box_name) 
values('jp.wsj.com', 'http://jp.wsj.com/user/login', 'Login', 'Password');

insert into login_url(host_name, post_url, id_box_name, password_box_name)
values('jbpress.ismedia.jp', 'https://jbpress.ismedia.jp/auth/dologin', 'login', 'password');

commit;