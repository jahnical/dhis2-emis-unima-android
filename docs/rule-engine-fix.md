# DHIS2 Rule Engine: Auto-Assignment Fix

**Module affected:** `emis`
**Feature affected:** Performance — score entry → grade auto-assignment
**Status:** Resolved

---

## Background

DHIS2 Program Rules allow server-configured logic to run on the client without code changes. In the Performance module, a rule was configured to **ASSIGN** a grade letter (e.g. "Level 3") to a grade data element whenever a score value is entered. This meant grade ranges could be updated in the DHIS2 admin interface without needing a new app release.

The rule engine is invoked via `RuleEngineRepository.evaluateDataEntryEffects(...)`, which returns a list of `RuleEffect` objects. Each effect has a `ruleAction` describing what should happen (ASSIGN, SHOWERROR, HIDEFIELD, etc.) and `data` carrying the computed value.

---

## The Problem

When a user entered a score, **no grade was auto-populated**. The grade field remained blank and was not saved.

---

## Root Causes (in order of discovery)

### 1. Grade value never reached the save cache (Critical)

**Where:** `PerformanceViewModel.fieldState()`

The save pipeline requires data to pass through `onClickNext()` → `_cache` → `save()`. The grade `DropdownField` is read-only (intentionally — users must not edit it manually), so `onNext` was never triggered for it. The ASSIGN handler called `fieldState(grade)` recursively, which only updated the UI state (`fieldsState`) but never wrote to `_cache`. On `save()`, only the score was persisted.

**Fix:** Replaced the recursive `fieldState()` call with inline logic that both updates `fieldsState` (UI) and directly adds an `EventTuple` to `_cache` for the assigned grade.

---

### 2. `fieldValidationJobs` keyed by TEI, not by field (Medium)

**Where:** `PerformanceViewModel.fieldState()`

```kotlin
// Before
fieldValidationJobs[key]?.cancel()          // key = TEI uid
fieldValidationJobs[key] = viewModelScope.launch { ... }

// After
val jobKey = "$key:$dataElement"
fieldValidationJobs[jobKey]?.cancel()
fieldValidationJobs[jobKey] = viewModelScope.launch { ... }
```

When `fieldState(grade)` was called recursively, it used the same TEI key and cancelled the currently-running score validation coroutine — self-cancellation. This caused unpredictable behaviour when multiple fields were active for the same TEI.

**Fix:** Keyed jobs by `"$teiUid:$dataElementUid"` so each field has its own independent job slot.

---

### 3. NPE in `ruleEvents` — null `eventDate` (Critical)

**Where:** `RuleEngineRepository.ruleEvents()`

```
java.lang.NullPointerException
    at RuleEngineRepository$ruleEvents$2.invokeSuspend(RuleEngineRepository.kt:109)
```

Line 109 was `Instant.fromEpochMilliseconds(event.eventDate()!!.time)`. Events that are **scheduled but not yet executed** have a null `eventDate`. The `!!` forced a crash when building the historical event list for rule context.

The same function also had unsafe `!!` calls on `event.programStage()`, `event.status()`, and `event.organisationUnit()`.

Additionally, `RuleEventStatus.valueOf(event.status()!!.name)` had no guard for `EventStatus.VISITED`, which is a valid DHIS2 status but does not exist in the rule engine's `RuleEventStatus` enum, and would have thrown `IllegalArgumentException`.

**Fix:** Changed `.map { }` to `.mapNotNull { }`. Each required field is extracted with a safe early exit (`?: return@mapNotNull null`). Status is resolved with a `when` expression covering `null`, `VISITED`, and a `try/catch` for any other unknown values.

```kotlin
// Before
.map { event ->
    RuleEvent(
        programStage = event.programStage()!!,
        status = RuleEventStatus.valueOf(event.status()!!.name),  // NPE if null
        eventDate = Instant.fromEpochMilliseconds(event.eventDate()!!.time), // NPE if null
        ...
    )
}

// After
.mapNotNull { event ->
    val programStage = event.programStage() ?: return@mapNotNull null
    val eventDate   = event.eventDate()     ?: return@mapNotNull null
    val status      = event.status()        ?: return@mapNotNull null
    val ou          = event.organisationUnit() ?: return@mapNotNull null

    RuleEvent(
        programStage = programStage,
        status = when (status) {
            EventStatus.VISITED -> RuleEventStatus.ACTIVE
            else -> try { RuleEventStatus.valueOf(status.name) }
                    catch (e: IllegalArgumentException) { RuleEventStatus.ACTIVE }
        },
        eventDate = Instant.fromEpochMilliseconds(eventDate.time),
        ...
    )
}
```

---

### 4. `PatternSyntaxException` in `extractUid` (Critical — blocked the ASSIGN from applying)

**Where:** `PerformanceViewModel.extractUid()`

