SReader
=======

SReader は、認証不要な公開 RSS/Atom feed を取得し、記事 URL/タイトルと本文抽出結果を PostgreSQL に保存する lightweight feed reader です。

テックスタックは Java 25 + Spring Boot + jOOQ + Flyway + PostgreSQL 17.x です。

## ディレクトリ構成

- `app/`: Spring Boot application
- `db/migration/`: Flyway migration。fresh development DB 前提で
  `feed_url`、`content_header`、`content_full_text`、`eft_rules` だけを作成
- `docker-compose.yml`: `postgres`、`flyway`、`maven`、`app`

標準 build/runtime path は `app/` module を対象にします。

Docker Compose 経由で操作可能です。ホスト OS に Java / Maven / PostgreSQL client / Flyway を入れる必要はありません。

## セットアップ

```sh
docker compose config
docker compose down -v
docker compose up -d postgres
docker compose ps
docker compose exec postgres psql -U sreader -d sreader -c "SELECT version();"
```

`docker compose down -v` はローカル PostgreSQL volume を削除します。既存の開発 DB データが必要な場合は実行しないでください。

## Flyway

```sh
docker compose run --rm flyway info
docker compose run --rm flyway migrate
docker compose run --rm flyway info
docker compose exec postgres psql -U sreader -d sreader -c "\dt"
```

Spring Boot app 起動時にも Flyway auto migration が有効です。

## jOOQ / build / test

jOOQ code generation は Docker Compose の PostgreSQL schema から `target/generated-sources/jooq` へ生成します。生成コードは Git 管理しません。

```sh
docker compose run --rm maven mvn -version
docker compose run --rm maven java -version
docker compose run --rm maven mvn clean generate-sources
docker compose run --rm maven mvn clean verify
```

`maven` service は jOOQ codegen 用に `sreader` DB へ接続し、test 実行時は `sreadertest` DB に接続します。`docker/postgres/init/00-create-test-database.sql` が test DB/role を作成します。

## app 起動

```sh
docker compose build app
docker compose up app
```

app container は `/app/app.jar` を実行します。

## Scheduler

Spring Scheduler がアプリケーションコンテナ内で job を実行します。

主な環境変数:

- `SREADER_SCHEDULER_ENABLED=true`
- `SREADER_SCHEDULER_CRON=0 */15 * * * *`
- `SREADER_HTTP_USER_AGENT=SReader/0.1`
- `SREADER_HTTP_CONNECT_TIMEOUT=5s`
- `SREADER_HTTP_READ_TIMEOUT=20s`
- `SREADER_HTTP_RETRY_COUNT=1`
- `SREADER_PLAYWRIGHT_ENABLED=false`
- `SREADER_SEED_FEED_URLS=` comma separated feed URLs

無効化起動:

```sh
docker compose run --rm -e SREADER_SCHEDULER_ENABLED=false app
```

1 回だけ job を実行:

```sh
docker compose run --rm app --sreader.scheduler.enabled=false --sreader.job.run-once=true
```

## 全文取得 method

`feed_url.full_text_method` は以下を指定できます。省略時は従来通り `http` です。

- `feed`: RSS/Atom entry 本文を保存します。
- `http`: 記事 URL を HTTP GET し、XPath rule、失敗時 body text で抽出します。
- `playwright`: Chromium で JS 実行後の DOM HTML から XPath rule、失敗時 body text で抽出します。
- `playwright_readability`: Chromium で JS 実行後の DOM HTML を Readability4J で抽出します。
- `playwright_infy_scroll`: Infy Scroll extension を読み込んだ Chromium で scroll 後の DOM HTML から XPath rule、失敗時 body text で抽出します。
- `playwright_infy_scroll_readability`: Infy Scroll 後の DOM HTML を Readability4J で抽出します。

Playwright 系 method はデフォルトでは無効です。使う場合は `SREADER_PLAYWRIGHT_ENABLED=true` を設定してください。無効時に Playwright 系 method の feed がある場合、その記事の全文取得は warning log を出して skip します。

Playwright / Infy Scroll 用の主な環境変数:

- `SREADER_PLAYWRIGHT_HEADLESS=true`
- `SREADER_PLAYWRIGHT_VIEWPORT_WIDTH=1280`
- `SREADER_PLAYWRIGHT_VIEWPORT_HEIGHT=1600`
- `SREADER_PLAYWRIGHT_NAVIGATION_TIMEOUT=60s`
- `SREADER_PLAYWRIGHT_NETWORK_IDLE_TIMEOUT=5s`
- `SREADER_PLAYWRIGHT_INFY_EXTENSION_DIR=/opt/sreader/extensions/infy-scroll`
- `SREADER_PLAYWRIGHT_INFY_USER_DATA_DIR=/var/lib/sreader/playwright-infy-profile`
- `SREADER_PLAYWRIGHT_INFY_MAX_SCROLLS=20`
- `SREADER_PLAYWRIGHT_INFY_STABLE_ROUNDS=3`
- `SREADER_PLAYWRIGHT_INFY_SCROLL_WAIT=2700ms`

