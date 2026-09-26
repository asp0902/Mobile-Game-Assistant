$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
$workspace = Split-Path $repo -Parent
$assets = Join-Path $repo 'app/src/main/assets/learning'
$asset = Join-Path $assets 'game_knowledge_20260917.json'
$utf8 = [Text.UTF8Encoding]::new($false)
# Preserve existing data before the historical generator runs.
$previous = Get-Content -LiteralPath $asset -Raw -Encoding utf8 | ConvertFrom-Json -AsHashtable -Depth 100
& "$PSScriptRoot/Save-ProsperityLearning.ps1"
$root = Get-Content -LiteralPath $asset -Raw -Encoding utf8 | ConvertFrom-Json -AsHashtable -Depth 100
$archive = $root.archive.localPath
foreach ($key in $previous.Keys) { if (-not $root.Contains($key)) { $root[$key] = $previous[$key] } }
foreach ($entry in $previous.entries) { if ($entry.id -notin $root.entries.id) { $root.entries += $entry } }
foreach ($hero in $previous.heroes) { if ($hero.id -notin $root.heroes.id) { $root.heroes += $hero } }
function Write-Json($path, $value) { [IO.File]::WriteAllText($path, (ConvertTo-Json -InputObject $value -Depth 100), $utf8) }
Write-Json (Join-Path $archive 'previous-knowledge.json') $previous
$root.policy.replacementRule = @{ minimum = 'equal_or_higher_confirmed_rank'; rejectUnknownRank = $true; ownedOnly = $true; source = 'user_explicit'; oneChangePerTrial = $true }
$root.policy.nonSeasonExclusions = @('unverified_transfer_of_season_effects')
$root.policy.modeEvidence = 'Shown level402/echo+18 must be preserved; actual effect applicability is separate evidence.'
$root.policy.videoRequired = $false
$root.policy.requiredHeroDisplay = @('user_individual_portrait_or_CHECK','name','confirmed_rank_or_CHECK','faction','class')
$root.nameCorrections += @{ wrong = '데미안'; correct = '다미안' }
$definitions = @(
    @('ulmus','울머스','와일더스','탱커','레전드','244bad69-d799-4c26-9795-bd2c0a98afd4','yeti-normal2-601m/ulmus.png'),
    @('solise','솔리스','와일더스','서포터','신화','b6ac6fc2-cbd0-4b14-aea4-75c9fd46627d','yeti-normal2-601m/solise.png'),
    @('quinn','퀸','와일더스','서포터','레전드','e0f243a1-c953-499f-9850-7d8756a2fc36','yeti-normal2-601m/quinn.png'),
    @('faramor','팔라모르','와일더스','레인저','레전드','8fb22290-4c7b-4c1b-8462-749e5b83fd89','yeti-normal2-700m/faramor.png'),
    @('shemira','세미라','그레이브본','마법사','신화','697c132f-b212-4706-a170-f0c82ca61044','yeti-normal2-704m/shemira.png'),
    @('eddy','에디','트라이브','사수','레전드','b05e7dcd-89b9-44e2-8818-4ef6b6129907',''),
    @('aurora','아로라','반신','마법사','신화+','',''),
    @('damian','다미안','와일더스','서포터','에픽+','',''),
    @('eironn','에이론','와일더스','레인저','에픽','','')
)
$portraitIssues = @()
foreach ($d in $definitions) {
    $hero = $root.heroes | Where-Object id -EQ $d[0] | Select-Object -First 1
    if (-not $hero) { $hero = @{ id = $d[0] }; $root.heroes += $hero }
    $hero.name=$d[1]; $hero.faction=$d[2]; $hero.role=$d[3]; $hero.rank=$d[4]; $hero.owned=$true
    $hero.rankEvidence='사용자 명시 확인, 테두리 추론 아님'; $hero.asOf='2026-09-17'
    if ($d[5]) {
        $source = Join-Path $env:TEMP "codex-clipboard-$($d[5]).png"
        if (-not [IO.File]::Exists($source) -and $d[6]) { $source = Join-Path $workspace "학습보관/$($d[6])" }
        if ([IO.File]::Exists($source)) {
            Copy-Item -LiteralPath $source -Destination "$assets/portraits-20260917/$($d[0]).png"
            $hero.portraitAsset="learning/portraits-20260917/$($d[0]).png"; $hero.portraitSource=$source; $hero.portraitStatus='USER_CONFIRMED'
        } else { $portraitIssues += @{hero=$d[1];source=$source;status='UNAVAILABLE'} }
    }
    # Existing individually sourced portraits remain valid; never replace them with roster screenshots.
    if (-not $hero.portraitAsset) {
        $candidate = "hero_recognition/portraits/$($d[0]).png"
        $manifest = Join-Path $repo 'app/src/main/assets/hero_recognition/hero_manifest.csv'
        $hero.portraitStatus='CHECK'; $hero.portraitNote='Previously supplied individual portrait; association must be recovered from source evidence, not a roster position.'
        $portraitIssues += @{hero=$d[1];status='ASSOCIATION_PENDING';candidate=$candidate;manifest=$manifest}
    }
}
$runs = @'
[
{"id":"yeti-413","difficulty":"initial","damageM":413,"percent":100,"rankingPercent":7.90,"party":["울머스","에디","아로라","퀸","다미안"],"damage":[16.557,66.836,296,17.928,15.612],"healing":[2.885,0,0,17,1.158],"received":[25.693,11.015,33.392,11.066,5.812],"boss":[22.857,1315],"deathStatus":"UNKNOWN"},
{"id":"yeti-549","damageM":549,"percent":41.9,"rankingPercent":7.20,"party":["울머스","에디","아로라","퀸","다미안"],"damage":[25.293,26.252,449,25.144,23.063],"healing":[9.403,0,0,175,3.521],"received":[90.463,14.804,211,74.603,49.155],"boss":[229,1081],"firstDeath":{"hero":"에디","reportedSeconds":58}},
{"id":"yeti-550","damageM":550,"percent":42,"rankingPercent":7.31,"party":["울머스","다미안","아로라","퀸","에디"],"damage":[25.141,23.643,449,25.321,26.791],"healing":[8.589,5.253,0,175,0],"received":[92.809,49.574,212,74.975,14.879],"boss":[232,1086],"firstDeath":{"hero":"에디","reportedSeconds":58}},
{"id":"yeti-601","damageM":601,"percent":45.9,"rankingPercent":7.10,"party":["울머스","다미안","아로라","퀸","솔리스"],"damage":[27.407,26.900,451,29.008,67.345],"healing":[9.015,2.818,0,182,77.313],"received":[155,46.662,190,80.164,80.395],"boss":[288,1271],"deathStatus":"USER_CONFIRMED_NONE"},
{"id":"yeti-700","damageM":700,"percent":53.4,"rankingPercent":6.50,"party":["울머스","팔라모르","아로라","퀸","솔리스"],"damage":[25.607,142,442,27.762,62.241],"healing":[6.509,0,0,217,142],"received":[137,138,232,90.723,91.108],"boss":[384,1580],"deathStatus":"USER_CONFIRMED_NONE"},
{"id":"yeti-687","damageM":687,"percent":52.4,"rankingPercent":6.60,"party":["에이론","팔라모르","아로라","퀸","솔리스"],"damage":[14.994,149,434,27.627,60.953],"healing":[0,0,0,206,123],"received":[31.653,125,267,90.316,90.240],"boss":[367,1496],"firstDeath":{"hero":"에이론","reportedSeconds":60},"supersededByPolicy":"no rank downgrade"},
{"id":"yeti-704","damageM":704,"percent":53.7,"rankingPercent":6.81,"party":["울머스","세미라","아로라","퀸","솔리스"],"damage":[22.986,204,391,26.342,58.761],"healing":[6.559,17.013,0,239,107],"received":[139,99.315,258,94.452,94.290],"boss":[388,1659],"deathStatus":"USER_CONFIRMED_NONE","bestObserved":true,"cleared":false},
{"id":"yeti-558","damageM":558,"percent":42.6,"rankingPercent":6.81,"party":["팔라모르","아로라","세미라","퀸","솔리스"],"damage":[27.154,299,151,25.572,54.930],"healing":[0,0,13.409,143,77.385],"received":[32.320,170,86.202,108,88.233],"boss":[307,1102],"firstDeath":{"hero":"팔라모르","reportedSeconds":60},"otherSurvival":"UNKNOWN","change":"울머스 → 팔라모르, 탱커 제거 실패"}
]
'@ | ConvertFrom-Json -AsHashtable
foreach ($run in $runs) {
    if (-not $run.difficulty) { $run.difficulty='노멀 II' }
    $run.units='M'; $run.source='user screenshots and explicit corrections, archived conversation'
}
$root.yeti = @{ scope='꿈의 세계 / 변형된 예티'; runs=$runs; bestObservedRun='yeti-704'; estimatedRemainingAttempts=0; attemptsStatus='historical conditional estimate, not current confirmed'; document='docs/yeti-learning-20260917.md' }
$root.scope='2026-09-17까지 사용자 학습. 모드별 기록/원문/정정 분리. 최신 전투: 예티558M, 최고704M.'
$root.entries = @($root.entries | Where-Object { $_.id -notlike 'hero-*' -and $_.id -notlike 'yeti-*' -and $_.id -ne 'policy' })
$root.entries = @(@{id='policy';title='최신 고정 정책: 동급 이상 교체 / 사용자 개별 초상';text=($root.policy | ConvertTo-Json -Depth 20)}) + $root.entries
foreach ($hero in $root.heroes) {
    $root.entries += @{id="hero-$($hero.id)";title="$($hero.name) · $($hero.rank) / $($hero.faction) / $($hero.role)";text=($hero | ConvertTo-Json -Depth 20);portrait=($hero.portraitAsset ?? '')}
}
$doc = Get-Content -LiteralPath "$repo/docs/yeti-learning-20260917.md" -Raw -Encoding utf8
$root.entries += @{id='yeti-guide';title='변형된 예티: 최신 정정·해금·실험 결론';text=$doc}
foreach ($run in $runs) { $root.entries += @{id=$run.id;title="예티 $($run.difficulty): $($run.damageM)M / $($run.percent)%";text=($run | ConvertTo-Json -Depth 30)} }
$root.entries += @{id='yeti-portrait-gaps';title='개별 초상화 출처 확인 상태';text=($portraitIssues | ConvertTo-Json -Depth 10)}
$root.archive.portraitIssues=$portraitIssues
Write-Json $asset $root
Write-Json "$archive/game_knowledge_20260917.json" $root
Copy-Item -LiteralPath "$repo/docs/yeti-learning-20260917.md" -Destination $archive
Copy-Item -LiteralPath "$assets/portraits-20260917" -Destination "$archive/portraits-20260917" -Recurse
$finalArchive = Join-Path $workspace '학습보관/yeti-normal2-558m'
New-Item -ItemType Directory -Path $finalArchive -Force | Out-Null
Write-Json "$finalArchive/record.json" $runs[-1]
$sources=@{formation='5afd1959-cf74-4a32-bf83-d283ac5587b3';result='a617ad12-bf63-4cec-baf4-266c587b80e8';statistics='ec3d1b71-6065-47ab-a669-ccd6db0ed2d6'}
foreach ($key in $sources.Keys) {
    $source=Join-Path $env:TEMP "codex-clipboard-$($sources[$key]).png"
    if ([IO.File]::Exists($source)) { Copy-Item -LiteralPath $source -Destination "$finalArchive/$key.png" }
}
$old704 = Join-Path $workspace '학습보관/yeti-normal2-704m/record.json'
$record704 = Get-Content -LiteralPath $old704 -Raw -Encoding utf8 | ConvertFrom-Json -AsHashtable
$record704.result.deathStatus='사용자 전원 생존 확인'; $record704.result.deaths=@()
Write-Json $old704 $record704
Get-ChildItem -LiteralPath (Join-Path $workspace '학습보관') -Directory -Filter 'yeti-normal2-*' | ForEach-Object { Copy-Item -LiteralPath $_.FullName -Destination $archive -Recurse }
$report = "# 통합 학습 원장`n`n" + (($root.entries | ForEach-Object { "## $($_.title)`n`n$($_.text)" }) -join "`n`n")
[IO.File]::WriteAllText("$archive/LEARNING.md", $report, $utf8)
$note = "# AFK complete learning refresh requested by user`nCanonical archive: $archive`nAPK target v9. Build/device status must be reported separately.`nLatest entry point tools/Save-YetiLearning.ps1 supersedes earlier snapshot entry points. Preserves prior modes and all conversation. User requires equal-or-higher confirmed rank only, individual user portraits/name/rank/faction/class. Aurora mythic+, Edi legendary. Yeti Normal II best704M/53.7% Ulmus/Shemira/Aurora/Quinn/Solise, all alive user confirmed. Last558M/42.6%, Faramor died60sec after replacing Ulmus; failed tankless experiment. See integrated LEARNING.md, portraitIssues and media-manifest.json; unavailable sources are not claimed recovered. No device installation or automated formation engine claimed.`n"
$notes=Join-Path $env:USERPROFILE '.codex/memories/extensions/ad_hoc/notes'
[IO.File]::WriteAllText("$notes/$(Get-Date -Format yyyyMMdd-HHmmss)-afk-yeti-complete.md",$note,$utf8)
Write-Output "YETI_ARCHIVE=$archive"
Write-Output "YETI_RUNS=$($runs.Count); PORTRAIT_ISSUES=$($portraitIssues.Count)"
