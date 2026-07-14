# SReader: 既存 URL 重複データ統合メンテナンスコマンドの実装指示

## 前提

この作業は、先行 PR「canonical URL 導入と新規重複発生停止」が merge・deploy 済みであることを前提とする。

DB の `content_header` に以下が存在すること。

```text
source_url
fetch_url
canonical_url
```

新規取り込みでは `canonical_url` が正規化され、COMEMO 記事 URL の `gs` 変化による新規重複が停止していること。

本 PR は、過去に保存された以下のような重複を安全に統合するメンテナンスコマンドを追加する。

```text
https://comemo.nikkei.com/n/n8fcec9d3d10f?gs=7b2f2a0bda7f
https://comemo.nikkei.com/n/n8fcec9d3d10f?gs=733b1e958c41
https://comemo.nikkei.com/n/n8fcec9d3d10f?gs=4ff17ebffc5d
```

これらは canonicalization 後に次へ集約される。

```text
https://comemo.nikkei.com/n/n8fcec9d3d10f
```

## 目的

- 既存 `content_header` を canonical URL ごとに検査する。
- 同一 canonical URL になる複数レコードを、決定論的な1レコードへ統合する。
- 最良の `content_full_text` を保持する。
- `content_text_file_export` の履歴と物理 `.txt` ファイルを整合させる。
- dry-run で変更予定を確認可能にする。
- 再実行しても追加変更が発生しない、冪等なコマンドにする。

この作業を Flyway migration だけで実装してはいけない。Spring Boot の明示的な CLI メンテナンスコマンドとして実装する。

---

# コマンド仕様

推奨 CLI:

```bash
docker compose run --rm app \
  --sreader.scheduler.enabled=false \
  content canonicalize --dry-run
```

実適用:

```bash
docker compose run --rm app \
  --sreader.scheduler.enabled=false \
  content canonicalize --apply
```

COMEMO のみに限定:

```bash
docker compose run --rm app \
  --sreader.scheduler.enabled=false \
  content canonicalize \
  --host comemo.nikkei.com \
  --apply
```

batch size 指定:

```bash
docker compose run --rm app \
  --sreader.scheduler.enabled=false \
  content canonicalize \
  --host comemo.nikkei.com \
  --batch-size 100 \
  --apply
```

## オプション要件

- `--dry-run`: DB・ファイルを変更せず、予定件数と代表例を出力する。
- `--apply`: 実際に変更する。
- `--dry-run` と `--apply` は排他的。どちらもない場合は安全側として dry-run にするか、usage error にする。既存 CLI の作法に合わせる。
- `--host <hostname>`: 対象 host を限定する。初回運用では `comemo.nikkei.com` を指定できること。
- `--batch-size <n>`: 1 transaction または1取得単位の最大 group 数。正の整数のみ。
- 可能なら `--limit <n>`: 検証用に処理 group 数を制限する。

本番利用時は scheduler を無効化して実行することを README に明記する。

---

# 推奨クラス・ファイル構成

既存 CLI framework・command pattern に合わせること。リポジトリに Picocli、Spring Shell、`ApplicationRunner` 等の既存方式がある場合は必ずそれを再利用する。

推奨する新規ファイル名:

```text
app/src/main/java/net/sasasin/sreader/command/ContentCanonicalizeCommand.java
app/src/main/java/net/sasasin/sreader/service/ContentCanonicalizationMaintenanceService.java
app/src/main/java/net/sasasin/sreader/repository/ContentCanonicalizationMaintenanceRepository.java
app/src/main/java/net/sasasin/sreader/domain/ContentCanonicalizationPlan.java
app/src/main/java/net/sasasin/sreader/domain/ContentCanonicalizationResult.java

app/src/test/java/net/sasasin/sreader/command/ContentCanonicalizeCommandTest.java
app/src/test/java/net/sasasin/sreader/service/ContentCanonicalizationMaintenanceServiceTest.java
app/src/test/java/net/sasasin/sreader/repository/ContentCanonicalizationMaintenanceRepositoryTest.java
```

