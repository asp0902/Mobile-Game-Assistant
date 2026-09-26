$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
# Always use this entry point for the complete September 17 snapshot.
& "$PSScriptRoot/Save-LearningSnapshot.ps1"
$asset = Join-Path $repo 'app/src/main/assets/learning/game_knowledge_20260917.json'
$root = Get-Content -LiteralPath $asset -Raw -Encoding utf8 | ConvertFrom-Json -AsHashtable -Depth 100
$document = Get-Content -LiteralPath (Join-Path $repo 'docs/prosperity-learning-20260917.md') -Raw -Encoding utf8
$sections = [regex]::Split($document, '(?m)(?=^## )')
$entries = @($root.entries | Where-Object { $_.id -notlike 'prosperity-20260917-*' })
$n = 0
foreach ($section in $sections) {
    if ([string]::IsNullOrWhiteSpace($section)) { continue }
    $n++
    $title = ($section -split "`n", 2)[0].TrimStart('#', ' ').Trim()
    $entries += @{ id = "prosperity-20260917-$n"; title = "번창 파티 | $title"; text = $section.Trim() }
}
$root.entries = $entries
$root.prosperityParty = @{
    scope = '황폐한 영역 > 번창 파티 > 거대 골렘'
    bestObserved = @{ damageM = 121; damagePercent = 89.6; rank = 193; earlyDeaths = $false; cleared = $false; party = @('에이론','솔리스','아로라','라이카','퀸'); echo = '파진술 +18' }
    auroraExclusiveLevel = 10
    auroraExclusive15Available = $false
    observationCount = 10
    source = 'docs/prosperity-learning-20260917.md'
    application = 'Offline learning entries only; do not replace another mode overlay.'
}
$json = $root | ConvertTo-Json -Depth 100
[IO.File]::WriteAllText($asset, $json, [Text.UTF8Encoding]::new($false))
$archiveRoot = Join-Path (Split-Path $repo -Parent) '학습보관'
$archive = Get-ChildItem -LiteralPath $archiveRoot -Directory | Where-Object Name -Match '^\d{8}-\d{6}$' | Sort-Object Name -Descending | Select-Object -First 1
if (-not $archive) { throw 'Snapshot archive was not created.' }
Copy-Item -LiteralPath $asset -Destination (Join-Path $archive.FullName 'game_knowledge_20260917.json')
Copy-Item -LiteralPath (Join-Path $repo 'docs/prosperity-learning-20260917.md') -Destination $archive.FullName
$noteDir = Join-Path $env:USERPROFILE '.codex/memories/extensions/ad_hoc/notes'
New-Item -ItemType Directory -Path $noteDir -Force | Out-Null
$note = "# AFK 번창 파티 최신 학습`n사용자 요청으로 전체 대화 보관 및 APK 학습 자료 반영. $($archive.FullName)`n최고 관측 121M/89.6%/193위, 에이론·솔리스·아로라·라이카·퀸, 파진술+18. 조기 사망 없음 사용자 확인. 100% 클리어 아님. 아로라+10, 단기+15 불가. 망령의 소굴505M/98%와 별도 모드. 최신 자료 재생성은 tools/Save-ProsperityLearning.ps1 사용. 상세 조건·10회 실험·잠금 스킬·미확인 가설은 docs/prosperity-learning-20260917.md. 장치 설치/검증은 별도.`n"
[IO.File]::WriteAllText((Join-Path $noteDir "$(Get-Date -Format yyyyMMdd-HHmmss)-afk-prosperity-final.md"), $note, [Text.UTF8Encoding]::new($false))
Write-Output "Complete snapshot with prosperity learning: $($archive.FullName)"
