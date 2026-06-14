CREATE TABLE account(
       id char(32) primary key,
       email varchar(1024) not null,
       password varchar(1024) not null
);

CREATE TABLE feed_url(
       id char(32) primary key,
       url text not null
);

CREATE TABLE subscriber(
       id char(32) primary key,
       account_id char(32) not null,
       feed_url_id char(32) not null,
       subscribe_date date,
       auth_name varchar(1024),
       auth_password varchar(1024),
       auth_check_date date,
       CONSTRAINT subscriber_fkey01 FOREIGN KEY (account_id) REFERENCES account(id) ON DELETE CASCADE,
       CONSTRAINT subscriber_fkey02 FOREIGN KEY (feed_url_id) REFERENCES feed_url(id) ON DELETE CASCADE
);

CREATE TABLE content_header(
       id char(32) primary key,
       url text not null,
       title varchar(1024),
       feed_url_id char(32) not null,
       CONSTRAINT content_header_fkey01 FOREIGN KEY (feed_url_id) REFERENCES feed_url(id) ON DELETE CASCADE
);

CREATE TABLE content_full_text(
       id char(32) primary key,
       full_text text,
       content_header_id char(32) not null,
       CONSTRAINT content_full_text_fkey01 FOREIGN KEY (content_header_id) REFERENCES content_header(id) ON DELETE CASCADE
);

CREATE TABLE publish_log(
       id char(32) primary key,
       account_id char(32) not null,
       content_header_id char(32) not null,
       publish_date date not null
);

CREATE TABLE login_rules(
       host_name varchar(96) primary key,
       post_url text not null,
       id_box_name varchar(256) not null,
       password_box_name varchar(256) not null,
       submit_button_name varchar(256) not null
);

CREATE TABLE eft_rules(
       id char(32) primary key,
       url text not null,
       extract_rule text not null
);

CREATE OR REPLACE VIEW content_view AS
SELECT a.id AS account_id
     , a.email
     , a.password
     , ch.id AS content_header_id
     , ch.url
     , ch.title
     , cf.full_text
FROM account a
INNER JOIN subscriber sub
        ON a.id = sub.account_id
INNER JOIN content_header ch
        ON sub.feed_url_id = ch.feed_url_id
INNER JOIN content_full_text cf
        ON ch.id = cf.content_header_id
WHERE NOT EXISTS (
      SELECT 1
      FROM publish_log p
      WHERE p.account_id = a.id
        AND p.content_header_id = ch.id
);