既存 package が `cli`、`runner` 等なら、その規約へ合わせてよい。名前を変更した場合は PR 説明で対応関係を示す。

再利用対象:

```text
app/src/main/java/net/sasasin/sreader/service/ArticleUrlCanonicalizer.java
app/src/main/java/net/sasasin/sreader/service/ContentTextFileWriter.java
```

ファイル削除について既存の専用 component がなければ、maintenance service から直接 `java.nio.file.Files` を使わず、テスト可能な小さな component/interface を追加してよい。

例:

```text
app/src/main/java/net/sasasin/sreader/service/ContentTextFileStore.java
```

ただし通常の text export 実装との重複を避ける。

---

# データ統合モデル

## canonical group

各 `content_header` の現在の `canonical_url` を `ArticleUrlCanonicalizer.canonicalize()` へ通し、その結果文字列で group 化する。

例:

```text
group key:
https://comemo.nikkei.com/n/n8fcec9d3d10f

members:
ID A / canonical_url=?gs=aaa
ID B / canonical_url=?gs=bbb
ID C / canonical_url=?gs=ccc
```

member が1件でも、現在値と正規化後値が異なる場合は「単独 rename 対象」である。

member が複数なら「merge 対象」である。

変更不要な group は、member が1件かつ現在の `canonical_url` が正規化済みの場合。

## 統合先 ID

統合後の survivor ID は必ず次とする。

```java
HashIds.md5(normalizedCanonicalUrl)
```

既存 member のどれかがすでにこの ID を持つ場合は、それを survivor として再利用する。

存在しない場合は、survivor row を新しい ID で作成するか、選択した既存 row の PK を安全に変更する。外部キー・cascade・export path への影響を考慮し、原則として「新 survivor row を作成し、子レコードを付け替え、旧 row を削除する」方式を推奨する。

単純な PK UPDATE は、関連 table の FK が `ON UPDATE CASCADE` でない可能性があるため採用しない。

## survivor の field 選択規則

決定論的にすること。推奨規則:

### `canonical_url`

```text
normalized canonical URL
```

### `id`

```text
MD5(normalized canonical URL)
```

### `source_url`

以下の優先順で1件選ぶ。

1. `canonical_url` と異なる元 URL がある場合、最も古い `created_at` の row の `source_url`
2. それ以外は最も古い `created_at` の row の `source_url`
3. tie-breaker は `id` の昇順

### `fetch_url`

以下の優先順で選ぶ。

1. 最も新しい `updated_at`
2. `updated_at` が同じなら最も新しい `created_at`
3. tie-breaker は `id` の昇順

### `feed_url_id`

現行 schema が1記事1 feed の場合、最も古い `created_at` の row の値を維持する。異なる feed 由来 member が存在する場合は dry-run/apply 結果に conflict count として表示する。

この PR では記事と複数 feed の多対多 schema へ変更しない。

### `title`

1. null/blank でない値を優先
2. 最も新しい `updated_at` の値
3. tie-breaker は `id` の昇順

### `published_at`

null でない最も古い日時を推奨する。別規則を採用する場合はテストと PR 説明に明記する。

### `feed_text`

1. null/blank でない値を優先
2. 文字数が長い値を優先
3. tie-breaker は新しい `updated_at`

### timestamps

- `created_at`: member 中の最古値
- `updated_at`: maintenance 適用時刻、または member 中の最新値との max

既存 domain/schema の実際の列を確認し、存在しない項目は無視する。

---

# content_full_text の統合規則

## 選択規則

member に紐づく `content_full_text` のうち、以下の優先順で survivor に保持する。

1. `full_text` が null/blank でない
2. `full_text` の文字数が長い
3. `extracted_at` が新しい
4. `created_at` が古い
5. tie-breaker は `id` の昇順

実際の schema に `extracted_at` 等がなければ、存在する timestamp で決定論的な順序を作る。

## ID と外部キー

`content_full_text` の主キー生成規則が現行コードにある場合は、その規則を維持する。

