CREATE DATABASE IF NOT EXISTS sreader CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS sreadertest CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE USER IF NOT EXISTS 'sreader'@'%' IDENTIFIED BY 'sreader';
CREATE USER IF NOT EXISTS 'sreadertest'@'%' IDENTIFIED BY 'sreadertest';

GRANT ALL PRIVILEGES ON sreader.* TO 'sreader'@'%';
GRANT ALL PRIVILEGES ON sreadertest.* TO 'sreadertest'@'%';
GRANT ALL PRIVILEGES ON sreadertest.* TO 'sreader'@'%';

USE sreader;

DROP VIEW IF EXISTS content_view;
DROP TABLE IF EXISTS eft_rules;
DROP TABLE IF EXISTS login_rules;
DROP TABLE IF EXISTS publish_log;
DROP TABLE IF EXISTS content_full_text;
DROP TABLE IF EXISTS content_header;
DROP TABLE IF EXISTS subscriber;
DROP TABLE IF EXISTS feed_url;
DROP TABLE IF EXISTS account;

CREATE TABLE account(
       id char(32) primary key,
       email varchar(1024) not null,
       password varchar(1024) not null
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE feed_url(
       id char(32) primary key,
       url text not null
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE content_header(
       id char(32) primary key,
       url text not null,
       title varchar(1024),
       feed_url_id char(32) not null,
       CONSTRAINT content_header_fkey01 FOREIGN KEY (feed_url_id) REFERENCES feed_url(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE content_full_text(
       id char(32) primary key,
       full_text longtext,
       content_header_id char(32) not null,
       CONSTRAINT content_full_text_fkey01 FOREIGN KEY (content_header_id) REFERENCES content_header(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE publish_log(
       id char(32) primary key,
       account_id char(32) not null,
       content_header_id char(32) not null,
       publish_date date not null
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

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

CREATE TABLE login_rules(
       host_name varchar(96) primary key,
       post_url text not null,
       id_box_name varchar(256) not null,
       password_box_name varchar(256) not null,
       submit_button_name varchar(256) not null
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE eft_rules(
       id char(32) primary key,
       url text not null,
       extract_rule text not null
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO login_rules(host_name, post_url, id_box_name, password_box_name, submit_button_name)
VALUES('jp.wsj.com', 'https://id.wsj.com/auth/log-in', 'loginUserOrEmail', 'password', 'loginSubmit');

INSERT INTO login_rules(host_name, post_url, id_box_name, password_box_name, submit_button_name)
VALUES('jbpress.ismedia.jp', 'https://jbpress.ismedia.jp/auth/dologin', 'login', 'password', 'login-btn');

INSERT INTO account(id, email, password)
VALUES('1', 'example@example.com', 'example-password');

USE sreadertest;

CREATE TABLE account LIKE sreader.account;
CREATE TABLE feed_url LIKE sreader.feed_url;
CREATE TABLE subscriber LIKE sreader.subscriber;
CREATE TABLE content_header LIKE sreader.content_header;
CREATE TABLE content_full_text LIKE sreader.content_full_text;
CREATE TABLE publish_log LIKE sreader.publish_log;
CREATE TABLE login_rules LIKE sreader.login_rules;
CREATE TABLE eft_rules LIKE sreader.eft_rules;
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

INSERT INTO login_rules SELECT * FROM sreader.login_rules;
INSERT INTO account SELECT * FROM sreader.account;
