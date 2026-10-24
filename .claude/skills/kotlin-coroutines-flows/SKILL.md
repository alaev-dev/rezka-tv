---
name: kotlin-coroutines-flows
description: Structured concurrency, dispatchers and cancellation for Kotlin coroutines on Android. Use when writing or reviewing coroutine code, or debugging cancellation and concurrency.
metadata:
  origin: ECC (https://github.com/affaan-m/ECC, MIT), adapted excerpt: no Compose, StateFlow UI or Turbine, examples rewritten for Activity scopes without comments
---

# Kotlin Coroutines

Patterns for structured concurrency, dispatchers and cancellation in Android projects.

## Structured Concurrency

### Scope Hierarchy

```
Application
  └── MainScope() (Activity, cancelled in onDestroy)
        └── coroutineScope { } (structured child)
              ├── async { } (concurrent task)
              └── async { } (concurrent task)
```

Always use structured concurrency — never `GlobalScope`. Wrong, the coroutine outlives the screen:

```kotlin
GlobalScope.launch { fetchData() }
```

Right, the scope belongs to the Activity and is cancelled with it:

```kotlin
private val scope = MainScope()

fun load() = scope.launch { fetchData() }

override fun onDestroy() {
    scope.cancel()
    super.onDestroy()
}
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

Use `supervisorScope` when child failures should not cancel siblings: a failing `syncItems` leaves
`syncStats` and `syncSettings` running.

```kotlin
suspend fun syncAll() = supervisorScope {
    launch { syncItems() }
    launch { syncStats() }
    launch { syncSettings() }
}
```

## Dispatchers

CPU-bound work such as parsing goes to `Dispatchers.Default`, blocking IO such as network and disk goes
to `Dispatchers.IO`, and views are touched only on `Dispatchers.Main`, the default of `MainScope()`:

```kotlin
val page = withContext(Dispatchers.IO) { client.download(url) }
val details = withContext(Dispatchers.Default) { parser.details(page) }
bind(details)
```

## Cancellation

### Cooperative Cancellation

Long-running loops must check for cancellation; `ensureActive()` throws `CancellationException`
once the scope is cancelled:

```kotlin
suspend fun processItems(items: List<Item>) = coroutineScope {
    for (item in items) {
        ensureActive()
        process(item)
    }
}
```

### Cleanup with try/finally

The `finally` block runs even when the coroutine is cancelled, so the spinner never stays on screen:

```kotlin
scope.launch {
    try {
        spinner.isVisible = true
        bind(withContext(Dispatchers.IO) { client.details(url) })
    } finally {
        spinner.isVisible = false
    }
}
```

## Anti-Patterns to Avoid

- Using `GlobalScope` — leaks coroutines, no structured cancellation
- Catching `CancellationException` — let it propagate for proper cancellation
