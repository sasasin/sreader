# Test Coverage Baseline

## Measurement metadata

| Item | Value |
|---|---|
| Measured at | 2026-07-12 |
| Commit | `a067d60bc1eb07e8b7ae19ea55b9ac6013710b8c` |
| Java | `25.0.3` (Eclipse Adoptium) |
| Maven | `3.9.16` |
| JaCoCo | `0.8.15` |
| Command | `docker compose run --rm maven mvn -B -V clean verify` |

The PostgreSQL database was started and migrated before measurement with the following commands:

```sh
docker compose up -d postgres
docker compose exec -T postgres pg_isready -U sreader -d sreader
docker compose run --rm flyway migrate
```

## Scope

- Included: hand-written production code under `app/src/main/java`
- Excluded: `net/sasasin/sreader/jooq/**` (checked-in jOOQ generated sources)
- No hand-written production package was excluded.

The JaCoCo XML report was checked to confirm that the excluded jOOQ package is absent. Its
bundle totals also match the top-level HTML report.

## Test results

| Tests | Failures | Errors | Skipped |
|---:|---:|---:|
| 95 | 0 | 0 | 0 |

## Results

| Metric | Covered | Missed | Ratio |
|---|---:|---:|---:|
| Branches | 383 | 280 | 57.77% |
| Instructions | 4,828 | 1,862 | 72.17% |
| Lines | 1,060 | 434 | 70.95% |
| Methods | 195 | 44 | 81.59% |
| Classes | 71 | 1 | 98.61% |

The JaCoCo HTML bundle summary rounds branch coverage to 57% and reports 280 missed of 663
branches, which agrees with the XML totals above.

## Largest branch coverage gaps by class

| Rank | Class | Covered branches | Missed branches | Ratio |
|---:|---|---:|---:|---:|
| 1 | `net.sasasin.sreader.service.PlaywrightHtmlSource` | 0 | 44 | 0.00% |
| 2 | `net.sasasin.sreader.service.FeedTomlService` | 107 | 43 | 71.33% |
| 3 | `net.sasasin.sreader.service.FullTextProbeService` | 26 | 22 | 54.17% |
| 4 | `net.sasasin.sreader.cli.ProbeFeedCommand` | 11 | 21 | 34.38% |
| 5 | `net.sasasin.sreader.runner.FeedReaderCommandRunner` | 5 | 17 | 22.73% |
| 6 | `net.sasasin.sreader.service.FeedEntryPicker` | 34 | 17 | 66.67% |
| 7 | `net.sasasin.sreader.service.HttpFetchService` | 0 | 14 | 0.00% |
| 8 | `net.sasasin.sreader.config.FeedReaderProperties$Playwright` | 13 | 13 | 50.00% |
| 9 | `net.sasasin.sreader.cli.ProbeOutputWriter` | 4 | 12 | 25.00% |
| 10 | `net.sasasin.sreader.cli.UrlValidator` | 8 | 8 | 50.00% |

## Largest branch coverage gaps by package

| Package | Covered branches | Missed branches | Ratio |
|---|---:|---:|---:|
| `net.sasasin.sreader.service` | 276 | 180 | 60.53% |
| `net.sasasin.sreader.cli` | 47 | 57 | 45.19% |
| `net.sasasin.sreader.runner` | 5 | 17 | 22.73% |
| `net.sasasin.sreader.config` | 27 | 15 | 64.29% |
| `net.sasasin.sreader.repository` | 11 | 5 | 68.75% |
| `net.sasasin.sreader.domain` | 15 | 4 | 78.95% |
| `net.sasasin.sreader.scheduler` | 2 | 2 | 50.00% |

## Analysis and next priorities

The baseline is 32.23 percentage points below the future branch-coverage target. The largest
gaps are the unexercised Playwright and HTTP source paths, followed by feed TOML validation and
probe-command error paths. The current test suite already covers almost every class, so the
primary opportunity is exercising alternative branches rather than adding superficial class
coverage.

1. Add focused tests for `PlaywrightHtmlSource` and `HttpFetchService`, including failure and
   timeout paths.
2. Cover remaining validation, conflict, and error branches in `FeedTomlService` and probe
   commands.
3. Cover scheduling and runner decision paths in `FeedReaderCommandRunner` and the scheduler.

## Target

The planned quality gate is a bundle-level branch covered ratio of at least 90%:

```text
BRANCH COVEREDRATIO >= 0.90
```

This baseline change does not enforce the gate; no `jacoco:check` execution or coverage threshold
is configured.

## Report and CI artifact locations

Local report outputs after `mvn clean verify`:

- `app/target/jacoco.exec`
- `app/target/site/jacoco/index.html`
- `app/target/site/jacoco/jacoco.xml`
- `app/target/site/jacoco/jacoco.csv`

CI stores the `test-and-coverage-${github.sha}` artifact for 14 days. It contains
`app/target/site/jacoco/` and `app/target/surefire-reports/`; upload runs with `if: always()` and
warns rather than failing when no report files exist.
