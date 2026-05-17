# Next

A focused Android TODO list with a persistent top-item notification and a deliberate snooze mechanic.

## What it does

- Shows the **current top task** as a persistent notification you can't accidentally dismiss
- Notification has two actions: **Mark complete** and **Snooze**
- **Snooze** hides the top N items for a few minutes, creating a new top that stays prominent until you engage with it
- Full list view inside the app for adding, reordering, crossing off, and managing the snooze
- Backup to / restore from Google Drive via explicit push and pull buttons (no live sync)

## How it's built

Kotlin + Jetpack Compose + Room. Functional Core / Imperative Shell architecture: pure-Kotlin domain logic (JVM-tested) separated from the Android-specific data layer, Compose UI, and foreground-service notification machinery.

See [`docs/adr/`](docs/adr/) for the architectural decisions and [`docs/spec.md`](docs/spec.md) for the feature spec.

## This repository

Built as Human/AI pair development — a hobby-scale Android app held to production standards. The human directs; Claude (Anthropic) authors, tests, and validates. Sibling project to [Bean Counter](https://github.com/Oddley/BeanCounter), a local-first PWA built with the same collaboration model.

## License

MIT.
