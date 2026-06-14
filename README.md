SReader
=======

SReader は、認証不要な公開 RSS/Atom feed を取得し、記事 URL/タイトルと
本文抽出結果を PostgreSQL に保存する lightweight feed reader です。

このリライトで標準アプリケーションは Java 25 + Spring Boot + jOOQ +
Flyway + PostgreSQL 16.x の `app` module になりました。旧 Hibernate
entity/DAO、JPA、legacy batch shell script は削除済みです。

## 廃止済み機能

運用安全性を優先し、以下の機能は復活させていません。

- Gmail アカウントを用いたメール配信
- SMTP / JavaMail / commons-email によるメール送信
- Gmail password を DB に保存する設計
- 購読先ログイン ID/password と `login_rules` による認証付き取得
- 購読先 password を DB に保存する設計

## 標準構成

- `app/`: Spring Boot application
- `db/migration/`: Flyway migration。fresh development DB 前提で
  `feed_url`、`content_header`、`content_full_text`、`eft_rules` だけを作成
- `docker-compose.yml`: `postgres`、`flyway`、`maven`、`app`

旧 `commons/` と `batch/` module は削除済みです。標準 build/runtime path は
Spring Boot 版 `app/` module だけを対象にします。

ホスト OS に Java / Maven / PostgreSQL client / Flyway を入れないでください。
標準操作は Docker Compose 経由です。

## セットアップ

```sh
docker compose config
docker compose down -v
docker compose up -d postgres
docker compose ps
docker compose exec postgres psql -U sreader -d sreader -c "SELECT version();"
```

`docker compose down -v` はローカル PostgreSQL volume を削除します。既存の
開発 DB データが必要な場合は実行しないでください。既存 MySQL volume /
schema history との互換性は標準サポート外です。

## Flyway

```sh
docker compose run --rm flyway info
docker compose run --rm flyway migrate
docker compose run --rm flyway info
docker compose exec postgres psql -U sreader -d sreader -c "\dt"
```

Spring Boot app 起動時にも Flyway auto migration が有効です。

## jOOQ / build / test

jOOQ code generation は Docker Compose の PostgreSQL schema から
`target/generated-sources/jooq` へ生成します。生成コードは Git 管理しません。

```sh
docker compose run --rm maven mvn -version
docker compose run --rm maven java -version
docker compose run --rm maven mvn clean generate-sources
docker compose run --rm maven mvn clean verify
```

`maven` service は jOOQ codegen 用に `sreader` DB へ接続し、test 実行時は
`sreadertest` DB に接続します。`docker/postgres/init/00-create-test-database.sql`
が test DB/role を作成します。

## app 起動

```sh
docker compose build app
docker compose up app
```

app container は `/app/app.jar` を実行します。

## Scheduler

旧 cron script 方式は削除済みです。Spring Scheduler が
アプリケーションコンテナ内で job を実行します。

主な環境変数:

- `SREADER_SCHEDULER_ENABLED=true`
- `SREADER_SCHEDULER_CRON=0 */15 * * * *`
- `SREADER_HTTP_USER_AGENT=SReader/0.1`
- `SREADER_HTTP_CONNECT_TIMEOUT=5s`
- `SREADER_HTTP_READ_TIMEOUT=20s`
- `SREADER_HTTP_RETRY_COUNT=1`
- `SREADER_SEED_FEED_URLS=` comma separated feed URLs

無効化起動:

```sh
docker compose run --rm -e SREADER_SCHEDULER_ENABLED=false app
```

1 回だけ job を実行:

```sh
docker compose run --rm app java -jar /app/app.jar --sreader.scheduler.enabled=false --sreader.job.run-once=true
```

## 残課題

- 本番データ移行
- 本格 Web UI
- production monitoring / secret manager 設計
- distributed scheduler lock
- feed URL 管理用の運用 API

配布条件
------

本プログラムはフリーソフトウェアです。LGPL (the GNU Lesser General
Public License) バージョン 3、またはそれ以降のバージョンに示す条件で
本プログラムを再配布できます。LGPL については LICENSE ファイルを参照して
ください。
