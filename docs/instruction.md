あなたは Java / Maven / MySQL / Docker に詳しいソフトウェア移行エンジニアです。

対象リポジトリは `sasasin/sreader` です。これは約13年前に更新停止した Java/Maven/MySQL ベースのリポジトリです。私はローカルPCに `mvn`, `javac`, `mysql` などを直接インストールしたくありません。作業PCには Docker Desktop がインストール済みで、ホスト側では `docker` と `docker compose` のみ利用できます。

この制約を守りながら、以下の方針で「計画案B: ビルドと依存を正常化する標準リハビリ」を進めてください。

## 最重要制約

* ホストOSに Maven / Java / MySQL client / その他ビルドツールをインストールしないでください。
* `mvn`, `javac`, `java`, `mysql`, `mysqldump` などは必ず Docker コンテナ内で実行してください。
* ホスト側で直接実行してよいコマンドは、原則として以下だけです。

  * `git`
  * `docker`
  * `docker compose`
  * `ls`, `cat`, `grep`, `sed`, `find` などの基本的なファイル確認コマンド
* 依存バージョンをむやみに最新化しないでください。まずは再現可能なビルド基盤を作ることを優先してください。
* 機能変更は最小限にしてください。
* セキュリティ上危険な変更、認証情報のハードコード、ローカル秘密情報の追加はしないでください。
* 破壊的な Git 操作、強制 push、履歴改変はしないでください。

## 作業ゴール

第1マイルストーンは、以下を満たすことです。

1. Docker Compose だけで Java/Maven/MySQL の作業環境を起動できる。
2. リポジトリの Maven ビルドを Docker コンテナ内で実行できる。
3. `commons` と `batch` を Maven multi-module プロジェクトとして扱えるようにする。
4. POM のバージョンレンジ指定を廃止し、依存バージョンを明示固定する。
5. MySQL 初期化 SQL を現行 MySQL で実行可能な形に近づける。
6. 最低限の検証手順を `README` または `docs/modernization.md` に記録する。
7. 最終的に、成功・失敗を問わず、どのコマンドを実行し、何が通り、何が残課題かを明確に報告する。

## 進め方

### 1. まずリポジトリ全体を調査してください

最初に以下を確認してください。

* ディレクトリ構成
* Maven POM の構成
* `commons` と `batch` の関係
* 既存の shell script
* SQL script
* Java バージョン指定
* 依存ライブラリ
* テストの有無
* README のビルド・実行手順
* system scope / ローカル jar / バージョンレンジ / 古い plugin 指定の有無

調査したら、作業前に短い方針メモを出してください。

### 2. Docker Compose 環境を追加してください

ルートディレクトリに `docker-compose.yml` を追加または更新してください。

要件:

* Maven + JDK 用のサービスを用意する。
* MySQL 用のサービスを用意する。
* Maven のローカルリポジトリ `.m2` は Docker volume にして、毎回依存を取り直さないようにする。
* 作業ディレクトリをコンテナに bind mount する。
* MySQL のデータは Docker volume に保存する。
* MySQL の root password、DB 名、ユーザー名、パスワードは開発用のダミー値にする。
* `.env` が必要なら `.env.example` を作り、実秘密情報はコミットしない。
* ホスト側から `docker compose run --rm maven ...` または `docker compose exec ...` で Maven を実行できるようにする。
* ホスト側に Maven/JDK/MySQL client がなくても作業できるようにする。

イメージは、現行の OpenJDK/JDK と Maven を使ってください。ただし、ビルドが古い Java ソース互換性で失敗する場合は、Dockerfile または Compose の中で JDK バージョンを切り替えやすい構成にしてください。

必要であれば、`Dockerfile.maven` のような Maven 作業用 Dockerfile を作って構いません。

### 3. Maven multi-module 化してください

現在の構成を確認したうえで、ルートに親 POM がない場合は追加してください。

要件:

* root `pom.xml` を packaging `pom` として作成する。
* modules に `commons` と `batch` を含める。
* 共通の Java version、encoding、plugin version、依存 version を親 POM 側で管理する。
* 既存の `commons/pom.xml` と `batch/pom.xml` は、親 POM を参照する形へ整理する。
* `batch` が `commons` に依存している場合、ローカル module dependency として扱う。
* 既存の build script がある場合は、必要に応じて Docker Compose 経由で動く形に更新する。

### 4. POM のバージョンレンジを廃止してください

`[2,)`, `[5.1,)` のような Maven version range を使っている場合、すべて明示バージョンに固定してください。

方針:

