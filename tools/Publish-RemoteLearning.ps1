param(
    [long]$Version = [long](Get-Date -Format 'yyyyMMddHHmmss'),
    [switch]$Publish,
    [switch]$IncludeSource
)
$ErrorActionPreference = 'Stop'
$OutputEncoding = [System.Text.UTF8Encoding]::new($false)
$repo = 'asp0902/Mobile-Game-Assistant'
$root = Split-Path $PSScriptRoot -Parent
$assets = Join-Path $root 'app/src/main/assets'
$out = Join-Path $root "build/remote-learning/$Version"
if (Test-Path -LiteralPath $out) { throw 'Output version already exists. Use a new version.' }
New-Item -ItemType Directory -Path $out -Force | Out-Null

function Api([string]$Endpoint, [string]$Method = 'GET', $Body = $null) {
    if ($null -eq $Body) { $result = & gh api $Endpoint --method $Method }
    else { $result = ($Body | ConvertTo-Json -Depth 100 -Compress) | & gh api $Endpoint --method $Method --input - }
    if ($LASTEXITCODE -ne 0) { throw "GitHub API failed: $Method $Endpoint" }
    if ($result) { return ($result -join "`n" | ConvertFrom-Json) }
}

$head = $null
if ($Publish) {
    $metadata = Api "repos/$repo"
    if (!$metadata.private -or $metadata.default_branch -ne 'main') { throw 'Require private repository with main branch.' }
    $head = Api "repos/$repo/git/ref/heads/main"
    $commit = Api "repos/$repo/git/commits/$($head.object.sha)"
    $base = Api "repos/$repo/git/trees/$($commit.tree.sha)?recursive=1"
    if ($base.truncated) { throw 'Repository tree truncated; cannot safely locate existing channel.' }
    $previousChannel = $base.tree | Where-Object path -EQ 'remote-learning/channel.json'
    if ($previousChannel) {
        $blob = Api "repos/$repo/git/blobs/$($previousChannel.sha)"
        $prior = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($blob.content)) | ConvertFrom-Json
        if ($Version -le [long]$prior.version) { throw 'Version must be newer than the published channel.' }
    }
}
if ($Version -le 2026091801) { throw 'Version must exceed bundled baseline 2026091801.' }

