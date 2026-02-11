# Frontend Build Fix

## Issue

The frontend build (`buildFrontend` task) was failing in CI environments with pnpm-related errors, even though it worked correctly in local development environments.

**Error messages encountered:**
1. `ERR_PNPM_INVALID_WORKSPACE_CONFIGURATION packages field missing or empty`
2. `ERR_PNPM_BAD_PM_VERSION This project is configured to use v10.22.0 of pnpm. Your current pnpm is v9.0.0`

## Root Cause

The gradle-node-plugin was not configured with explicit Node.js and pnpm versions, leading to:

1. **Version mismatch**: The plugin would download whatever default version it was configured with, which didn't match the project's requirements
2. **Missing workspace configuration**: The `pnpm-workspace.yaml` file was missing the `packages` field, which is required by pnpm 10.x
3. **Inconsistent environments**: Local environments had compatible versions installed system-wide, but CI environments used the plugin's defaults

## Solution

### 1. Configured Explicit Versions in build.gradle

```gradle
node {
    // Explicitly set Node.js and pnpm versions for reproducible builds
    // pnpm version must match packageManager in ui/package.json
    version = '20.11.0'
    pnpmVersion = '10.22.0'
    download = true
    nodeProjectDir = file("${project.projectDir}/ui")
}
```

**Key changes:**
- `version = '20.11.0'` - Explicit Node.js version (LTS)
- `pnpmVersion = '10.22.0'` - Must match `packageManager` in `ui/package.json`
- `download = true` - Automatically download if not present

### 2. Fixed pnpm-workspace.yaml

Added the required `packages` field:

```yaml
packages:
  - '.'

onlyBuiltDependencies:
  - '@parcel/watcher'
  - core-js
  - esbuild
  - vue-demi
```

The `packages: ['.']` declaration tells pnpm that this is a workspace with the current directory as a package.

## Verification

After the fix, the frontend build works correctly:

```bash
$ ./gradlew clean buildFrontend
BUILD SUCCESSFUL in 16s
```

The build now:
- ✅ Downloads the correct Node.js and pnpm versions automatically
- ✅ Works consistently across all environments (local, CI, etc.)
- ✅ Produces the expected output in `src/main/resources/console/`

## Maintenance Notes

### When to Update Versions

Update the pnpm version in `build.gradle` whenever you update the `packageManager` field in `ui/package.json`:

```json
{
  "packageManager": "pnpm@10.22.0"  // Must match build.gradle
}
```

### Skipping Frontend Build

For backend-only development, you can still skip the frontend build:

```bash
./gradlew build -PskipFrontend=true
```

This is useful when working only on Java code and wanting faster build times.

## Related Files

- `build.gradle` - Node.js and pnpm configuration
- `ui/package.json` - `packageManager` field defines required pnpm version
- `ui/pnpm-workspace.yaml` - Workspace configuration
- `ui/pnpm-lock.yaml` - Lock file version corresponds to pnpm version

## Testing

To verify the fix works:

1. Clean build directories:
   ```bash
   ./gradlew clean
   ```

2. Remove cached Node.js/pnpm:
   ```bash
   rm -rf .gradle/nodejs .gradle/pnpm
   ```

3. Build frontend:
   ```bash
   ./gradlew buildFrontend
   ```

4. Verify output:
   ```bash
   ls -la src/main/resources/console/
   ```

Expected: Directory should contain built assets (chunks/, *.js, *.css files)
