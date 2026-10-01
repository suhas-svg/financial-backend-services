# Releases and versioning

The whole product (both services, the frontend and the Helm charts) carries **one semantic
version**, recorded in [`.release-please-manifest.json`](../../.release-please-manifest.json).
While the product is in controlled beta it stays at `0.x`.

## How a release happens

1. Merge PRs to `main` with [Conventional Commit](https://www.conventionalcommits.org/) titles
   (the squash commit uses the PR title):
   - `feat(scope): …` bumps the minor version (0.x), `fix(scope): …` the patch version.
   - `feat!:` or a `BREAKING CHANGE:` footer marks a breaking change.
   - `docs:`, `chore:`, `ci:`, `test:` do not trigger a release.
2. After `Required Acceptance` passes on `main`, the **Release PR and Tag** job keeps one
   release PR open (`chore: release X.Y.Z`). It updates `CHANGELOG.md` and every version below.
3. Merging the release PR tags `vX.Y.Z`, creates the GitHub release, and the
   **Publish Release Image** jobs push:
   - `ghcr.io/suhas-svg/financial-account-service:X.Y.Z` and `:sha-<commit>`
   - `ghcr.io/suhas-svg/financial-transaction-service:X.Y.Z` and `:sha-<commit>`

   The job summary lists each image digest. Deploy by digest (`image@sha256:…`) where you can;
   a version tag is a pointer, a digest is the exact bytes that passed CI.

## Where the version lives

Kept in step by release-please (lines marked `x-release-please-version`, or by JSON path):

| File | Field |
| --- | --- |
| `pom.xml`, `account-service/pom.xml`, `transaction-service/pom.xml` | project `<version>` |
| `frontend/package.json`, `frontend/package-lock.json` | `version` |
| `infrastructure/helm/*/Chart.yaml` | `appVersion` |
| `infrastructure/helm/*/values*.yaml` | `image.tag` |

Running services report it at `/actuator/info` (`build.version`, from Spring Boot build-info).
Every image carries `org.opencontainers.image.version` and `org.opencontainers.image.revision`
(the commit SHA). CI images built for pull requests are tagged with the commit SHA.

## One-time setup

- Add a `RELEASE_PLEASE_TOKEN` secret: a fine-grained PAT or GitHub App token with
  *Contents* and *Pull requests* write access. Release PRs opened with the default
  `GITHUB_TOKEN` do not trigger workflows, so they could never pass `Required Acceptance`.
- Settings → Actions → General → allow GitHub Actions to create pull requests.
- `0.1.0` is the baseline (everything up to #85). Commits after it go into the next release.
