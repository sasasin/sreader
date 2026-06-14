INSERT INTO login_rules(host_name, post_url, id_box_name, password_box_name, submit_button_name)
VALUES('example.com', 'https://example.com/login', 'username', 'password', 'submit');

INSERT INTO login_rules(host_name, post_url, id_box_name, password_box_name, submit_button_name)
VALUES('news.example.test', 'https://news.example.test/session', 'login', 'password', 'login-button');

INSERT INTO account(id, email, password)
VALUES('1', 'reader@example.test', 'development-password');