- survivor の `content_header_id` に付け替える。
- unique constraint に衝突する場合は、選択した1件だけを残す。
- 非選択の本文レコードは survivor の本文が確実に保存された後に削除する。
- 本文を失う可能性がある場合は transaction を rollback する。

## 受け入れ条件

- 統合前に有効本文が1件以上あれば、統合後にも有効本文が1件存在する。
- 空本文が有効本文を上書きしない。
- 再実行しても本文選択が変わらない。

---

# content_text_file_export と物理ファイルの扱い

## 方針

統合後 survivor の canonical URL と ID で、テキストファイルを再生成可能な状態にする。

推奨手順:

1. group member の `content_text_file_export` 行を取得する。
2. survivor へ本文を統合する。
3. member に紐づく export history を削除する。
4. transaction commit 後、旧 member ID に対応する物理 `.txt` ファイルを削除する。
5. survivor を未出力状態にして、通常の exporter が次回 job で再出力できるようにする。

または maintenance command 内で survivor file を直接出力してから export history を記録してもよいが、通常 exporter の既存ロジックを再利用すること。

## ファイル path

現行仕様:

```text
<output-dir>/<content_header.id>.txt
```

旧 member ごとに以下を削除対象とする。

```text
<old-content-header-id>.txt
```

survivor ID と同じファイルは、内容が古い可能性があるため再生成対象にする。

## DB と filesystem の transaction 境界

DB transaction と filesystem operation は単一 ACID transaction にできない。以下の安全策を取る。

- DB の group 統合を先に transaction commit する。
- 削除予定 file path を結果として保持する。
- commit 後にファイルを削除する。
- ファイルが存在しない場合は成功扱いにする。
- ファイル削除失敗は warning/error として集計し、コマンドを non-zero exit にするか、明確な partial failure を報告する。
- DB rollback 時にはファイルを削除しない。
- survivor の export history を削除しておけば、通常 exporter で再生成可能である。

ファイル削除失敗後にコマンドを再実行しても、残存 orphan file を検出・削除できる設計が望ましい。

## dry-run

`--dry-run` では以下を一切行わない。

- INSERT
- UPDATE
- DELETE
- export history 変更
- 物理ファイルの作成・削除・rename

削除予定 path の件数と、先頭数件の相対 path を表示してよい。

---

# Repository API

推奨責務:

```java
List<CanonicalizationCandidate> findCandidates(String host, int limit, ...);
CanonicalizationGroup loadGroup(String normalizedCanonicalUrl);
MergeResult mergeGroup(CanonicalizationPlan plan);
```

巨大 table を JVM へ全件ロードしないこと。

候補探索の例:

- `canonical_url` が host に一致する行を keyset pagination で読む。
- Java 側 canonicalizer で normalized URL を計算する。
- normalized URL が変わる行だけ候補にする。
- group member は必要時に DB から取得する。

ただし DB 内で正規化規則を二重実装しない。正規化の source of truth は `ArticleUrlCanonicalizer` とする。

## transaction

- canonical group 単位、または小さな batch 単位で transaction を分ける。
- 1 group の統合は atomic にする。
- 長時間 table 全体を lock しない。
- 必要に応じて `SELECT ... FOR UPDATE` を使い、同一 group の並行変更を防ぐ。
- scheduler を無効化して実行する前提でも、repository 層では整合性制約を尊重する。

---

# コマンド出力

## dry-run summary

標準出力に少なくとも以下を表示する。

```text
mode=dry-run
scanned_rows=...
unchanged_rows=...
rename_groups=...
merge_groups=...
duplicate_rows_to_delete=...
full_text_rows_to_delete=...
export_history_rows_to_delete=...
files_to_delete=...
feed_conflict_groups=...
```

代表例を最大10件程度表示する。

```text
canonical_url=...
members=7
survivor_id=...
selected_full_text_id=...
files_to_delete=7
```

URL に秘密 query が含まれる可能性を考慮し、`--verbose` がない場合は query を省略・マスクしてもよい。ただし COMEMO の検証に必要な canonical path は確認可能にする。

## apply summary

