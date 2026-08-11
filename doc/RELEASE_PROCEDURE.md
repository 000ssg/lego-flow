# Lego Flow — Release Procedure

This document describes the step-by-step process for releasing a new version of the Lego Flow framework to GitHub Packages and creating a GitHub Release.

---

## Pre-Release Checklist

### 1. Verify Code State
- [ ] All tests pass: `./gradlew clean test --rerun-tasks`
- [ ] Maven build succeeds: `mvn clean install -DskipTests`
- [ ] No `SNAPSHOT` references in child modules that should be fixed versions
- [ ] Root `pom.xml` version reflects the target release version (e.g., `0.1.0`, not `0.1.0-SNAPSHOT`)
- [ ] `gradle.properties` `legoFlowVersion` matches the target release version
- [ ] Child module `pom.xml` files inherit version from parent correctly

### 2. Prepare Version
- [ ] Update root `pom.xml` `<version>` to the release version (e.g., `0.1.0`)
- [ ] Update `gradle.properties` `legoFlowVersion` to the release version
- [ ] Verify all child `pom.xml` files use `${project.version}` (not hardcoded)
- [ ] Update `README.md` version badge to match
- [ ] Update `README.md` test count if changed

### 3. Verify Release Workflow
- [ ] `.github/workflows/release.yml` has `permissions: contents: write` and `packages: write`
- [ ] Workflow uses `PACKAGE_PAT` secret for publishing
- [ ] No YAML multi-line command folding bugs (no backslash continuations in `run:` blocks)

### 4. Pre-Release Build
```bash
# Clean build with all tests
./gradlew clean build --parallel --no-daemon -x :benchmarks:test -x :interop-tests:test

# Verify publish works locally
./gradlew publishToMavenLocal --parallel --no-daemon
```

---

## Release Steps

### Step 1: Tag and Push
```bash
# Tag the release (replace with actual version)
git tag -a v0.1.0 -m "Release v0.1.0"

# Push tag to trigger the release workflow
git push origin v0.1.0
```

### Step 2: Monitor GitHub Actions
- Watch the "Build & Publish" workflow run at `https://github.com/000ssg/lego-flow/actions`
- Verify the following steps succeed:
  1. Checkout repository
  2. Set up JDK 25
  3. Build & test
  4. Publish to GitHub Packages
  5. Create GitHub Release

### Step 3: Verify Published Artifacts
```bash
# Check GitHub Packages
# Visit: https://github.com/000ssg/lego-flow/packages

# Verify a specific artifact can be resolved:
mvn dependency:get -Dartifact=ssg:lego-flow-blocks:0.1.0 \
  -DremoteRepositories=https://maven.pkg.github.com/000ssg/lego-flow
```

### Step 4: Bump to Next Development Version
After a successful release, bump versions for the next development cycle:
```bash
# Update pom.xml
# Change <version>0.1.0</version> to <version>0.2.0-SNAPSHOT</version>

# Update gradle.properties
# Change legoFlowVersion=0.1.0 to legoFlowVersion=0.2.0-SNAPSHOT

# Update README.md badge
# Change 0.1.0 to 0.2.0-SNAPSHOT

# Commit and push
git add pom.xml gradle.properties README.md
git commit -m "Bump version to 0.2.0-SNAPSHOT post-release"
git push origin master
```

---

## Known Issues & Findings

### YAML Multi-Line Command Folding Bug (CRITICAL)
GitHub Actions YAML folding converts newlines to spaces but preserves backslashes literally.
**Never use backslash line continuations** in `run:` blocks in `.github/workflows/*.yml`.
Always write multi-line shell commands on a single line. See `AGENTS.md` for details.

### Missing Permissions
The release workflow requires `packages: write` permission. Without it, publishing to
GitHub Packages will fail with a 401/403 error. Ensure the workflow has:
```yaml
permissions:
  contents: write
  packages: write
```

### Secret Configuration
The workflow uses `PACKAGE_PAT` secret for publishing. Ensure this GitHub secret is
configured in the repository settings. Fall back to `GITHUB_TOKEN` is not reliable
for cross-repository package access.

### Benchmarks Exclusion
The `benchmarks` and `interop-tests` modules are excluded from the release build
(`-x :benchmarks:test -x :interop-tests:test`) because they have pre-existing structural
issues and are not part of the published artifact set.

---

## Troubleshooting

### "Unknown lifecycle phase" or "Task not found" in CI
→ YAML folding bug. Check for backslash continuations in `run:` blocks.

### 401/403 on publish
→ Missing `packages: write` permission or stale `PACKAGE_PAT` secret.

### Child module versions don't match
→ Verify child `pom.xml` files inherit from parent via `<parent>` and use `${project.version}`.

### Gradle build fails but Maven works (or vice versa)
→ Check that both `pom.xml` and `build.gradle.kts` have consistent module declarations
and dependency versions. Cross-reference `settings.gradle.kts` module names.

---

## Release Order (Multi-Project)

Lego Flow has no external project dependencies. It can be released independently.

If MDB-SQL depends on lego-flow artifacts, ensure lego-flow is released **before**
MDB-SQL to provide stable Maven coordinates for cross-project dependency resolution.

---

**Last Updated**: 2026-08-11
