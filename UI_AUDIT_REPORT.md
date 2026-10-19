# Pox Kernel Manager UI Audit Report

**Audit Date:** October 16, 2026
**Framework:** Jetpack Compose + Material 3
**Target:** UI Layer Recomposition, Aesthetics, and Smoothness

---

## 1. Compose Recomposition Performance Analysis

### Critical Issues

#### **A. Whole-Data-Class Collection Triggers Full Recompositions**

Both ViewModels collect their entire `StateFlow` via `collectAsState()`, causing the entire UI to recompose whenever *any single field* changes.

**DashboardScreen.kt (line 34):**
```kotlin
val uiState by viewModel.uiState.collectAsState()
```
- `DashboardUiState` is a `data class` containing `deviceInfo: DeviceInfo`, `stats: HardwareStats`, `isLoading: Boolean`, `isRootGranted: Boolean`
- Every hardware poll (every 1.5s via `startHardwareMonitorLoop()`) calls `_uiState.value = _uiState.value.copy(stats = stats)`, triggering a full screen recomposition
- Similarly, `onAppForegrounded()` re-reads device info and root status, causing another full recomposition

**FeaturesScreen.kt (line 29):**
```kotlin
val uiState by viewModel.uiState.collectAsState()
```
- `FeaturesUiState` contains `features: TuningFeatures`, `isRootGranted: Boolean`, `isLoading: Boolean`, `feedbackMessage: String?`
- `loadFeatures()` re-reads the entire tuning feature set from the kernel
- `apply()` re-reads after every write operation

**DiagnosticsScreen.kt (line 39):**
```kotlin
val uiState by viewModel.uiState.collectAsState()
```
- `DiagnosticsUiState` contains `selinuxMode: String`, `nodeReport: List<KernelNode>`, `dmesgLogs: List<String>`, `isExporting: Boolean`, `exportMessage: String?`
- `nodeReport` and `dmesgLogs` can change independently, but the whole state triggers full recomposition

#### **B. DisposableEffect with `LocalLifecycleOwner.current`**

In both `DashboardScreen.kt` (line 42) and `FeaturesScreen.kt` (line 36):
```kotlin
val lifecycleOwner = LocalLifecycleOwner.current
DisposableEffect(lifecycleOwner) { ... }
```
- `LocalLifecycleOwner.current` is **recomputed on every composition**, not just when the lifecycle actually changes
- This means the `DisposableEffect` restarts its observer on every trivial recomposition (e.g., a `Text` change elsewhere)
- Potential for observer leaks or duplicate registrations

#### **C. 1.5s Hardware Poll Loop Causes Constant Recompositions**

`DashboardViewModel.startHardwareMonitorLoop()` (line 76-87) runs a `while(isActive)` loop with `delay(1500)` that updates `_uiState.value` every 1.5 seconds. Since the entire `DashboardUiState` is collected, this means **the UI recomposes fully 60+ times per minute** even when nothing user-relevant changes.

---

## 2. Design Aesthetics Audit

### **Dark Mode Palette**

The color scheme follows Material 3 Dark "Deep Emerald AMOLED Pure Dark" with generally good choices:

| Color | Hex | Evaluation |
|-------|-----|------------|
| `md_theme_dark_background` | `#FF111413` | Excellent for AMOLED - true black |
| `md_theme_dark_surface` | `#FF111413` | Matches background, good |
| `md_theme_dark_onBackground` | `#FFE1E3DF` | Light grayish, contrast adequate |
| `md_theme_dark_primary` | `#FF6EDCAC` | Emerald accent, vibrant |
| `md_theme_dark_primaryContainer` | `#FF005138` | Good contrast with `#FF6EDCAC` |
| `md_theme_dark_onPrimaryContainer` | `#FF8BF8C7` | Light green text on dark container - **contrast ratio may be borderline** |
| `md_theme_dark_error` | `#FFFFB4AB` | Yellow-ish, could be harder to read |
| `md_theme_dark_onError` | `#FF690005` | Bright red, good for errors |