* まずは「大規模 API 変更を避ける」ため、既存コードに近いバージョンで固定してください。
* ただし MySQL Connector/J など、artifact group/name が現行と大きく変わっているものは、現行 artifact への移行を検討してください。
* バージョン選定理由を `docs/modernization.md` に簡潔に記録してください。
* 依存を一気に全部最新版へ上げるのではなく、「まず reproducible build」を優先してください。

### 5. Maven Enforcer / Compiler / Surefire を整理してください

親 POM に必要な plugin 管理を追加してください。

候補:

* `maven-compiler-plugin`
* `maven-surefire-plugin`
* `maven-enforcer-plugin`

要件:

* Java source/target/release の指定を明示してください。
* 文字コードは UTF-8 を明示してください。
* Maven バージョンや Java バージョンの最低条件を enforcer で表現してください。
* ただし、現在のコードが最新 JDK でコンパイルできない場合は、原因を記録したうえで最小修正してください。

### 6. MySQL 初期化 SQL を現行 MySQL 向けに整理してください

既存の SQL script を調査し、現行 MySQL で問題になりそうな箇所を洗い出してください。

特に以下を確認してください。

* `GRANT ... IDENTIFIED BY ...` のような古いユーザー作成構文
* `utf8` 指定
* engine / charset / collation
* reserved word との衝突
* timestamp / datetime のデフォルト値
* root 権限前提の SQL
* テスト DB と本番 DB の分離

必要に応じて、Docker Compose の MySQL 初期化用に `docker/mysql/init/` のようなディレクトリを作り、現行 MySQL で実行できる初期化 SQL を配置してください。

ただし、元の SQL を削除するのではなく、可能なら互換用または履歴として残してください。変更理由をドキュメント化してください。

### 7. 検証コマンドを Docker 経由に統一してください

以下のようなコマンドで検証できるようにしてください。

例:

```sh
docker compose up -d mysql
docker compose run --rm maven mvn -version
docker compose run --rm maven java -version
docker compose run --rm maven mvn clean verify
docker compose exec mysql mysql -uroot -p... -e "SHOW DATABASES;"
```

実際のサービス名やパスワードは、作成した Compose に合わせて調整してください。

可能なら Makefile も追加してください。ただし Makefile は必須ではありません。追加する場合も、内部では必ず `docker compose` を呼び出してください。

候補:

```sh
make docker-up
make docker-down
make mvn-verify
make mysql-shell
```

### 8. ドキュメントを追加してください

`docs/modernization.md` または README の modernization section に、以下を記録してください。

* 今回の目的
* ローカルPCに Maven/JDK/MySQL client を入れない方針
* Docker Compose の使い方
* ビルド手順
* テスト手順
* MySQL 初期化手順
* 依存バージョン固定の方針
* まだ更新していない古い依存
* Hibernate 移行は今回の第1マイルストーンでは深追いしないこと
* 残課題

### 9. 変更後に必ず検証してください

最低限、以下を実行してください。

```sh
docker compose config
docker compose up -d mysql
docker compose run --rm maven mvn -version
docker compose run --rm maven java -version
docker compose run --rm maven mvn clean verify
```

もし `mvn clean verify` が失敗する場合:

* エラーを読んで、最小修正で進めてください。
* 依存解決エラー、コンパイルエラー、テストエラーを分類してください。
* すぐ直せるものは直してください。
* 大規模移行が必要なものは無理に直さず、残課題として記録してください。
* 最終報告では、失敗したコマンド、エラー概要、次にやるべきことを明記してください。

### 10. 今回やらないこと

以下は今回のスコープ外です。

* Hibernate 4 系から最新 Hibernate への本格移行
* Spring Boot 化
* アプリケーション仕様の変更
* DB スキーマの大規模再設計
* Gmail 認証方式の本格刷新
* 全依存ライブラリの最新版追従
* UI や機能追加
* 本番運用向け secret 管理

ただし、これらが将来必要であることは `docs/modernization.md` の残課題に記録してください。

## 成果物

最終的に、以下を含む patch を作ってください。

* `docker-compose.yml`
* 必要なら Maven 作業用 `Dockerfile`
* root `pom.xml`
* 更新された `commons/pom.xml`
* 更新された `batch/pom.xml`
* 必要なら Docker 用 MySQL 初期化 SQL
* 必要なら `.env.example`
* 必要なら `.gitignore` の更新
* `docs/modernization.md` または README 更新
* 必要なら Makefile

## 最終報告フォーマット

作業完了後、以下の形式で報告してください。

1. 変更概要
2. 追加・変更したファイル
3. 実行した Docker / Maven / MySQL コマンド
4. 成功した検証
5. 失敗した検証があれば、その原因
6. 依存バージョン固定の方針
7. 残課題
8. 次の推奨ステップ

この作業では「最新化を完了する」ことよりも、「Docker だけで再現可能にビルド・検証できる土台を作る」ことを最優先してください。
