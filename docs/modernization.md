# モダナイゼーション: Docker Compose + Flyway + PostgreSQL

## 目的

このマイルストーンでは、古い Java/Maven/MySQL プロジェクトを、Docker
だけで再現できる作業基盤へ移行しています。Maven、JDK、Java コマンド、
PostgreSQL client コマンド、Flyway は、ホスト OS ではなく Docker コンテナ内で
実行する方針です。

標準 DB を MySQL から PostgreSQL 16.x に変更しました。Docker Compose の
標準 service 名は `postgres` です。MySQL 互換は維持しません。

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
- `docker/postgres/init/` は database/role 作成だけを担当します。
- `commons/script` 配下の元 SQL file は legacy reference です。現代化後の
  標準セットアップでは実行しません。

## Docker Compose

PostgreSQL を起動します。

```sh
docker compose up -d postgres
```

`sreader` database に Flyway migration を適用します。

```sh
docker compose run --rm flyway info
docker compose run --rm flyway migrate
docker compose run --rm flyway info
```

`sreadertest` database に同じ Flyway migration を適用します。

```sh
docker compose run --rm flyway -url=jdbc:postgresql://postgres:5432/sreadertest -user=sreadertest -password=sreadertest info
docker compose run --rm flyway -url=jdbc:postgresql://postgres:5432/sreadertest -user=sreadertest -password=sreadertest migrate
docker compose run --rm flyway -url=jdbc:postgresql://postgres:5432/sreadertest -user=sreadertest -password=sreadertest info
```

reactor 全体を build/test します。test は `sreadertest` database へ接続する
ため、事前に `sreadertest` へ Flyway migration を適用してください。

```sh
docker compose run --rm maven mvn clean verify
```

## PostgreSQL 初期化

Docker Compose は `docker/postgres/init/` を PostgreSQL container の
`/docker-entrypoint-initdb.d` に mount します。ここに置くスクリプトは
PostgreSQL container の初回起動時、つまり `postgres-data` volume が空の
時だけ実行されます。

`POSTGRES_DB`・`POSTGRES_USER`・`POSTGRES_PASSWORD` で指定した primary
database とユーザーは official image entrypoint が自動で作成します。

`docker/postgres/init/00-create-test-database.sh` の責務は以下だけです。

- `sreadertest` role の作成
- `sreadertest` database の作成
- `sreadertest` database への権限付与

この初期化スクリプトには `CREATE TABLE`、`CREATE VIEW`、seed data の
`INSERT`、利用者データを含めません。

既存 Docker volume がある場合、PostgreSQL entrypoint の init script は
再実行されません。schema 更新は Flyway migration を適用してください。
DB/role 作成からやり直す必要がある場合は、ローカル開発 DB を破棄する
操作であることを理解したうえで、次を実行してください。

```sh
docker compose down -v
docker compose up -d postgres
```

`docker compose down -v` は `postgres-data` volume を削除し、ローカルの
開発 DB データを失います。

> **注意**: 既存の MySQL volume または MySQL Flyway schema history がある場合、
> PostgreSQL 化後の標準手順とは互換性がありません。fresh PostgreSQL database
> に対して migration を適用してください。既存 MySQL 開発データの自動移行は
> 今回のスコープ外です。

## Flyway migration

Flyway は専用 Compose service として定義しています。DB migration を Maven
build と別責務にでき、CI や将来の自動化でも database ごとに同じ service を
再利用しやすいためです。image は `redgate/flyway:12.8.1` に固定しています。

Flyway は PostgreSQL 用 JDBC URL を使用します。
`FLYWAY_URL=jdbc:postgresql://postgres:5432/sreader`

- `V1__create_schema.sql` は PostgreSQL 用に変換した legacy schema を作成します。
  MySQL 固有の `ENGINE=InnoDB`、`DEFAULT CHARSET=utf8mb4`、`COLLATE`、
  `LONGTEXT` を除去・変換済みです。
- `V2__insert_development_seed_data.sql` は V1 時点の dummy seed data を投入します。
- `V3__remove_email_delivery_and_authenticated_fetch.sql` は Gmail 配信と認証付き取得の
  廃止に伴い、`content_view`、`publish_log`、`subscriber`、`account`、
  `login_rules` を削除します。

fresh DB では V1/V2 の後に V3 が適用され、一時的に古い table/seed が
作られてから削除されます。

> **注意**: この変更により、既存の MySQL Flyway volume / schema history との
> 互換性は標準サポート外になります。

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
- MySQL Connector/J (`com.mysql:mysql-connector-j`) を削除しました。
- PostgreSQL JDBC Driver (`org.postgresql:postgresql:42.7.3`) を追加しました。

## Hibernate / JDBC 設定

- JDBC URL: `jdbc:postgresql://postgres:5432/sreader`
- JDBC driver: `org.postgresql.Driver`
- Hibernate dialect: `org.hibernate.dialect.PostgreSQL82Dialect`

Hibernate 4.2.7.SP1 で利用可能な最高位の PostgreSQL dialect として
`PostgreSQL82Dialect` を使用しています。Hibernate 本格アップグレードは
今回のスコープ外です。

## Legacy SQL

`commons/script/` 配下の SQL は現代化前の host-oriented setup 用の
legacy reference です。標準手順では実行しません。

- `gmail.sql` は Gmail 配信廃止により使用しません。
- `dml.sql` に含まれる historical `login_rules` seed は廃止済み機能の記録です。
- `ddl.mysql.tables.sql` は historical schema reference であり、現行 schema の
  定義元ではありません。MySQL 固有構文を含みます。
- `ddl.mysql.users.sql` をホストの `mysql` command で直接実行する旧手順は
  標準手順ではありません。

標準セットアップ・標準 test に `mysql` service、`mysql` コマンド、
`jdbc:mysql` は使用しません。

## 検証

代表的な検証 command は次の通りです。

```sh
docker compose config
docker compose down -v
docker compose up -d postgres
docker compose ps
docker compose exec postgres psql -U sreader -d sreader -c "SELECT version();"
docker compose exec postgres psql -U sreader -d sreader -c "\l"
docker compose run --rm flyway info
docker compose run --rm flyway migrate
docker compose run --rm flyway info
docker compose exec postgres psql -U sreader -d sreader -c "\dt"
docker compose exec postgres psql -U sreader -d sreader -c "SELECT installed_rank, version, description, success FROM flyway_schema_history ORDER BY installed_rank;"
docker compose run --rm flyway -url=jdbc:postgresql://postgres:5432/sreadertest -user=sreadertest -password=sreadertest info
docker compose run --rm flyway -url=jdbc:postgresql://postgres:5432/sreadertest -user=sreadertest -password=sreadertest migrate
docker compose exec postgres psql -U sreadertest -d sreadertest -c "\dt"
docker compose exec postgres psql -U sreadertest -d sreadertest -c "SELECT installed_rank, version, description, success FROM flyway_schema_history ORDER BY installed_rank;"
docker compose run --rm maven mvn clean verify
```

## 残課題

- Hibernate 4 から modern Hibernate への移行
- Java language level の引き上げ
- Spring Boot 化
- DB schema migration の将来的な squash
- legacy な host-oriented shell script の Docker Compose wrapper 化または削除
- 本番運用設計
- PostgreSQL JDBC Driver バージョンの定期更新
