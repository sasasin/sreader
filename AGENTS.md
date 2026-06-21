# AGENTS.md

このファイルは、OpenAI Codex などのエージェントが `sasasin/sreader` リポジトリを変更するときに、毎回確認すべき前提と検証コマンドをまとめたものです。

## 現在の構成

現在のアプリケーションは Spring Boot 版アプリケーションです。

- Java 25
- Spring Boot
- jOOQ
- Flyway
- PostgreSQL 18.x
- Docker Compose
- Maven

## ホスト環境のルール

ホスト OS に Java / Maven / PostgreSQL client / Flyway / jOOQ codegen 用ツールを直接インストールしないでください。

ホスト側で直接実行してよいものは、原則として以下に限定します。

- `git`
- `docker`
- `docker compose`
- `ls`, `cat`, `grep`, `find`, `sed` などの基本的な確認コマンド

以下は必ず Docker Compose 経由で実行してください。

- `mvn`
- `java`
- `javac`
- `psql`
- Flyway
- jOOQ code generation

## 変更前に確認すること

作業前に、現在の構成を確認してください。

```sh
find . -maxdepth 3 -type f \
  \( -name 'pom.xml' -o -name 'docker-compose.yml' -o -name 'Dockerfile*' -o -name '*.md' -o -name '*.yml' -o -name '*.yaml' \) \
  | sort
```

## 通常変更後に毎回実行する確認コマンド

通常のコード変更、設定変更、README 更新後は、最低限以下を実行してください。

```sh
docker compose config
```

```sh
docker compose up -d postgres
```

```sh
docker compose ps
```

```sh
docker compose exec postgres psql -U sreader -d sreader -c "SELECT version();"
```

```sh
docker compose run --rm flyway migrate
```

```sh
docker compose run --rm maven mvn -version
```

```sh
docker compose run --rm maven java -version
```

```sh
docker compose run --rm maven mvn clean verify
```

```sh
docker compose build app
```

```sh
docker compose up -d app
```

```sh
docker compose logs --tail=200 app
```

## Java formatter / linter の確認

Java コードを変更した場合は、レビュー前に Spotless と Checkstyle を通してください。
ホスト OS ではなく Docker Compose 経由で Maven を実行します。

```sh
docker compose run --rm maven mvn spotless:apply
```

```sh
docker compose run --rm maven mvn spotless:check
```

```sh
docker compose run --rm maven mvn checkstyle:check
```

```sh
docker compose run --rm maven mvn verify
```

実行する Maven goal は以下です。

```sh
mvn spotless:apply
mvn spotless:check
mvn checkstyle:check
mvn verify
```

- Java コード変更後は `mvn spotless:apply` で整形すること。
- レビュー前に `mvn spotless:check` と `mvn checkstyle:check` を通すこと。
- 最終確認として `mvn verify` を実行すること。
- generated sources や `target/` 配下を手編集しないこと。
- jOOQ generated code は formatter / linter の対象にしないこと。

## DB / Flyway / jOOQ を変更した場合の追加確認

以下を変更した場合は、fresh DB で検証してください。

- Flyway migration
- jOOQ code generation 設定
- DB schema
- PostgreSQL 接続設定
- repository / persistence layer
- Docker Compose の `postgres` / `app` / `maven` service

ローカル開発 DB を破棄してよい場合だけ、以下を実行してください。

```sh
docker compose down -v
```

その後、fresh DB で起動・migration・code generation・test を確認してください。

```sh
docker compose up -d postgres
```

```sh
docker compose exec postgres psql -U sreader -d sreader -c "SELECT version();"
```

```sh
docker compose run --rm flyway migrate
```

```sh
docker compose run --rm maven mvn clean generate-sources
```

```sh
docker compose run --rm maven mvn clean verify
```

schema と Flyway 履歴を確認してください。

```sh
docker compose exec postgres psql -U sreader -d sreader -c "\dt"
```

```sh
docker compose exec postgres psql -U sreader -d sreader -c "SELECT installed_rank, version, description, success FROM flyway_schema_history ORDER BY installed_rank;"
```

## Scheduler / feed reader を変更した場合の確認

Spring Scheduler、feed reader job、HTTP 取得、RSS/Atom parse、本文抽出に関係する変更をした場合は、通常確認に加えて以下を確認してください。

```sh
docker compose up -d app
```

```sh
docker compose logs --tail=300 app | grep -i -E "scheduler|feed|job|sreader|error|exception" || true
```

手動実行 path が用意されている場合は、README の記載と一致するコマンドで実行してください。例:

```sh
docker compose run --rm app java -jar /app/app.jar --sreader.scheduler.enabled=false --sreader.job.run-once=true
```

実際の jar path / option は現在の Dockerfile と README に合わせてください。

## Docker / Compose を変更した場合の確認

Dockerfile、`docker-compose.yml`、`.env.example` を変更した場合は、以下を実行してください。

```sh
docker compose config
```

```sh
docker compose build --no-cache app
```

```sh
docker compose up -d postgres
```

```sh
docker compose up -d app
```

```sh
docker compose ps
```

```sh
docker compose logs --tail=200 app
```

## README を変更した場合の確認

README を変更した場合は、以下を確認してください。

- 手順が Docker Compose 前提になっていること
- ホストに Java / Maven / PostgreSQL client / Flyway を入れる手順を書いていないこと
- RDBMS が PostgreSQL 18.x になっていること
- Spring Scheduler による定期実行が説明されていること

## Maven dependency の確認

依存関係を変更した場合は、以下を確認してください。

```sh
docker compose run --rm maven mvn dependency:tree
```

## 期待する最終状態

各変更後、少なくとも以下を満たしてください。

- `docker compose config` が成功する。
- `docker compose up -d postgres` が成功する。
- PostgreSQL 18.x に接続できる。
- `docker compose run --rm maven mvn clean verify` が成功する。
- `docker compose build app` が成功する。
- `docker compose up -d app` が成功する。
- app logs に起動直後の致命的例外がない。
- README が現行構成と一致している。

## 失敗した場合の扱い

検証が失敗した場合は、失敗を隠さず、以下を報告してください。

- 失敗したコマンド
- exit code
- 主要な error message
- 失敗分類
  - build failure
  - test failure
  - Docker / Compose failure
  - PostgreSQL connection failure
  - Flyway migration failure
  - jOOQ code generation failure
  - app startup failure
  - documentation mismatch
- 直した内容
- 残課題

## 最終報告フォーマット

エージェントは作業完了時に、以下の形式で報告してください。

1. 変更概要
2. 変更したファイル
3. 実行した確認コマンド
4. 成功した検証
5. 失敗した検証があれば、その原因
6. README 更新の有無
7. 残課題
8. 次の推奨ステップ