```text
mode=apply
processed_groups=...
merged_groups=...
renamed_groups=...
deleted_content_headers=...
deleted_full_texts=...
deleted_export_histories=...
deleted_files=...
missing_files=...
failed_files=...
failed_groups=...
```

1件でも DB group 統合に失敗した場合、コマンドは non-zero exit とする。ファイル削除失敗の exit policy も明示し、テストする。

---

# 必須テスト

## 1. dry-run 無変更テスト

事前に重複 group を作る。

実行後:

- DB row count が変化しない。
- field 値が変化しない。
- export history が変化しない。
- filesystem が変化しない。
- summary 件数だけが正しい。

## 2. COMEMO 7重複統合テスト

以下のように7件を作る。

```text
https://comemo.nikkei.com/n/n8fcec9d3d10f?gs=7b2f2a0bda7f
https://comemo.nikkei.com/n/n8fcec9d3d10f?gs=733b1e958c41
https://comemo.nikkei.com/n/n8fcec9d3d10f?gs=4ff17ebffc5d
https://comemo.nikkei.com/n/n8fcec9d3d10f?gs=4ac3efca66fa
https://comemo.nikkei.com/n/n8fcec9d3d10f?gs=6092939ded14
https://comemo.nikkei.com/n/n8fcec9d3d10f?gs=0eb324214355
https://comemo.nikkei.com/n/n8fcec9d3d10f?gs=f5447f4b4ea2
```

適用後:

```text
content_header: 1件
canonical_url: https://comemo.nikkei.com/n/n8fcec9d3d10f
id: MD5(canonical_url)
fetch_url: 選択規則どおりの最新値
source_url: 選択規則どおりの初回値
```

有効本文が残り、旧 export history が整理されること。

## 3. 単独 rename テスト

member が1件だが `canonical_url` に `gs` が付いているケース。

- survivor ID が canonical URL の MD5 になる。
- 本文が維持される。
- 旧ファイルが削除対象になる。
- 新しい export が可能になる。

## 4. 既に survivor ID が存在するケース

- canonical ID の row と `gs` 付き row が共存する。
- canonical row を survivor として再利用する。
- unique constraint violation が起きない。

## 5. 本文選択テスト

- blank 本文と non-blank 本文 → non-blank を選ぶ。
- 短い本文と長い本文 → 長い本文を選ぶ。
- 同じ長さ → timestamp/tie-breaker 規則どおり。

## 6. ファイル処理テスト

temporary directory を使用する。

- 旧7ファイルが削除される。
- 存在しない旧ファイルはエラーにしない。
- 削除権限エラー等をシミュレートし、summary と exit status が正しい。
- dry-run ではファイルを削除しない。
- survivor が再出力対象になる。

## 7. 冪等性テスト

`--apply` を2回実行する。

2回目:

```text
merged_groups=0
renamed_groups=0
deleted_content_headers=0
```

DB と filesystem に追加変更がないこと。

## 8. host filter テスト

`--host comemo.nikkei.com` 実行時に `example.com` の候補を変更しない。

## 9. transaction rollback テスト

1 group の途中で repository exception を発生させる。

- その group の DB 変更がすべて rollback される。
- その group の物理ファイルは削除されない。
- 他 group を続行するか中止するかは明示的 policy とし、テストする。

## 10. feed conflict テスト

同じ canonical URL に異なる `feed_url_id` がある場合:

- dry-run に conflict として表示する。
- apply 時の選択規則が決定論的である。
- 多対多 schema へ暗黙に変更しない。

---

# 実装後に実行するテスト

```bash
docker compose config
docker compose up -d postgres
docker compose run --rm flyway migrate
docker compose run --rm maven mvn test
docker compose run --rm maven mvn clean verify
docker compose run --rm maven mvn -pl app -am package -DskipTests
```

CLI の end-to-end 確認:

```bash
docker compose run --rm app \
  --sreader.scheduler.enabled=false \
  content canonicalize \
  --host comemo.nikkei.com \
  --dry-run
```

テスト用 DB/data directory に対する apply:

