# Private remote learning updates (APK v15)

Repository: `asp0902/Mobile-Game-Assistant`, private, branch `main`.
Code is stored in the repository; personal learning JSON, portraits and reference
documents are stored in its private Release asset `learning.zip`.
`remote-learning/channel.json` is published only after the asset is uploaded.

## Device operation

1. Install APK v15 once. This includes the tracker flicker fix, app-icon
   collapse/expand control, Honor Duel starting Epic rule, Valka Epic portrait
   reference and scoped user confirmations for Perseus, Quinn and Valka.
2. Open saved learning, then remote updates.
3. Create a GitHub fine-grained PAT limited to this repository, Contents: Read-only.
4. Enter it on the device and save. Never send the token in chat or commit it.
5. Press manual learning update. Version, client compatibility, ZIP SHA-256,
   every file's size/hash, paths, JSON shape, images and rule schema are checked.
6. On success, stop tracking, fully close the app, then relaunch it.
   Verify the displayed active version. Data-only updates do not need another APK.

Tokens use Android Keystore AES-GCM; only ciphertext and IV are in private app
preferences. Android backup is disabled. A token can be deleted without deleting
offline data. Rooted/compromised devices are outside this protection boundary.
The desktop GitHub CLI credential is never copied into the app or APK.

The app sends authorization only to `api.github.com`, never to a redirected
Release download host. Network access occurs only when the update button is used.
Do not make this repository public: source history and Releases contain personal
information. Changing visibility later does not revoke copies already obtained.

## Consumed data and boundaries

- `LearningFiles` supplies one immutable, verified source for each app process.
- Learning screen JSON, linked text and portraits use that source.
- Hero catalog JSON, name corrections, CSV, normal and initial-offer portrait
  templates use the same source before their existing caches are built.
- Boss overlay uses the updated learning JSON through the repository.
- Initial formation advisor consumes `learning/initial_formation_rules.json`.
  Supported weights: healer, support, statueLightbearer, flame, midas,
  midasTankHealer, springPerWilder, springSustain, goblinCassadia (integers -20..20).
  Known artifacts' explanations may be replaced and an artifact may be disabled.
  Existing confirmed-hero requirements, ties, unknown evidence and SELECT safety
  gates cannot be overridden by the remote JSON. Personal roster rank and
  Honor Duel offer rank remain separate.
- Other arbitrary notes are searchable evidence, not automatically executable
  `learning/honor_initial_confirmations.json` applies explicit user confirmations
  only to the exact supported initial board layout; similarity scores and other
  screens are unchanged. Hidden random identities are never fabricated.
  Other arbitrary notes are searchable evidence, not automatically executable
  recommendation rules. New conditions, artifacts requiring new algorithms,
  recognition methods, layouts and executable behavior require an APK update.

ZIP payloads cannot contain code. Schema v1 accepts only learning/ and
hero_recognition/ paths and supported text/image extensions, at most 5000 files,
including JSONL observation archives (data only, never executed),
16 MiB each, 128 MiB total and 128 MiB compressed. SHA-256 protects consistency;
authenticity relies on HTTPS and write access to the private GitHub repository,
not an independent publisher signature.

Installation stages files in a fresh app-private directory. AtomicFile switches
the on-disk pointer only after all validation succeeds. Failure/cancellation keeps
the pointer unchanged. The running process continues using its old immutable
source until restart. Next launch checks hashes again and falls back to the
previous verified bundle, then bundled APK assets, if necessary. Older/equal
channel versions cannot overwrite a newer saved version. A repaired release must
use a higher version. Previous bundles are retained; storage is checked before
downloading. Automatic pruning is intentionally not implemented.

## Publishing

Run PowerShell 7 at the repository root, with GitHub CLI already authenticated:

```powershell
./tools/Publish-RemoteLearning.ps1 -Publish -IncludeSource
```

For later data-only updates omit `-IncludeSource`. Edit the existing assets first.
The default version is a monotonic timestamp; pass a larger `-Version` if needed.
Without `-Publish`, this only builds an unpublished local bundle.
Do not edit the release payload in place or manually decrease the channel version.

The script publishes the named source files and channel as one fast-forward-only
GitHub API commit; it does not stage, reset or commit the local worktree. If main
changes concurrently it fails rather than overwrite that commit. A failed publish
may leave an unreferenced release; the old app channel remains usable.

## Evidence

Build/package success, GitHub publication and device execution are distinct.
The device must still confirm token persistence, private download, restart
activation, offline consumption and tracker flicker behavior. The safety unit
check is `RemoteLearningSafetyTest`; adding it does not imply it was executed.

References: https://docs.github.com/en/rest/releases/assets#get-a-release-asset
and https://developer.android.com/privacy-and-security/keystore
