---
name: Android Architecture
description: Structure an Android app cleanly: MVVM/MVI, ViewModel, StateFlow, repositories, navigation and optional DI. Use when a project grows beyond one screen or the user asks for a maintainable structure.
---

# Android Architecture

## Default: MVVM, no over-engineering

```text
app/
├─ data/        # repositories, network, database
├─ domain/      # optional use cases (only if they add value)
└─ ui/
   ├─ home/     # HomeViewModel + HomeScreen/Activity
   └─ detail/
```

- **UI** observes state and sends events.
- **ViewModel** owns UI state and calls repositories.
- **Repository** hides data sources (network, DB, files).
- Do not put Android `Context` in ViewModels.

## ViewModel + StateFlow

```kotlin
data class HomeUiState(val loading: Boolean = false, val items: List<Item> = emptyList(), val error: String? = null)

class HomeViewModel(private val repo: ItemRepository) : ViewModel() {
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    fun load() = viewModelScope.launch {
        _state.update { it.copy(loading = true, error = null) }
        runCatching { repo.items() }
            .onSuccess { items -> _state.update { it.copy(loading = false, items = items) } }
            .onFailure { e -> _state.update { it.copy(loading = false, error = e.message) } }
    }
}
```

In Compose: `collectAsStateWithLifecycle()`. In Views: `repeatOnLifecycle`.

## Navigation

- Compose: `androidx.navigation:navigation-compose`.
- Views: Navigation Component or a simple single-activity + fragments.
- Keep one source of truth for routes; pass IDs, not whole objects.

## DI

Hilt/Koin are optional and add dependencies. For small apps, manual
construction in an `Application` class or a simple `ServiceLocator` is enough
and works offline.

## Rules

- One class, one reason to change.
- No business logic in Activities/Fragments/Composables.
- No singletons holding Activity context.
- Prefer immutable state and explicit events.
- Before adding a library, check the offline dependency cache (`android-deps`).
