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