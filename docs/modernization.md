# モダナイゼーション: Spring Boot + jOOQ + Flyway + PostgreSQL

## 目的

SReader の中核である「認証不要な公開 RSS/Atom feed の取得、記事
URL/タイトル保存、本文抽出、DB 保存」を、Java 25 + Spring Boot + jOOQ +
Flyway + PostgreSQL 16.x の標準的なアプリケーションとして作り直しました。

ホスト OS に Maven、JDK、Java コマンド、PostgreSQL client、Flyway を
インストールしない方針は維持し、標準操作は Docker Compose に統一します。

## 採用 version

- Java: 25
- Spring Boot: 3.5.7
- jOOQ: 3.20.7
- Flyway image: `redgate/flyway:12.8.1`
- PostgreSQL: `postgres:16.9`
- PostgreSQL JDBC Driver: 42.7.7
- ROME: 2.1.0
- jsoup: 1.21.2

Spring Boot は現時点で Java 25 との組み合わせを優先し、Spring Boot 3.5 系を
固定しました。`latest` は使用しません。

## module 構成

- `app`: 標準 Spring Boot application
- `commons`: legacy reference。標準 build から除外
- `batch`: legacy reference。標準 build から除外
- `db/migration`: app と Flyway service が共有する migration

root `pom.xml` の reactor は `app` のみです。旧 Hibernate / DAO / shell
script を延命せず、新 app 側に service/repository として移植しました。

## 移植したドメインロジック

- feed URL と article URL は旧実装同様 MD5 hex を ID として利用
- feed URL は重複登録しない
- RSS/Atom parse は ROME を利用
- feed entry から article URL と title を `content_header` に保存
- article URL は redirect 後 URI を優先
- 未抽出 `content_header` だけを本文抽出対象にする
- `eft_rules` は URL pattern の最長一致を採用し、XPath で本文抽出
- rule 不一致または抽出失敗時は body text に fallback

Gmail/SMTP、`login_rules`、外部サイト login/cookie 認証は移植していません。

## Flyway schema 方針

リライト作業のため fresh development DB 前提で migration を squash しました。
本番データ移行は今回のスコープ外です。

現行 table:

- `feed_url`
- `content_header`
- `content_full_text`
- `eft_rules`

廃止 table/view:

- `account`
- `subscriber`
- `publish_log`
- `login_rules`
- `content_view`

ID は既存ロジックの移植コストを下げるため `char(32)` を維持しました。
`created_at`、`updated_at`、`published_at`、`extracted_at` は運用確認と
job 対象の並び替えに使うため追加しています。

## jOOQ code generation

`docker compose run --rm maven mvn clean generate-sources` で、PostgreSQL の
`public` schema から `target/generated-sources/jooq` へ生成します。
`flyway_schema_history` は生成対象から除外します。生成コードは Git 管理しません。

## Scheduler / run-once

`@EnableScheduling` と `@Scheduled(cron = "${sreader.scheduler.cron}")` を使います。
`sreader.scheduler.enabled` で有効/無効を切り替え、同一 JVM 内では
`AtomicBoolean` で多重起動を避けます。

job の順序:

1. seed feed URL を登録
2. DB の feed URL 一覧を読む
3. RSS/Atom feed を取得して `content_header` に insert
4. 未抽出記事を取得して本文抽出
5. 結果を log

手動実行:

```sh
docker compose run --rm app java -jar /app/app.jar --sreader.scheduler.enabled=false --sreader.job.run-once=true
```

## Docker Compose

標準 service:

- `postgres`
- `flyway`
- `maven`
- `app`

代表 command:

```sh
docker compose config
docker compose down -v
docker compose up -d postgres
docker compose ps
docker compose exec postgres psql -U sreader -d sreader -c "SELECT version();"
docker compose run --rm flyway info
docker compose run --rm flyway migrate
docker compose run --rm flyway info
docker compose run --rm maven mvn clean generate-sources
docker compose run --rm maven mvn clean verify
docker compose build app
docker compose up app
```

`docker compose down -v` は PostgreSQL volume を削除します。既存データが必要な
場合は実行しないでください。

## Legacy code の扱い

`commons` / `batch` には Hibernate entity/DAO、JUnit 4/DBUnit、旧
`batch/script/run_feedreader.sh` が残っていますが、標準 reactor と app runtime
には含めません。参照用 legacy-only code です。

標準手順では `batch/script/run_feedreader.sh` を実行しません。README からも
cron 前提の手順を削除しました。

## 残課題

- 本番データ移行
- feed URL 登録 API または管理 CLI
- 本格 Web UI
- production monitoring
- secret manager 設計
- distributed scheduler lock