**Contrast Concerns:**
- `md_theme_dark_onPrimaryContainer` (#FF8BF8C7) text on primaryContainer (#FF005138): The light green text may have insufficient contrast against the dark container in low-quality displays. Consider using `#FFC8E6C9` or a slightly darker green.
- `md_theme_dark_onError` (#FF690005) on error surfaces: The bright red on the yellow error container (#FFFFB4AB) has acceptable contrast but could be more vibrant.

**Suggestion:** Add a dynamic contrast check or use Material 3's `contrast()` function to verify AA/AAA compliance.

### **Typography**

The `Type.kt` typography scale is generally well-structured against Material 3 guidelines:

- `headlineLarge`: 32.sp, Weight 700 - Good for main titles
- `headlineMedium`: 24.sp, Weight 600 - Good section headers
- `titleMedium`: 16.sp, Weight 500 - Good for card titles
- `bodyLarge`: 16.sp, Weight 400 - Good for body text
- `bodyMedium`: 14.sp, Weight 400 - Good for subtitles
- `labelSmall`: 11.sp, Weight 500 - Good for labels

**Minor Issues:**
- `labelSmall` at 11.sp is used for dense UI elements (status badges, core frequencies). On some devices, this may be too small for comfortable reading.
- `bodyLarge` at 16.sp is used interchangeably with `bodyMedium` (14.sp) in some places - ensure intentional distinction.

### **Glassmorphism / Card Elevation**

| Element | Current | Evaluation |
|---------|---------|------------|
| Primary Cards | `surfaceContainer`, `RoundedCornerShape(20dp)` | Standard M3 card, good |
| FeatureSwitchCard | `surfaceContainer`, `RoundedCornerShape(20dp)` | Consistent |
| UnsupportedReason | `surfaceContainerHigh`, `RoundedCornerShape(14dp)` | Good distinction for disabled/unavailable |
| OutlinedCard (Diagnostics) | `surfaceContainerLowest` + `BorderStroke(1dp)` | Distinctive, good for "read-only" sections |
| StatusBadge | `secondaryContainer` / `surfaceContainerHighest` | Good color differentiation |

**Missing:** No true glassmorphism (backdrop blur) is used, which is appropriate for this type of system-tuning app where clarity trumps aesthetic trends. The card elevation uses default `CardDefaults.shadowElevation()` which is subtle but present.

**Suggestion:** Consider adding `elevation = CardElevation.Medium` on primary action cards to improve tactile affordance.

### **Haptic Feedback**

**Critical Gap:** No haptic feedback is implemented in the UI layer.

- Switch toggles have no `rememberHapticFeedbackSource()` + `clickHapticFeedback()` 
- Sliders have no `onValueChangeFinished` haptic response
- Buttons (Reclaim, Export) have no feedback

**Opportunity Missed:** The app already has vibrator strength control (`FeatureSliderCard` for "Hardware Vibrator Strength") but doesn't provide feedback when the user changes it.

**Suggested Additions:**
```kotlin
// In FeatureSwitchCard
Modifier.clickable(
    hapticFeedback = HapticFeedbackKind.SelectionChanged
) { ... }

// In FeatureSliderCard
onValueChangeFinished = {
    LocalHapticFeedbackFactory.vibrate(HapticFeedbackType.light)
}
```

---

## 3. 60fps / 90fps Scrolling smoothness & UI Responsiveness

### **Scrolling Performance**

All three screens use `Column` with `verticalScroll(scrollState)`:

- **DashboardScreen**: Fixed set of sections (Kernel overview, Battery, CPU, GPU, RAM). No `LazyColumn` used since the content set is finite and known.
- **FeaturesScreen**: Fixed tuning features grid. No `LazyColumn`.
- **DiagnosticsScreen**: Uses `LazyColumn` for `dmesgLogs` (line 236) - **this is the only screen using lazy loading, well done**.

**Problem:** When the hardware monitor loop updates stats every 1.5s, the entire `Column` recomposes. During manual scrolling, this can cause:
- "jitter" where the scroll position shifts slightly on each tick
- dropped frames if the shell commands take >16ms

**No `LazyColumn` for long lists** (except dmesg):
- `FeaturesScreen` has ~20 feature controls in a `Column`
- `DashboardScreen` has ~8 stat cards in a `Column`
- These could benefit from `LazyColumn` if the content were to grow, but for fixed UIs, the overhead of `LazyColumn` may not be justified.

### **Background Load Responsiveness**

- `HardwareRepository.getHardwareStats()` runs on `Dispatchers.IO` - good, won't block UI thread directly
- However, synchronous `ShellUtils.fastRead()` calls could take variable time depending on kernel responsiveness
- If a shell read times out or blocks, the `catch (_: Exception) {}` in the monitor loop prevents ANRs, but the last good values will be displayed
- The `refresh()` function also has no timeout, so a slow kernel probe could briefly freeze the UI scope

**Recommendation:** Add a timeout to hardware repo calls or use `withTimeoutOrNull()` to prevent long stalls.

---

## 4. Concrete Diffs & Architectural Improvements

### **Fix 1: Granular State Collection (Prevent Full Recompositions)**

**DashboardScreen.kt** - Use `snapshotFlow` with keys or observe individual fields:

```kotlin
// Before (line 34):
val uiState by viewModel.uiState.collectAsState()

// After - observe only what's needed:
val deviceInfo by viewModel.uiState.map { it.deviceInfo }.collectAsState()
val stats by viewModel.uiState.map { it.stats }.collectAsState()
val isLoading by viewModel.uiState.map { it.isLoading }.collectAsState()
val isRootGranted by viewModel.uiState.map { it.isRootGranted }.collectAsState()
```

**FeaturesScreen.kt** - Same pattern:

```kotlin
val features by viewModel.uiState.map { it.features }.collectAsState()
val isRootGranted by viewModel.uiState.map { it.isRootGranted }.collectAsState()
val isLoading by viewModel.uiState.map { it.isLoading }.collectAsState()
val feedbackMessage by viewModel.uiState.map { it.feedbackMessage }.collectAsState()
```

**DiagnosticsScreen.kt** - Observe independent state slices:

```kotlin
val selinuxMode by viewModel.uiState.map { it.selinuxMode }.collectAsState()
val nodeReport by viewModel.uiState.map { it.nodeReport }.collectAsState()
val dmesgLogs by viewModel.uiState.map { it.dmesgLogs }.collectAsState()
val isExporting by viewModel.uiState.map { it.isExporting }.collectAsState()
```

### **Fix 2: Stabilize `DisposableEffect` Lifecycle Observers**

**DashboardScreen.kt** - Use `remember { LocalLifecycleOwner.current }` or move observer logic:

```kotlin
// Before (problematic):
val lifecycleOwner = LocalLifecycleOwner.current
DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event -> ... }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
}

// After - use remember with selected key:
DisposableEffect(Unit) {  // Run once initially
    val observer = LifecycleEventObserver { _, event -> ... }
    val currentOwner = LocalLifecycleOwner.current
    currentOwner.lifecycle.addObserver(observer)
    onDispose { currentOwner.lifecycle.removeObserver(observer) }
}
```

Actually, better approach - move the observer logic to a dedicated UI host composable or use `Effect` with proper keys.

### **Fix 3: Throttle Hardware Polling or Use SnapshotFlow**

**DashboardViewModel.kt** - Reduce poll frequency or use `SnapshotFlow`:

```kotlin
// Reduce from 1.5s to 3s:
delay(3000)

// Or use SnapshotFlow for batched updates:
private val _uiState = MutableStateFlow(DashboardUiState())
val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

// In the monitor loop:
_uiState.value = withLock(_uiState.mutex) {
    _uiState.value.copy(stats = stats)
}
```

Actually, better: use `derivedStateOf` or convert to `SnapshotFlow` in the composable:

```kotlin
// In DashboardScreen.kt:
val derivedStats by remember { mutableDerivedStateOf { uiState.stats } }
// Use derivedStats instead of uiState.stats for scrollable content
```

### **Fix 4: Add Haptic Feedback to Interactive Components**

**FeatureSwitchCard.kt** - Add haptic feedback:

```kotlin
// Add to the Row/Column clickable modifier:
Modifier.clickable(
    onClick = { onCheckedChange(!isChecked) },
    hapticFeedback = HapticFeedbackKind.SelectionChanged
) { ... }
```

**FeatureSliderCard.kt** - Add haptic feedback on release:

```kotlin
Slider(
    value = value,
    onValueChange = onValueChange,
    onValueChangeFinished = {
        LocalHapticFeedbackFactory.vibrate(HapticFeedbackType.medium)
        onValueChangeFinished()  // original callback
    },
    ...
)
```

**PoxTopAppBar.kt** - Add refresh button feedback:

```kotlin
IconButton(onClick = onRefresh, enabled = !isRefreshing) {
    if (isRefreshing) {
        CircularProgressIndicator(...)
    } else {
        Icon(...)
    }
}.clickable(
    hapticFeedback = HapticFeedbackKind.ButtonPress
)
```

### **Fix 5: Improve Color Contrast**

**Color.kt** - Fix `onPrimaryContainer` text contrast:

```kotlin
// Before:
val md_theme_dark_onPrimaryContainer = Color(0xFF8BF8C7)

// After - slightly darker for better contrast:
val md_theme_dark_onPrimaryContainer = Color(0xFFC8E6C9)  // brighter green with better contrast
```

Or use Material 3's built-in contrast adjustment:
```kotlin
// If using dynamic color, let Material 3 compute:
val (primaryContainer, onPrimaryContainer) = when {
    darkTheme => dynamicDarkColorScheme(context).primaryContainer to dynamicDarkColorScheme(context).onPrimaryContainer
    else => ...
}
```

### **Fix 6: Replace `DisposableEffect(lifecycleOwner)` Pattern**

Replace the anti-pattern in all three screens with a cleaner approach. Create a reusable `lifecycleEffect` composable:

**New utility composable** (add to theme or components package):
```kotlin
@Composable
fun LifecycleEffect(
    block: LifecycleEventObserver.() -> Unit = {},
    unit: Boolean = false
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var observer: LifecycleEventObserver? = null
    DisposableEffect(unit) {
        observer = LifecycleEventObserver(block)
        lifecycleOwner.lifecycle.addObserver(observer!!)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer!!) }
    }
}
```

Then use: `LifecycleEffect { onResume -> viewModel.onAppForegrounded() }`

### **Fix 7: Consider `LazyColumn` for Scalable Lists**

Where content might grow (or for consistency), convert sections to `LazyColumn`:

**FeaturesScreen.kt** - Feature cards could use `LazyColumnFor`:
```kotlin
LazyColumn(modifier = Modifier.fillMaxSize()) {
    items(items = featureSections) { section ->
        // composable for each section
    }
}
```

For the current fixed UI, `Column` is acceptable, but adding `rememberMaxScrollIndex()` could help with measure/layout optimizations.

### **Fix 8: Add Timeout to Hardware Reads**

**HardwareRepository.kt** - Wrap slow shell reads with timeout:

```kotlin
import kotlinx.coroutines.withTimeout

// Example:
val curFreq = withTimeoutOrNull(500) {
    ShellUtils.fastRead(freqPath)?.toIntOrNull() ?: fallbackFreq
} ?: fallbackFreq
```

This prevents a single slow kernel read from stalling the 1.5s polling loop and causing UI jank.

---

## Summary of Priority Fixes

| Priority | Issue | File(s) | Fix |
|----------|-------|---------|-----|
| **P0** | Whole-data-class `collectAsState()` causes full recompositions | All screens | Granular `map {}` collection |
| **P0** | `DisposableEffect(lifecycleObserver)` recomputed every composition | DashboardScreen, FeaturesScreen | Stabilize with `remember` or use dedicated utility |
| **P0** | 1.5s hardware poll triggers 60+ full UI recomps/min | DashboardViewModel | Throttle or use `SnapshotFlow`/`derivedStateOf` |
| **P1** | No haptic feedback on interactive elements | FeatureSwitchCard, FeatureSliderCard, PoxTopAppBar | Add `HapticFeedback` modifiers |
| **P1** | `onPrimaryContainer` contrast may be insufficient | Color.kt | Adjust color values |
| **P2** | Slow shell reads could cause UI stalls | HardwareRepository.kt | Add `withTimeoutOrNull` |
| **P2** | No `LazyColumn` for scalable content | FeaturesScreen, DashboardScreen | Consider for future-proofing |