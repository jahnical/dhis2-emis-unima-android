# UNIMA-EMIS Module

This module contains **University of Malawi (UNIMA)-specific customizations** for the DHIS2 EMIS Android application.

## Purpose

The `unima-emis` module isolates UNIMA-specific code from the base `emis` module so that:
- The base `emis` module can be updated from upstream without conflicts
- UNIMA-specific logic is clearly separated and documented
- Future UNIMA changes only need to be made in this module

## Architecture

```
app
 └── depends on: unima-emis (via implementation)
                   └── depends on: emis (via api - transitive)
```

- `unima-emis` depends on `emis` using `api()`, so the app module gets access to both
- The app's `build.gradle.kts` only needs `implementation(project(":unima-emis"))`

## Module Structure

```
unima-emis/
├── build.gradle.kts
├── src/main/
│   ├── AndroidManifest.xml
│   └── java/org/unima/emis/
│       ├── data/
│       │   └── UnimaDataManagerDebug.kt     # Debug logging for data operations
│       ├── data/model/
│       │   └── UnimaEMISConfig.kt           # Enhanced config parsing with verbose logging
│       ├── di/
│       │   └── UnimaAppModule.kt            # UNIMA-specific Hilt module
│       ├── ui/home/
│       │   └── UnimaModuleVisibility.kt     # UNIMA module visibility logic
│       └── utils/
│           ├── UnimaConstants.kt            # UNIMA-specific constants
│           └── UnimaLogger.kt               # Structured logging utility
```

## Customizations

### 1. Enhanced Config Parsing (`UnimaEMISConfig.kt`)
- Provides verbose error logging when JSON parsing fails
- Logs JSON preview, error type, and message for debugging
- Use instead of base `EMISConfig.fromJson()` when detailed error info is needed

### 2. Module Visibility (`UnimaModuleVisibility.kt`)
- UNIMA shows features based on whether config section **exists** (not null)
- Base EMIS requires explicit `enabled: true` flag
- Use `UnimaModuleVisibility.getModules(config)` in HomeViewModel

### 3. Debug Logging (`UnimaDataManagerDebug.kt`, `UnimaLogger.kt`)
- Structured logging for absenteeism queries, config loading, etc.
- All logs tagged with `UNIMA_` prefix for easy filtering:
  ```bash
  adb logcat | grep "UNIMA_"
  ```

### 4. Constants (`UnimaConstants.kt`)
- UNIMA-specific feature keys (enrollment, transfer, final-result, socio-economics)
- Logging tag constants

## Bug Fixes in Base `emis`

These fixes were applied directly to the `emis` module since they are correctness issues, not customizations:

| Fix | File | Description |
|-----|------|-------------|
| `listOfNotNull` | `AttendanceViewModel.kt` | Prevents `"null"` string in SQL query data element IDs |
| `?: ""` | `HomeScreen.kt` | Prevents null navigation argument crash in absenteeism route |
| Basic error logging | `EMISConfig.kt` | Added basic `Timber.e()` in catch block (was empty) |
| `reason` field | `Transfer.kt` | Added `reason` field to match DataStore JSON structure |

## DataStore Configuration

The UNIMA EMIS uses the following DataStore structure:
- **Namespace:** `semis`
- **Key:** `values`

See `config_values_corrected.json` in the project root for the expected JSON format.

## How to Update Base EMIS

When updating the base `emis` module from upstream:

1. Update only files in the `emis/` directory
2. Verify the bug fixes listed above are preserved (or no longer needed)
3. Run the app to verify `unima-emis` customizations still work
4. Check that `EMISConfigItem`, `Transfer`, `Attendance`, etc. data classes
   still have the fields expected by `unima-emis` utilities