Docker app image は system Chromium を含みます。Maven service で Playwright の bundled browser を使う場合は、Docker Compose 経由で以下を実行してください。

```sh
docker compose run --rm maven mvn -pl app exec:java -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install chromium"
```

Infy Scroll は実行時に Chrome Web Store からインストールしません。Chromium が load できる unpacked extension directory を用意し、その directory を `SREADER_PLAYWRIGHT_INFY_EXTENSION_DIR` に指定してください。GitHub source tree をそのまま指定して必ず動くとは限らないため、`manifest.json` を含む extension artifact を配置してください。

一度 `content_full_text` に保存された記事は `insertIfAbsent` のため、`full_text_method` を変更しても自動再抽出されません。再抽出が必要な場合は対象行の扱いを別途検討してください。

## feed URL TOML import / export

`feed_url` は購読状態を持つ購読レコードです。

- `active`: 記事取得対象です。
- `unsubscribed`: 購読停止済みです。DELETE ではなく tombstone として残し、過去に停止した feed が別環境や古い TOML import で誤って復活しにくいようにします。

export はデフォルトで `active` と `unsubscribed` の両方を URL 昇順で出力します。

```sh
docker compose run --rm app --sreader.scheduler.enabled=false feeds export --output /tmp/feeds.toml
docker compose run --rm app --sreader.scheduler.enabled=false feeds export --active-only --output /tmp/active-feeds.toml
```

import は safe merge です。TOML に存在する feed だけを insert/update し、TOML に存在しない DB 側 feed はデフォルトでは変更しません。dry-run で件数と conflict を確認できます。

```sh
docker compose run --rm -v ./feeds.toml:/tmp/feeds.toml:ro app --sreader.scheduler.enabled=false feeds import --input /tmp/feeds.toml --dry-run
docker compose run --rm -v ./feeds.toml:/tmp/feeds.toml:ro app --sreader.scheduler.enabled=false feeds import --input /tmp/feeds.toml
```

DB 側が `unsubscribed`、TOML 側が `active` の場合、デフォルトでは復活させず conflict として報告します。明示的に復活させる場合だけ `--resubscribe` を付けます。

```sh
docker compose run --rm -v ./feeds.toml:/tmp/feeds.toml:ro app --sreader.scheduler.enabled=false feeds import --input /tmp/feeds.toml --resubscribe
```

## TOML import 前に全文取得方式を試す

`feeds import` で `full_text_method` を指定する前に、probe コマンドで実際の記事/フィードに対して方式を試せます。DB へは何も書き込みません。

```sh
docker compose run --rm app --sreader.scheduler.enabled=false probe article \
  --url https://example.com/article \
  --method http
```

```sh
docker compose run --rm app --sreader.scheduler.enabled=false probe feed \
  --feed-url https://example.com/feed.xml \
  --method playwright_readability
```

```sh
docker compose run --rm app --sreader.scheduler.enabled=false feeds discover \
  --site-url https://example.com/
```

- `probe article` / `probe feed` は本文を STDOUT へ（`--output` 指定時はファイルへ）。`--verbose` 時は診断情報を STDERR へ。
- `--xpath` で XPath 抽出を明示的にテストできます（DB rule / Readability をバイパス）。
- `feeds discover` はページから RSS/Atom リンクを検出。`--format toml` で import 用の雛形を出力。
- Playwright 系 method を使う場合は `SREADER_PLAYWRIGHT_ENABLED=true`（および関連設定）が必要です。

TOML schema:

```toml
schema_version = 2
generated_at = "2026-06-14T12:00:00+09:00"

[[feeds]]
url = "https://example.com/feed.xml"
status = "active"
full_text_method = "http"

[[feeds]]
url = "https://closed.example/rss.xml"
status = "unsubscribed"
unsubscribe_reason = "site_closed"
unsubscribed_at = "2026-06-14T12:00:00+09:00"
note = "サイト閉鎖を確認したため"
full_text_method = "http"
```

`schema_version = 2`（エクスポート時の現在のバージョン）と `feeds[].url` は必須です。import では `schema_version = 1`（後方互換）も受け付けます（`full_text_method` 未指定時は `"http"` として扱います）。`status` は省略時 `active` です。`full_text_method` は省略時 `"http"` です。

URL は trim され、`http` / `https` の absolute URI のみ許可します。userinfo を含む URL は認証付き取得につながるため拒否します。同一 TOML 内で正規化後 URL が重複した場合も validation error です。

`unsubscribe_reason` は `unsubscribed` 用の任意項目です。省略時は `other` として扱います。

- `not_interested`: もう関心がない。
- `site_closed`: サイト閉鎖。
- `feed_dead`: feed が取得不能・壊れている。
- `moved`: 移転済み。
- `other`: その他。

## 残課題

- 本格 Web UI
- production monitoring / secret manager 設計
- distributed scheduler lock

配布条件
------

本プログラムはフリーソフトウェアです。LGPL (the GNU Lesser General Public License) バージョン 3、またはそれ以降のバージョンに示す条件で本プログラムを再配布できます。LGPL については LICENSE ファイルを参照してください。
