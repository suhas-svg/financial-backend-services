# Development Environment Setup

This document describes how to set up your development environment with code quality and security tools.

## Prerequisites

- Java 22
- Maven 3.9+
- Git
- Docker (for integration tests)

## Code Quality Tools Setup

### 1. Pre-commit Hooks

Pre-commit hooks help maintain code quality by running checks before each commit.

#### Installation

**On macOS/Linux:**
```bash
# Install pre-commit
pip install pre-commit
# or
brew install pre-commit

# Install hooks
pre-commit install
```

**On Windows:**
```powershell
# Install pre-commit
pip install pre-commit

# Install hooks
pre-commit install
```

#### Manual Setup
If you prefer to run the script:
```bash
# Make script executable (Linux/macOS)
chmod +x .github/scripts/setup-pre-commit.sh

# Run setup script
./.github/scripts/setup-pre-commit.sh
```

### 2. IDE Configuration

#### IntelliJ IDEA
1. Install plugins:
   - SonarLint
   - SpotBugs
   - CheckStyle-IDEA
   - PMD

2. Configure code style:
   - Import Google Java Style: `File > Settings > Editor > Code Style > Java`
   - Enable format on save: `File > Settings > Tools > Actions on Save`

#### VS Code
1. Install extensions:
   - Extension Pack for Java
   - SonarLint
   - Checkstyle for Java

### 3. Local Code Quality Checks

Run these commands before committing:

```bash
cd account-service

# Compile and run tests
./mvnw clean compile test

# Generate coverage report
./mvnw jacoco:report

# Run static analysis
./mvnw pmd:check spotbugs:check

# Check license headers
./mvnw license:check-file-header

# Run all quality checks
./mvnw clean verify
```

### 4. SonarCloud Setup (Optional for local development)

1. Create account at https://sonarcloud.io
2. Generate token: `Account > Security > Generate Tokens`
3. Set environment variable:
   ```bash
   export SONAR_TOKEN=your_token_here
   ```
4. Run analysis:
   ```bash
   cd account-service
   ./mvnw sonar:sonar
   ```

## Code Quality Standards

### Coverage Requirements
- Minimum line coverage: 80%
- Minimum branch coverage: 70%

### Code Style
- Follow Google Java Style Guide
- Use meaningful variable and method names
- Add JavaDoc for public APIs
- Keep methods under 50 lines
- Keep classes under 500 lines

### Security Requirements
- No hardcoded secrets or passwords
- Validate all user inputs
- Use parameterized queries for database access
- Follow OWASP security guidelines

## Pre-commit Hook Details

The following checks run automatically on commit:

### Code Quality
- **Java formatting**: Google Java Format
- **Static analysis**: PMD, SpotBugs
- **License headers**: Apache 2.0 license check
- **Compilation**: Maven compile check
- **Tests**: Unit test execution

### Security
- **Secret detection**: TruffleHog
- **Security analysis**: Semgrep with security rules
- **Dependency check**: OWASP dependency vulnerabilities

### General
- **File formatting**: Trailing whitespace, end-of-file
- **YAML/JSON**: Syntax validation
- **Docker**: Dockerfile linting with Hadolint
- **Markdown**: Linting and formatting

## Troubleshooting

### Pre-commit Hook Failures

If pre-commit hooks fail:

1. **View detailed output:**
   ```bash
   pre-commit run --all-files
   ```

2. **Skip hooks temporarily (not recommended):**
   ```bash
   git commit --no-verify -m "commit message"
   ```

3. **Fix specific issues:**
   - Format code: Run Google Java Format
   - Fix security issues: Review Semgrep/TruffleHog output
   - Update dependencies: Check OWASP dependency report

### Maven Build Issues

1. **Clean and rebuild:**
   ```bash
   ./mvnw clean compile
   ```

2. **Update dependencies:**
   ```bash
   ./mvnw dependency:resolve
   ```

3. **Skip tests temporarily:**
   ```bash
   ./mvnw clean package -DskipTests
   ```

### Coverage Issues

1. **Generate detailed report:**
   ```bash
   ./mvnw jacoco:report
   open target/site/jacoco/index.html
   ```

2. **Exclude generated code:**
   - Update `sonar.exclusions` in pom.xml
   - Add `@Generated` annotation to generated classes

## Continuous Integration

The CI/CD pipeline runs the same quality checks:

1. **Code compilation and testing**
2. **Security scanning** (Trivy, OWASP, Semgrep)
3. **Code quality analysis** (SonarCloud, CodeQL)
4. **Coverage reporting** (JaCoCo)
5. **Container security scanning**

All checks must pass before code can be merged to main branch.

## Resources

- [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html)
- [OWASP Top 10](https://owasp.org/www-project-top-ten/)
- [SonarCloud Documentation](https://docs.sonarcloud.io/)
- [JaCoCo Documentation](https://www.jacoco.org/jacoco/trunk/doc/)
- [Pre-commit Documentation](https://pre-commit.com/)