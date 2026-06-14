-- Legacy reference only.
-- The modern Docker Compose/Flyway setup does not use this file. Gmail delivery
-- was removed, and SReader must not store Gmail account passwords.
-- GMailログイン情報のサンプルデータ。
delete from account;

insert into account(id, email, password)
values('1', 'fugafuga@gmail.com', 'piyopiyo');

commit;
