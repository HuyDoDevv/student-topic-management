# RF-17 Final Audit: `.project-kit` Layout Consolidation

> Historical audit only. Kết quả dưới đây mô tả trạng thái sau feature 007, trước khi
> repository được tổ chức lại thành `.agents/`, `.specify/`, `specs/` và `workspace/`.

**Audit date**: 2026-10-19

**Branch**: `feature/007-project-kit-layout`

**Scope**: RF-00 through RF-17

## Outcome

The SpecKit and AI-supporting project artifacts are consolidated under `.project-kit/`. The migration preserves the repository's existing governance gates and does not modify application code under `src/**`.

The structural and static acceptance checks pass. The full Maven suite is not green in this environment because 16 MySQL-backed tests require the missing `TEST_DATABASE_URL` environment variable; this is recorded as an environment-blocked regression result, not as a test pass.

## Final canonical structure

```text
.project-kit/
├── agents/skills/          # canonical skill instructions
├── decisions/history/      # migration and audit history
├── features/               # canonical feature artifacts
├── integrations/           # integration configuration and manifests
├── standards/              # constitution and governance standards
├── state/                  # local active-feature state
├── templates/              # SpecKit templates
├── tools/powershell/       # canonical automation scripts
└── workflows/              # workflow definitions and registry
```

`.agents/skills/*/SKILL.md` remains only as the discovery layer. Each of the 10 adapters points to its canonical instruction file in `.project-kit/agents/skills/` and explicitly states that it does not grant permissions or override the root `AGENTS.md`.

## Permission and behavior preservation

- The root `AGENTS.md` still defines `READ ONLY` as the default mode.
- The exact implementation gate remains `IMPLEMENT APPROVED PLAN`.
- The precedence of `AGENTS.md` over skills, workflows, plans, checklists, and historical approvals is unchanged.
- The only approved change in the source-location block of `AGENTS.md` replaces `.specify/` and `specs/` with `.project-kit/` and describes `.agents/` as discovery adapters.
- No file under `src/**` is changed.
- No authentication, authorization, security configuration, API, route, database schema, migration, dependency, build, or deployment behavior is changed.

## Migration inventory

- Migrated 20 tracked `.specify/` files into the canonical `.project-kit/` layout.
- Migrated six feature directories containing 53 tracked files from `specs/` into `.project-kit/features/`.
- Migrated 10 canonical skills and retained 10 lightweight discovery adapters under `.agents/skills/`.
- Removed the legacy `.specify/` and `specs/` trees after canonical validation.
- Final `.project-kit/` inventory after this report: 86 files.

## Static and structural verification

| Check | Result | Evidence |
|---|---|---|
| Canonical layout inventory | PASS | Expected standards, templates, tools, workflows, integrations, skills, features, state, and decisions paths exist. |
| Manifest integrity | PASS | 22 of 22 declared canonical path checks passed. |
| Canonical feature content | PASS | All six migrated feature-directory hashes match the locked migration baseline. |
| Local state integrity | PASS | `.project-kit/state/feature.json` SHA-256 is `21da8be1512977842d9c004ebe8d179608d5707b6d3359e7f92f8aadf39c8c52`. |
| JSON and canonical-path hygiene | PASS | Canonical JSON parses and active references resolve inside `.project-kit/`; no active legacy `.specify/` or `specs/` dependency remains. |
| Skill discovery adapters | PASS | 10 adapters have canonical frontmatter and resolve to 10 canonical skill files. |
| PowerShell canonical smoke | PASS | Canonical prerequisite resolution succeeds from `.project-kit/tools/powershell/`. |
| Feature creation dry run | PASS | `create-new-feature.ps1 -DryRun` created no files and did not mutate local state. |
| Application source isolation | PASS | `git diff --name-only -- src` returns no paths. |
| Patch whitespace validation | PASS | `git diff --check` reports no errors. |

### Locked canonical feature hashes

| Feature | SHA-256 |
|---|---|
| `001-topic-registration` | `2bcb384ad7f8f2c034cb6d19829914a900c302e739bc73a9a17b400a92474845` |
| `002-review-results` | `5937e3042a53dad8cd78c6fb99e2edc7798c2a4275887ec2f1c2592e683289af` |
| `003-notifications` | `c7313e45eae8b8cc98fc27200f8e4ae3ad9ebc0ec58a0ba35165bd20d8678d52` |
| `004-department-management` | `f88a442f24ac2a928070409578eb1a79acff156ed490dd95b8b25667cf0ce07a` |
| `005-department-period-scope` | `85a3043b44ed0ab77ec85f3fdf88b4bdf5250285854ab388bd00355699604546` |
| `006-feedback-fixes` | `0848596032d163f21e71cfda18c01a3c9df25ef6db45788f9c945e8d11d69f2e` |

## Maven regression result

Command:

```bash
env -u JAVA_HOME ./mvnw test
```

Runtime: Java 21.0.12.1.

```text
Tests run: 424
Failures: 0
Errors: 16
Skipped: 0
BUILD FAILURE
```

The 16 errors are isolated to:

- `AuthorizationMatrixTest`: 5 errors
- `PeriodContractTest`: 11 errors

The first initialization error in each class has the same root cause:

```text
java.lang.IllegalStateException: TEST_DATABASE_URL is required for MySQL tests
```

The subsequent `NoClassDefFoundError` entries are cascading class-initialization failures. Therefore, the Maven regression suite is **environment-blocked** because the required test database URL was not supplied. RF-17 does not change test infrastructure or application code, so no test or source fix is included.

## Remaining verification gap and risk

- Windows PowerShell 5.1 compatibility was not executed because this audit ran on macOS with PowerShell Core. The canonical PowerShell Core smoke checks pass.
- Database-backed behavior remains unverified by the full Maven suite in this environment until a safe test-only `TEST_DATABASE_URL` is provided.
- No commit, push, merge, rebase, cherry-pick, stash, or pull-request operation was performed.
