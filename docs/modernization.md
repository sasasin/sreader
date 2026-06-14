# モダナイゼーション: Docker Compose + Flyway

## 目的

このマイルストーンでは、古い Java/Maven/MySQL プロジェクトを、Docker
だけで再現できる作業基盤へ移行しています。Maven、JDK、Java コマンド、
MySQL client コマンド、Flyway は、ホスト OS ではなく Docker コンテナ内で
実行する方針です。

今回の追加作業では、運用安全性を優先して Gmail 配信機能と外部サイトへの
認証付き取得機能を廃止しました。認証情報を暗号化する方向ではなく、DB と
codebase から Gmail password、購読先 login ID/password、`login_rules` を
扱う機能を削除しています。

## 現在の機能範囲

- 認証不要な公開 RSS/Atom feed URL の登録
- 公開 RSS/Atom feed から記事 URL とタイトルを取得
- 認証不要ページの本文抽出
- `feed_url`、`content_header`、`content_full_text`、`eft_rules` への保存

メール配信、SMTP relay、Gmail OAuth2、外部サイトログイン、secret manager
連携は現在の scope では実装しません。

## リポジトリ構成

- `commons` には、残存 schema に対応する entity、DAO、utility、legacy SQL
  script、および test が含まれています。
- `batch` には、feed URL 登録、feed 取り込み、全文抽出を行う command-line
  job が含まれています。
- `db/migration` には、Docker Compose の Flyway service から実行する
  migration SQL を置きます。
- `docker/mysql/init/` は database/user 作成だけを担当します。
- `commons/script` 配下の元 SQL file は legacy reference です。現代化後の
  標準セットアップでは実行しません。

## Docker Compose

MySQL を起動します。

```sh
docker compose up -d mysql
```

`sreader` database に Flyway migration を適用します。

```sh
docker compose run --rm flyway info
docker compose run --rm flyway migrate
docker compose run --rm flyway info
```

`sreadertest` database に同じ Flyway migration を適用します。

```sh
docker compose run --rm flyway "-url=jdbc:mysql://mysql:3306/sreadertest?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=UTC" -user=sreadertest -password=sreadertest info
docker compose run --rm flyway "-url=jdbc:mysql://mysql:3306/sreadertest?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=UTC" -user=sreadertest -password=sreadertest migrate
docker compose run --rm flyway "-url=jdbc:mysql://mysql:3306/sreadertest?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=UTC" -user=sreadertest -password=sreadertest info
```

reactor 全体を build/test します。test は `sreadertest` database へ接続する
ため、事前に `sreadertest` へ Flyway migration を適用してください。

```sh
docker compose run --rm maven mvn clean verify
```

## MySQL 初期化

Docker Compose は `docker/mysql/init/` を MySQL container の
`/docker-entrypoint-initdb.d` に mount します。ここに置く SQL は MySQL
container の初回起動時、つまり `mysql-data` volume が空の時だけ実行されます。

`docker/mysql/init/00-create-databases-and-users.sql` の責務は以下だけです。

- `sreader` database の作成
- `sreadertest` database の作成
- 開発用 `sreader` user の作成
- テスト用 `sreadertest` user の作成
- それぞれの database への最小限の権限付与

この初期化 SQL には `CREATE TABLE`、`CREATE VIEW`、seed data の `INSERT`、
Gmail や購読先認証情報などの利用者データを含めません。

既存 Docker volume がある場合、MySQL entrypoint の init SQL は再実行されません。
schema 更新は Flyway migration を適用してください。DB/user 作成 SQL から
やり直す必要がある場合は、ローカル開発 DB を破棄する操作であることを理解
したうえで、次を実行してください。

```sh
docker compose down -v
docker compose up -d mysql
```

`docker compose down -v` は `mysql-data` volume を削除し、ローカルの開発 DB
データを失います。

## Flyway migration

Flyway は専用 Compose service として定義しています。DB migration を Maven
build と別責務にでき、CI や将来の自動化でも database ごとに同じ service を
再利用しやすいためです。image は `redgate/flyway:12.8.1` に固定しています。

- `V1__create_schema.sql` は legacy schema を作成します。
- `V2__insert_development_seed_data.sql` は V1 時点の dummy seed data を投入します。
- `V3__remove_email_delivery_and_authenticated_fetch.sql` は Gmail 配信と認証付き取得の
  廃止に伴い、`content_view`、`publish_log`、`subscriber`、`account`、
  `login_rules` を削除します。

既存 migration は master に入っているため checksum 不一致を避ける目的で
直接編集していません。fresh DB では V1/V2 の後に V3 が適用され、一時的に
古い table/seed が作られてから削除されます。将来、migration を squash するかは
別作業として判断してください。

期待する現行 schema は、`feed_url`、`content_header`、`content_full_text`、
`eft_rules` と Flyway 管理 table です。Gmail password、購読先 password、
`login_rules`、Gmail 配信用 `content_view`、メール送信済み管理専用
`publish_log` は残しません。

## Maven の変更

- root `pom.xml` は packaging `pom` として `commons` / `batch` module を定義します。
- 依存 version と plugin version は parent POM 側で固定しています。
- Java source/target は既存 codebase に合わせて `1.7` を維持しています。
- HtmlUnit は認証不要ページの本文抽出にも使うため残しています。
- `commons-email` は Gmail/SMTP 配信専用だったため削除しました。

## Legacy SQL

`commons/script/` 配下の SQL は現代化前の host-oriented setup 用 reference です。
標準手順では実行しません。

- `gmail.sql` は Gmail 配信廃止により使用しません。
- `dml.sql` に含まれる historical `login_rules` seed は廃止済み機能の記録です。
- `ddl.mysql.tables.sql` は historical schema reference であり、現行 schema の定義元ではありません。
- `ddl.mysql.users.sql` をホストの `mysql` command で直接実行する旧手順は標準手順ではありません。

## 検証

代表的な検証 command は次の通りです。

```sh
docker compose config
docker compose down -v
docker compose up -d mysql
docker compose run --rm flyway info
docker compose run --rm flyway migrate
docker compose run --rm flyway info
docker compose exec mysql mysql -uroot -psreaderroot -e "SHOW DATABASES;"
docker compose exec mysql mysql -uroot -psreaderroot -e "SELECT table_name FROM information_schema.tables WHERE table_schema = 'sreader' ORDER BY table_name;"
docker compose exec mysql mysql -uroot -psreaderroot -e "SELECT table_name, column_name FROM information_schema.columns WHERE table_schema = 'sreader' AND (column_name LIKE '%password%' OR column_name LIKE 'auth_%' OR table_name = 'login_rules') ORDER BY table_name, column_name;"
docker compose run --rm flyway "-url=jdbc:mysql://mysql:3306/sreadertest?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=UTC" -user=sreadertest -password=sreadertest info
docker compose run --rm flyway "-url=jdbc:mysql://mysql:3306/sreadertest?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=UTC" -user=sreadertest -password=sreadertest migrate
docker compose exec mysql mysql -uroot -psreaderroot -e "SELECT table_name, column_name FROM information_schema.columns WHERE table_schema = 'sreadertest' AND (column_name LIKE '%password%' OR column_name LIKE 'auth_%' OR table_name = 'login_rules') ORDER BY table_name, column_name;"
docker compose run --rm maven mvn clean verify
```

## 残課題

- Hibernate 4 から modern Hibernate への移行
- Java language level の引き上げ
- Spring Boot 化
- DB schema migration の将来的な squash
- legacy な host-oriented shell script の Docker Compose wrapper 化または削除
- 本番運用設計
