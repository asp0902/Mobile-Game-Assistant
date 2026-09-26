$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
$root = Get-Content -LiteralPath (Join-Path $repo 'app/src/main/assets/learning/game_knowledge_20260917.json') -Raw | ConvertFrom-Json -Depth 100
if ($root.account.seasonResonance -ne 412 -or $root.preservationUpdate.apkVersionCode -ne 10) { throw 'Latest account or APK revision lost' }
foreach ($id in @('arena3-result-current','phantom141-current')) {
    if (-not ($root.entries | Where-Object id -EQ $id)) { throw "Missing $id" }
}
if ($root.conversationIndex.Count -lt 810) { throw 'Conversation history regressed' }
Write-Output 'Current learning preservation check passed'
