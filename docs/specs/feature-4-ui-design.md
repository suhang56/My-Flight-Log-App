# UI Design Spec: Feature 4 — Logbook Search & Filter

**Author:** UI/UX Designer
**Status:** Ready for Developer

Key design decisions:
- Search bar fixed above LazyColumn (never scrolls away)
- FilterChip row with horizontal scroll (years first, seat classes second)
- Sort icon in TopAppBar → DropdownMenu (Newest/Oldest/Longest Distance)
- Clear TextButton in TopAppBar, visible only when filterState.isActive
- Two empty states: LogbookEmptyState vs NoResultsState with SearchOff icon
- StatsRow gains "(filtered)" suffix in labelSmall/tertiary
- Chips show checkmark when selected
- Search field has trailing X to clear query only
- LogbookSortOrder enum gains displayName property

See full wireframes and component specs in Designer's message.
