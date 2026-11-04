# Verification notes

This repository contains domain, data, presentation, and native-platform tests, plus a GitHub Actions workflow. Their presence alone does not establish a passing build on every target.

## Local evidence

During the November 4, 2026 editing session, the cached-dependency check reported **33 passing domain and non-SQL repository tests**. That helper omits SQLDelight-generated persistence and is not a substitute for the Gradle suites.

The final UI changes have not completed compilation or interactive verification in this session. Gradle startup was blocked by the execution environment's read-only global cache and networking restrictions. No successful Android or iOS build, device run, or full desktop launch is claimed here.

## Reproducible checks

From a development environment with JDK 21, the required Android SDK, and dependency access:

```bash
./gradlew :domain:jvmTest :data:jvmTest :shared:jvmTest
./gradlew :androidApp:assembleDebug :androidApp:lintDebug :desktopApp:assemble
```

On macOS with a compatible Xcode installation:

```bash
./gradlew :domain:iosSimulatorArm64Test :data:iosSimulatorArm64Test :shared:linkDebugFrameworkIosSimulatorArm64
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build
```

The CI configuration currently selects Xcode 26.4 explicitly. Its availability on the chosen runner must be checked when running the workflow.