```bash
docker compose run --rm app \
  --sreader.scheduler.enabled=false \
  content canonicalize \
  --host comemo.nikkei.com \
  --limit 10 \
  --apply
```

同じコマンドを再実行し、冪等であることを確認する。

---

# 変更してよい箇所

- 既存 CLI command registration に必要なファイル
- maintenance 用 command/service/repository/domain/result model
- `ArticleUrlCanonicalizer` の再利用に必要な最小変更
- text export file store 抽象化のための最小 refactoring
- maintenance command の unit/integration/E2E test
- README の maintenance 手順、backup、dry-run/apply 説明
- 必要なら専用 index を追加する新規 Flyway migration
  - ただし実測または query plan で必要性を説明すること
  - schema 変更時は jOOQ sources を再生成すること

# 変更してはいけない箇所

- `ArticleUrlCanonicalizer` と別の URL 正規化規則を SQL や command 内へ複製しない。
- `gs` 以外の query parameter を無条件に削除しない。
- `--apply` をデフォルト動作にしない。
- dry-run で DB または filesystem を変更しない。
- すべての既存データを1 transaction で処理しない。
- scheduler を自動的に disable/enable する外部操作を command から行わない。
- feed URL、scheduler、HTTP fetch、full text extraction の通常動作を変更しない。
- 記事と feed の関係を多対多 schema へ拡張しない。
- unrelated dependency upgrade や大規模 refactoring を混ぜない。
- Flyway migration で重複データを直接削除しない。
- backup を command 内から自動作成したと偽らない。backup は運用者の明示作業とする。
- 物理ファイル削除を DB commit より先に行わない。

# 運用安全要件

README に以下を明記する。

1. 実行前に scheduler を停止または無効化する。
2. 実行前に PostgreSQL backup を取得する。
3. 最初に `--host comemo.nikkei.com --dry-run` を実行する。
4. summary と代表例を確認する。
5. 初回 apply は `--limit` または小 batch で実行する。
6. apply 後に dry-run を再実行し、候補が0になることを確認する。
7. text exporter を起動し、survivor file が再生成されることを確認する。
8. orphan file が残っていないか確認する。

backup 例は repository README の既存方式に合わせる。

# PR 完了条件

- [ ] `content canonicalize` 相当の明示的 CLI が存在する。
- [ ] dry-run がデフォルト安全動作である、または mode 必須である。
- [ ] `--host`, `--batch-size` が使用可能である。
- [ ] canonicalization source of truth は `ArticleUrlCanonicalizer` である。
- [ ] group 単位で atomic に統合する。
- [ ] survivor ID は `MD5(normalized canonical URL)` である。
- [ ] 有効な本文を失わない決定論的選択規則がある。
- [ ] export history と物理ファイルの整合性を扱う。
- [ ] DB rollback 前に物理ファイルを削除しない。
- [ ] dry-run は DB・filesystem を変更しない。
- [ ] 2回目の apply が no-op になる。
- [ ] host filter が機能する。
- [ ] failure count と non-zero exit policy が明確である。
- [ ] COMEMO 7重複の統合テストがある。
- [ ] PostgreSQL integration test と temporary filesystem test がある。
- [ ] `mvn test` が成功する。
- [ ] `mvn clean verify` が成功し、JaCoCo branch coverage gate 90% を満たす。
- [ ] README に backup、scheduler disable、dry-run、apply、再検証手順がある。

# PR 説明に記載する内容

1. 先行 canonical URL PR への依存
2. 対象となる既存重複の例
3. survivor 選択規則
4. full text 選択規則
5. export history・物理ファイルの処理順
6. transaction 境界
7. dry-run summary の実例
8. apply summary の実例
9. 実行したテストと結果
10. 本番適用手順と rollback/復旧上の注意

# Definition of Done

テスト fixture 上で、同一 COMEMO 記事の `gs` 違い7件を1件へ統合でき、有効本文が保持され、旧 export history と旧物理ファイルが整理され、通常 exporter によって canonical ID のファイルを再生成できること。さらに、同じ apply を再実行しても変更が発生しないこと。
