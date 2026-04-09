# Memory Profiling Guide for GeckoView

This guide explains how to profile memory usage and detect memory leaks in the `react-native-geckoview` library.

## Prerequisites

- Android device or emulator running the example app
- Android SDK platform tools (`adb`)
- Android Studio (optional, for heap analysis)

## Quick Start

### 1. Run the Example App

```bash
cd example && yarn android
```

### 2. Basic Memory Check

```bash
adb shell dumpsys meminfo com.reactnative.geckoview.example
```

## Detecting Memory Leaks

### Step-by-Step Process

#### 1. **Baseline Measurement**
Run the app and let it stabilize for 1-2 minutes, then take an initial measurement:

```bash
adb shell dumpsys meminfo com.reactnative.geckoview.example | grep -A 10 "TOTAL"
```

#### 2. **Stress Test the Application**
Perform these actions repeatedly (10-20 times):
- Navigate between different URLs
- Create and destroy browser instances
- Change privacy settings (ETP levels, cookie modes)
- Install and uninstall web extensions
- Trigger rapid component mount/unmount cycles

#### 3. **Monitor Memory Over Time**
Use `watch` to continuously monitor memory:

```bash
watch -n 5 'adb shell dumpsys meminfo com.reactnative.geckoview.example | grep -A 10 TOTAL'
```

**What to look for:**
- **Normal**: Memory increases initially, then stabilizes
- **Leak**: Continuous unbounded growth even after stress test stops

#### 4. **Force Garbage Collection** (if supported)
Some devices allow triggering GC via developer options:

```bash
# Enable "Show taps" in Developer Options to verify
# Then force background GC by minimizing app
adb shell input keyevent KEYCODE_HOME
sleep 5
adb shell dumpsys meminfo com.reactnative.geckoview.example
```

### Expected Behavior

✅ **Good (No Leak)**
```
Initial:  Java Heap: 45 MB, Native Heap: 120 MB
After 20 cycles: Java Heap: 48 MB, Native Heap: 125 MB
After GC: Java Heap: 46 MB, Native Heap: 122 MB
```

❌ **Bad (Memory Leak)**
```
Initial:  Java Heap: 45 MB, Native Heap: 120 MB
After 20 cycles: Java Heap: 78 MB, Native Heap: 280 MB
After GC: Java Heap: 75 MB, Native Heap: 275 MB ← Not reclaiming
```

## Advanced Profiling

### Capture Heap Dump

#### 1. **Capture the Dump**
```bash
adb shell am dumpheap -n com.reactnative.geckoview.example /data/local/tmp/heap.dump
adb pull /data/local/tmp/heap.dump ./heap.dump
```

#### 2. **Analyze with Android Studio**
1. Open Android Studio
2. Go to **View → Tool Windows → Profiler**
3. Click **Sessions** → **Load from file**
4. Select the `heap.dump` file
5. Look for:
   - Retained objects that should have been garbage collected
   - GeckoSession instances that outlive their components
   - Event listeners not properly cleaned up

### Use Android Studio Profiler (Real-time)

#### 1. **Connect Device**
```bash
adb devices
```

#### 2. **Start Profiler**
1. Open Android Studio
2. **View → Tool Windows → Profiler**
3. Click **+** → Select your device and app
4. Click **Memory** timeline

#### 3. **Perform Stress Test**
While profiler is running:
1. Perform stress test actions in app
2. Click **Force GC** button in profiler
3. click **Dump Java Heap**

#### 4. **Analyze Results**
Look for:
- **Shallow Size**: Memory directly occupied by object
- **Retained Size**: Total memory freed if object is GC'd
- **Dominator Tree**: Objects preventing GC
- **Package Filter**: Filter by `com.reactnative.geckoview`

## Automated Memory Tests

### Running Jest Memory Leak Tests

```bash
cd example
yarn test MemoryLeak-test.tsx
```

These tests verify:
- ✅ Rapid session creation/destruction (100 iterations)
- ✅ Persistent session across mount/unmount cycles (50 iterations)
- ✅ Multiple concurrent sessions (10 instances)
- ✅ Event listener cleanup
- ✅ Rapid URL navigation

**Note**: Jest tests verify logical behavior but cannot detect actual memory leaks. Use the profiling methods above for real memory analysis.

## Common Memory Leak Scenarios

### 1. **Unreleased Event Listeners**

**Symptom**: Memory grows with each component mount/unmount
**Check**: Event handlers not removed in cleanup

```javascript
// ✅ Good
useEffect(() => {
  const handler = (event) => { /* ... */ };
  geckoRef.current?.addEventListener('load', handler);
  
  return () => {
    geckoRef.current?.removeEventListener('load', handler);
  };
}, []);

// ❌ Bad - Listener never removed
useEffect(() => {
  geckoRef.current?.addEventListener('load', handler);
  // Missing cleanup!
}, []);
```

### 2. **Session Retention**

**Symptom**: GeckoSession objects not garbage collected
**Check**: Sessions not released when components unmount

```kotlin
// ✅ Good
override fun onDropViewInstance(view: GeckoView) {
    super.onDropViewInstance(view)
    view.setSession(null) // Release session reference
}

// ❌ Bad - Session retained
override fun onDropViewInstance(view: GeckoView) {
    super.onDropViewInstance(view)
    // Session still referenced!
}
```

### 3. **Closure Captures**

**Symptom**: JavaScript closures retaining large objects
**Check**: Callbacks capturing unnecessary context

```javascript
// ✅ Good - Minimal capture
const handleLoad = useCallback(() => {
  console.log('Loaded');
}, []);

// ❌ Bad - Captures entire component state
const handleLoad = () => {
  console.log('Loaded', largeDataObject);
};
```

## Troubleshooting

### High Native Heap Usage

**Cause**: GeckoView WebEngine instances not released
**Solution**: Ensure `session.close()` is called and sessions are nulled

### High Java Heap Usage

**Cause**: JavaScript closures or React component state retention
**Solution**: Review useEffect cleanup, useCallback dependencies

### Memory Spikes During Navigation

**Cause**: Normal - new pages allocate memory before old pages are GC'd
**Solution**: Wait for GC cycle, ensure pages are released after navigation

## Performance Benchmarks

### Acceptable Memory Profile

| Scenario | Initial | After 10 navigations | After 50 sessions | After GC |
|----------|---------|---------------------|-------------------|----------|
| Java Heap | 40-50 MB | 45-55 MB | 50-60 MB | 42-52 MB |
| Native Heap | 100-130 MB | 120-150 MB | 130-170 MB | 110-140 MB |
| Graphics | 20-30 MB | 25-35 MB | 25-35 MB | 20-30 MB |

**Red Flags**:
- Java Heap > 100 MB without heavy usage
- Native Heap > 300 MB
- Continuous growth without stabilization
- No memory reclaimed after GC

## Reporting Issues

When reporting memory leaks, include:

1. **Heap dumps** (before and after stress test)
2. **Memory profiler screenshots**
3. **Steps to reproduce**
4. **Device/emulator details**
5. **React Native version**
6. **GeckoView version**

## Additional Resources

- [Android Memory Profiler Guide](https://developer.android.com/studio/profile/memory-profiler)
- [GeckoView Memory Management](https://mozilla.github.io/geckoview/)
- [React Native Performance](https://reactnative.dev/docs/performance)