```
java.util.regex.PatternSyntaxException: Syntax error in regexp pattern near index 19
[#AaVvDd]\{([^}]+)}
```

Android's ICU regex engine rejects `\{` as an escape sequence. The standard JVM accepts it, but the Android runtime does not. The fix is to use character classes `[{]` and `[}]` instead.

The regex was also being compiled on every `extractUid()` call. It is now a compiled constant in the companion object.

```kotlin
// Before — crashes on Android ICU
Regex("[#AaVvDd]\\{([^}]+)}")

// After — works on all Android versions
companion object {
    private val UID_TOKEN_REGEX = Regex("[#AaVvDd][{]([^}]+)[}]")
}
```

**What `extractUid` does:**
DHIS2 rule actions reference fields via token expressions (`#{deUid}`, `A{attrUid}`) rather than plain UIDs. This helper strips the token wrapper to return the raw UID for matching against the app's data structures. If the value is already a plain UID (as observed in the actual data), the regex finds no match and the input is returned unchanged.

---

### 5. Null-safety gaps in the ASSIGN effect handler (Medium)

**Where:** `PerformanceViewModel.fieldState()` — effects loop

The original code accessed `effect.ruleAction.type` and `effect.ruleAction.values[...]` without null guards. Any null in the effect chain would cause an NPE or silently skip the effect with no diagnostic information.

**Fix:** Each nullable field is extracted with an explicit null check and a `Timber.w(...)` skip log, so if an effect is malformed it is clearly reported and skipped rather than crashing.

---

## Diagnostic Logging Added

All logs use the tag `RULE_ENGINE`. Filter Logcat by this tag to debug rule engine issues in any component.

| Log level | Message | Meaning |
|-----------|---------|---------|
| `D` | `fieldState called: de=... event=... value=...` | `fieldState` was invoked; inputs are visible |
| `D` | `Calling evaluateDataEntryEffects for de=...` | Coroutine started; about to call rule engine |
| `D` | `Effects for de=... count=N` | Rule engine returned; N = number of effects |
| `D` | `Effect type=X data=Y values=Z` | Each individual effect's content |
| `D` | `ASSIGN rawTarget=... targetField=... assignedValue=...` | UID extraction result for ASSIGN actions |
| `D` | `ASSIGN applied: key=... de=... value=... cached` | Grade was successfully applied and cached |
| `W` | `ASSIGN skipped: could not resolve target field` | UID extraction returned blank |
| `W` | `ASSIGN skipped: assigned value is blank` | Rule returned no value |
| `W` | `ASSIGN skipped: target equals source DE` | Self-assignment guard triggered |
| `E` | `evaluateDataEntryEffects failed: ...` | Full exception with context — check the stack trace |

---

## Files Changed

| File | Change |
|------|--------|
| `emis/.../RuleEngineRepository.kt` | `ruleEvents()` — `map` → `mapNotNull`, all `!!` removed, `VISITED` and unknown status handled |
| `emis/.../PerformanceViewModel.kt` | `fieldState()` — ASSIGN handler caches grade, null-safe effect iteration, diagnostic logging |
| `emis/.../PerformanceViewModel.kt` | `fieldValidationJobs` key changed to `"$tei:$dataElement"` |
| `emis/.../PerformanceViewModel.kt` | `extractUid()` added with ICU-safe regex in companion object |

---

## Backward Compatibility

**`RuleEngineRepository.ruleEvents()`** is called from:
- `ruleEngineContextData()` → used by `evaluateDataEntryEffects()` and `evaluate()`
- Both of these are used wherever rule evaluation is needed across the app

The change from `map` to `mapNotNull` is **safe for all callers**. Events that previously caused a crash are now silently skipped. They cannot contribute meaningful context to rule evaluation (missing required fields like `eventDate` or `status`), so skipping them is semantically correct. No caller receives fewer valid results — they receive the same results minus the broken events.

---

## Applying This Pattern to Other Components

If rule evaluation is broken in another part of the app, check these points in order:

1. **Is `fieldState` (or equivalent) being called?**
   Add a log before the coroutine launch. If nothing appears, the UI callback (`setFormState` / `onValueChange`) is not wired up.

2. **Is `evaluateDataEntryEffects` throwing?**
   Check Logcat for `RULE_ENGINE E` entries. The exception message will point to the exact line. The most common causes are null fields in `ruleEvents` (already fixed) or an invalid `ou`/`program`/`stage` value.

3. **Are effects being returned but skipped?**
   Check the `ASSIGN skipped` warning logs. The raw target value is logged so you can see exactly what the rule engine is returning and whether `extractUid` is resolving it correctly.

4. **Is the assigned value updating the UI but not saving?**
   The assigned value must be added to whatever save cache the component uses. The rule engine only returns the value — the app code is responsible for persisting it. Follow the same pattern as the ASSIGN block in `PerformanceViewModel.fieldState()`.
