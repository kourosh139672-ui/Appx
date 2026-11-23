# Pixora

Pixora is an Android photo discovery and sharing app inside the Appx repository.

## Product goals

- Browse a large online photo collection supplied by an external photo provider.
- Receive images shared from other Android apps through the Android Sharesheet.
- Save liked photos to a persistent local Favorites collection.
- Download photos to the device only when the user chooses Download.
- Let users submit photos for publication through a moderated upload flow.

## Planned screens

1. **Explore** — online photo feed with search and category filters.
2. **Favorites** — photos liked by the user, retained after app restarts.
3. **Incoming share** — preview an image shared from another app, then save it to Pixora.
4. **Upload** — choose an image and submit it for review.
5. **Photo details** — image, creator/source attribution, Like, Download, and Share actions.

## Technical plan

- Native Android app (Kotlin).
- Android Sharesheet integration using the appropriate image share intent filters.
- Persistent on-device storage for favorites and cached metadata.
- An external photo API for the public photo feed. Provider selection and API requirements must be verified before integration.
- A backend/storage service for user-submitted images, with authentication, access controls, reporting, and moderation before public display.

## Important behavior

- Liking a photo must not download a second copy of the full image; store its stable provider ID and metadata locally.
- A photo shared into Pixora must not become public automatically. The user should explicitly choose whether to keep it privately or submit it for review.
- Respect the photo provider's attribution, hotlinking, download-tracking, and licensing requirements.
- Never put private server credentials in the Android app.
- Show clear permission prompts and handle denied permissions gracefully.

## Build status

Planning/specification only. No APK has been built or tested yet.