$files = @(Get-ChildItem -LiteralPath (Join-Path $assets 'learning'), (Join-Path $assets 'hero_recognition') -File -Recurse | Sort-Object FullName)
if ($files.Count -gt 5000) { throw 'Too many files for client format v1.' }
$records = @()
$zipPath = Join-Path $out 'learning.zip'
Add-Type -AssemblyName System.IO.Compression
$stream = [IO.File]::Open($zipPath, [IO.FileMode]::CreateNew)
$zip = [IO.Compression.ZipArchive]::new($stream, [IO.Compression.ZipArchiveMode]::Create)
try {
    foreach ($file in $files) {
        $relative = [IO.Path]::GetRelativePath($assets, $file.FullName).Replace('\', '/')
        if ($relative.Length -gt 240 -or $relative.Split('/') -contains '..' -or $relative -notmatch '^(learning|hero_recognition)/.+\.(json|jsonl|png|jpg|jpeg|webp|md|txt|csv|html)$') { throw "Unsupported data file: $relative" }
        if ($file.Length -gt 16MB) { throw "File exceeds client limit: $relative" }
        $records += [ordered]@{path=$relative; size=$file.Length; sha256=(Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash.ToLowerInvariant()}
        [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, $file.FullName, $relative, [IO.Compression.CompressionLevel]::Optimal) | Out-Null
    }
} finally { $zip.Dispose(); $stream.Dispose() }
if (($files | Measure-Object Length -Sum).Sum -gt 128MB -or (Get-Item -LiteralPath $zipPath).Length -gt 128MB) { throw 'Bundle exceeds client format v1 limit.' }
$channel = [ordered]@{
    formatVersion=1; version=$Version; minAppVersion=15; assetId=0
    zipSize=(Get-Item -LiteralPath $zipPath).Length
    zipSha256=(Get-FileHash -LiteralPath $zipPath -Algorithm SHA256).Hash.ToLowerInvariant()
    files=$records
}
if (!$Publish) {
    $channel | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath (Join-Path $out 'channel-unpublished.json') -Encoding utf8NoBOM
    Write-Output "Prepared only: $zipPath (assetId=0 is not publishable)"
    return
}

$tag = "learning-data-$Version"
$release = Api "repos/$repo/releases" 'POST' @{
    tag_name=$tag; target_commitish=$head.object.sha; name="Learning data $Version"
    body='Private learning data. APK v15 or later; manual update and app restart required. Includes app-icon overlay control and scoped user-confirmed starting heroes. No tokens or executable update payloads.'
    draft=$true; prerelease=$false
}
& gh release upload $tag $zipPath --repo $repo
if ($LASTEXITCODE -ne 0) { throw 'Release upload failed; channel unchanged.' }
$uploaded = @(Api "repos/$repo/releases/$($release.id)/assets") | Where-Object name -EQ 'learning.zip'
if (@($uploaded).Count -ne 1 -or $uploaded.size -ne $channel.zipSize -or $uploaded.state -ne 'uploaded') { throw 'Incomplete asset upload; channel unchanged.' }
$channel.assetId = [long]$uploaded.id
Api "repos/$repo/releases/$($release.id)" 'PATCH' @{draft=$false; make_latest='false'} | Out-Null

$changes = @([ordered]@{path='remote-learning/channel.json'; mode='100644'; type='blob'; content=($channel | ConvertTo-Json -Depth 10 -Compress)})
if ($IncludeSource) {
    $paths = @(
        'app/build.gradle.kts', 'app/src/main/AndroidManifest.xml',
        'app/src/main/java/com/asp0902/mobilegameassistant/learning/RemoteLearningStore.kt',
        'app/src/main/java/com/asp0902/mobilegameassistant/learning/LearnedKnowledgeRepository.kt',
        'app/src/main/java/com/asp0902/mobilegameassistant/learning/LearningScreen.kt',
        'app/src/main/java/com/asp0902/mobilegameassistant/analysis/InitialFormationRules.kt',
        'app/src/main/java/com/asp0902/mobilegameassistant/analysis/InitialFormationAdvisor.kt',
        'app/src/main/java/com/asp0902/mobilegameassistant/analysis/HonorDuelShopAnalyzer.kt',
        'app/src/main/java/com/asp0902/mobilegameassistant/analysis/HeroRecognition.kt',
        'app/src/main/java/com/asp0902/mobilegameassistant/tracking/TrackingViewModel.kt',
        'app/src/main/java/com/asp0902/mobilegameassistant/overlay/RecommendationOverlayController.kt',
        'app/src/main/assets/learning/initial_formation_rules.json',
        'app/src/main/assets/learning/honor_duel_portraits.json',
        'app/src/main/assets/learning/honor_initial_confirmations.json',
        'app/src/test/java/com/asp0902/mobilegameassistant/learning/RemoteLearningSafetyTest.kt',
        'app/src/test/java/com/asp0902/mobilegameassistant/analysis/HonorDuelStartingRarityTest.kt',
        'app/src/test/java/com/asp0902/mobilegameassistant/analysis/HonorDuelUserConfirmationTest.kt',
        'tools/Publish-RemoteLearning.ps1', 'docs/remote-learning.md'
    )
    foreach ($path in $paths) {
        $changes += [ordered]@{path=$path; mode='100644'; type='blob'; content=[IO.File]::ReadAllText((Join-Path $root $path))}
    }
}
$tree = Api "repos/$repo/git/trees" 'POST' @{base_tree=$commit.tree.sha; tree=$changes}
$new = Api "repos/$repo/git/commits" 'POST' @{message="Publish private learning data $Version"; tree=$tree.sha; parents=@($head.object.sha)}
# Fast-forward only: never overwrite a concurrent push or alter the local worktree/index.
Api "repos/$repo/git/refs/heads/main" 'PATCH' @{sha=$new.sha; force=$false} | Out-Null
$channel | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath (Join-Path $out 'channel.json') -Encoding utf8NoBOM
Write-Output "Published version=$Version files=$($records.Count) commit=$($new.sha) release=$($release.html_url)"
