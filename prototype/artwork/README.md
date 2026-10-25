# App icons

- `app-icon.svg`: approved app icon, including the flat background and keypad.
- `notification-icon.svg`: white gesture on transparency, for notification small icons (`@drawable/ic_stat_glide`).
- `concepts/`: design drafts.

Regenerate Android resources with `node scripts/generate-app-icons.cjs` from the repository root. Node.js and `sharp` are required; `NODE_PATH` can point to an existing installation of `sharp`.

The generator produces density-specific legacy icons and transparent adaptive foregrounds, a solid adaptive background, and Android vectors for notifications and themed icons. The notification resource is supplied for future use; it does not enable notifications.
