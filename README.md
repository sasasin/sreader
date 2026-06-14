SReader
=======

SReader は、RSS/Atom リーダーです。現在は、約 13 年間停止していた
codebase を Docker Compose / Maven multi-module / Flyway / PostgreSQL 16.x
ベースへ現代化している途中です。

現在サポートする範囲は、認証不要な公開 RSS/Atom feed を取得し、記事
URL とタイトルを `content_header` に保存し、記事本文を抽出して
`content_full_text` に保存するところまでです。

## 廃止した機能

運用安全性を優先し、以下の機能は削除しました。

- Gmail アカウントを用いたメール配信
- SMTP / JavaMail / commons-email によるメール送信
- Gmail password を DB に保存する設計
- 購読先ログイン ID/password と `login_rules` による認証付き取得
- 購読先 password を DB に保存する設計

OAuth2 や secret manager への置き換えではなく、認証情報を扱う機能
そのものを廃止しています。

## Docker Compose セットアップ

ホスト OS に JDK/Maven/PostgreSQL client/Flyway を入れず、Docker Compose
経由で実行します。標準 DB は PostgreSQL 16.x です。

```sh
docker compose config
docker compose up -d postgres
docker compose ps
docker compose run --rm flyway info
docker compose run --rm flyway migrate
docker compose run --rm flyway info
docker compose run --rm flyway -url=jdbc:postgresql://postgres:5432/sreadertest -user=sreadertest -password=sreadertest info
docker compose run --rm flyway -url=jdbc:postgresql://postgres:5432/sreadertest -user=sreadertest -password=sreadertest migrate
docker compose run --rm flyway -url=jdbc:postgresql://postgres:5432/sreadertest -user=sreadertest -password=sreadertest info
docker compose run --rm maven mvn clean verify
```

既存 Docker volume がある場合は、新しい Flyway migration を適用して
schema を移行してください。開発 DB を作り直す場合は次を実行できますが、
`docker compose down -v` はローカル DB volume を削除します。

```sh
docker compose down -v
docker compose up -d postgres
docker compose run --rm flyway migrate
```

> **注意**: `docker compose down -v` は `postgres-data` volume を削除し、
> ローカルの開発 DB データをすべて失います。実行前に確認してください。

> **注意**: 既存の MySQL volume / Flyway schema history は標準サポート外です。
> PostgreSQL 化後は fresh PostgreSQL database に対して migration を適用してください。

## Feed URL の登録

`$HOME/sreader.txt` に、認証不要な公開 RSS/Atom feed URL を 1 行に 1 つ
記載します。

```text
https://example.com/rss.xml
https://example.net/atom.xml
```

現代化後の標準手順では、ID/password をタブ区切りで記載する形式は使いません。

batch の legacy shell script を使う場合の流れは次の通りです。

```sh
./batch/script/run_feedreader.sh
```

## Legacy SQL

`commons/script/` 配下の SQL は legacy reference です。現代化後の標準
セットアップでは、`ddl.mysql.users.sql`、`ddl.mysql.tables.sql`、
`dml.sql`、`gmail.sql` をホストの `mysql` コマンドで直接実行しません。

特に `gmail.sql` は Gmail 配信廃止後の標準手順では使いません。
`login_rules` も廃止済みです。DB schema と development seed data は
`db/migration/` の Flyway migration で管理します。

配布条件
------

本プログラムはフリーソフトウェアです。LGPL (the GNU Lesser General
Public License) バージョン 3、またはそれ以降のバージョンに示す条件で
本プログラムを再配布できます。LGPL については LICENSE ファイルを参照して
ください。
