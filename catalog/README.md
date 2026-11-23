# Appx catalog

`apps.json` is the app-store catalog consumed by the Appx clients.

Each app entry should include:

- `id`: stable unique lowercase identifier
- `name`, `version`, `description`
- `category`: one of the IDs in `categories.json`
- `icon_url`: public image URL
- `download_url`: public HTTPS URL to the APK (Dropbox shared link must be converted to a direct-download URL when appropriate)
- `package_name`: Android application ID
- `file_size_bytes`: APK size in bytes
- `sha256`: SHA-256 checksum of the exact APK
- `min_android`: minimum supported Android version, if known
- `source_url`: official developer/source page

Only publish apps when the source and redistribution permission are clear. Check the APK hash and package metadata before listing it. Never publish modified, suspicious, or unverified APKs. Installation always remains under Android's own user confirmation.

## Adding an app

1. Obtain the APK from the developer's official source or another source with clear redistribution permission.
2. Upload it to the Appx Dropbox storage folder.
3. Verify the uploaded file and create a public HTTPS download link.
4. Calculate SHA-256 and inspect package name, version, and minimum Android version.
5. Add a complete entry to `apps.json` and test the download link.
