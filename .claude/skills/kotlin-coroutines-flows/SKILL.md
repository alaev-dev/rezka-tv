---
name: kotlin-coroutines-flows
description: Structured concurrency, dispatchers and cancellation for Kotlin coroutines on Android. Use when writing or reviewing coroutine code, or debugging cancellation and concurrency.
metadata:
  origin: ECC (https://github.com/affaan-m/ECC, MIT), excerpt without Compose, StateFlow UI and Turbine sections
---

# Kotlin Coroutines

Patterns for structured concurrency, dispatchers and cancellation in Android projects.

## Structured Concurrency

### Scope Hierarchy

```
Application
  └── viewModelScope (ViewModel)
        └── coroutineScope { } (structured child)
              ├── async { } (concurrent task)
              └── async { } (concurrent task)
```

Always use structured concurrency — never `GlobalScope`:

```kotlin
// BAD
GlobalScope.launch { fetchData() }

// GOOD — scoped to ViewModel lifecycle
viewModelScope.launch { fetchData() }

// GOOD — scoped to composable lifecycle
LaunchedEffect(key) { fetchData() }
```

### Parallel Decomposition

Use `coroutineScope` + `async` for parallel work:

```kotlin
suspend fun loadDashboard(): Dashboard = coroutineScope {
    val items = async { itemRepository.getRecent() }
    val stats = async { statsRepository.getToday() }
    val profile = async { userRepository.getCurrent() }
    Dashboard(
        items = items.await(),
        stats = stats.await(),
        profile = profile.await()
    )
}
```

### SupervisorScope

Use `supervisorScope` when child failures should not cancel siblings:

```kotlin
suspend fun syncAll() = supervisorScope {
    launch { syncItems() }       // failure here won't cancel syncStats
    launch { syncStats() }
    launch { syncSettings() }
}
```


## Dispatchers

```kotlin
// CPU-intensive work
withContext(Dispatchers.Default) { parseJson(largePayload) }

// IO-bound work
withContext(Dispatchers.IO) { database.query() }

// Main thread (UI) — default in viewModelScope
withContext(Dispatchers.Main) { updateUi() }
```

In KMP, use `Dispatchers.Default` and `Dispatchers.Main` (available on all platforms). `Dispatchers.IO` is JVM/Android only — use `Dispatchers.Default` on other platforms or provide via DI.

## Cancellation

### Cooperative Cancellation

Long-running loops must check for cancellation:

```kotlin
suspend fun processItems(items: List<Item>) = coroutineScope {
    for (item in items) {
        ensureActive()  // throws CancellationException if cancelled
        process(item)
    }
}
```

### Cleanup with try/finally

```kotlin
viewModelScope.launch {
    try {
        _state.update { it.copy(isLoading = true) }
        val data = repository.fetch()
        _state.update { it.copy(data = data) }
    } finally {
        _state.update { it.copy(isLoading = false) }  // always runs, even on cancellation
    }
}
```


## Anti-Patterns to Avoid

- Using `GlobalScope` — leaks coroutines, no structured cancellation
- Catching `CancellationException` — let it propagate for proper cancellation
