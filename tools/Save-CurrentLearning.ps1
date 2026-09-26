$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
$workspace = Split-Path $repo -Parent
$assets = Join-Path $repo 'app/src/main/assets/learning'
$path = Join-Path $assets 'game_knowledge_20260917.json'
$before = [IO.File]::ReadAllText($path)
$root = $before | ConvertFrom-Json -AsHashtable -Depth 100
$backup = Join-Path $workspace ('학습보관/' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '-pre-full-export.json')
[IO.File]::WriteAllText($backup, $before)
try {
    $result = (& "$PSScriptRoot/Save-LearningSnapshot.ps1" | Out-String) | ConvertFrom-Json
    $fresh = Get-Content -LiteralPath $path -Raw | ConvertFrom-Json -AsHashtable -Depth 100
} catch {
    [IO.File]::WriteAllText($path, $before)
    throw
}
# Preserve authoritative account, rank, mode and correction data; refresh only the transcript and archive index.
$root.conversationIndex = $fresh.conversationIndex
$root.archive = $fresh.archive
$root.scope = '전체 대화 원문 및 기존 학습 보존. 최신: 계정 승급, 아레나1~3 결과, 시즌 팬텀141 미실험 교체안. 모드별 조건과 확정/추정 구분.'
$root.entries = @($root.entries | Where-Object { $_.id -ne 'archive' }) + @($fresh.entries | Where-Object { $_.id -eq 'archive' })
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
if ($root.account.seasonResonance -ne 416) {
    $root.accountHistory = @($root.accountHistory) + @(($root.account | ConvertTo-Json -Depth 100 | ConvertFrom-Json -AsHashtable -Depth 100))
}
$root.account.asOf = $stamp
$root.account.seasonResonance = 416
$root.account.phantomContractHeroLevel = $null
$root.account.separateContractLevels = @{'오렐리안'=27;'포이즌 스포아'=27;'오우거'=27;'네크로 드래곤'=28;'백야의 글로리 헌터'=28}
$root.account.phantomContractLevelNote = '사용자 최신 명칭과 개별 레벨 그대로 보존. 과거 단일26은 당시 캡처이며 현재 공통 레벨 아님.'
$root.snapshotDate = '2026-09-18'
$root.scope += ' 최신 사용자 정정: 아로라 챔피언, 공명416, 계약27/28, 워커 에픽. ChatGPT 학습/Codex 코딩 역할 분리.'
$aurora = $root.heroes | Where-Object name -EQ '아로라' | Select-Object -First 1
if (-not $aurora) { throw 'Aurora record missing; refusing to invent a replacement' }
if ($aurora.rank -ne '챔피언') { $aurora.rankHistory = @($aurora.rankHistory) + @(@{rank=$aurora.rank;asOf=$aurora.asOf}) }
$aurora.rank = '챔피언'
$aurora.rankEvidence = 'USER_EXPLICIT: 아로라 진급 결과 챔피언'
$aurora.asOf = $stamp
$aurora.unlockNote = '새 스킬/전용 장비 강화 수치는 제공되지 않음. 기존 초상은 과거 모습이며 챔피언 테두리 학습 샘플 아님.'
$walker = $root.heroes | Where-Object name -EQ '워커' | Select-Object -First 1
if (-not $walker) {
    $walker = @{id='walker';name='워커';faction='레오프론';role='사수';owned=$true;portraitStatus='CHECK'}
    $root.heroes = @($root.heroes) + @($walker)
}
$walker.rank = '에픽'
$walker.previousRankReported = '엘리트+'
$walker.rankEvidence = 'USER_EXPLICIT: 엘리트+ -> 에픽'
$walker.asOf = $stamp
$update = @{asOf=$stamp;source='USER_EXPLICIT';seasonResonance=416;phantomContracts=$root.account.separateContractLevels;promotions=@(@{name='아로라';rank='챔피언'},@{name='워커';from='엘리트+';rank='에픽'});workflow='ChatGPT: 학습/분석/추천. Codex: 코딩/자료 반영/APK 빌드.'}
$root.accountUpdates = @($root.accountUpdates) + @($update)
$root.entries = @($root.entries | Where-Object { $_.id -notin @('account','hero-aurora','hero-walker','account-current-v11') }) + @(
    @{id='account';title='최신 계정: 시즌 공명416 / 팬텀 계약 개별27·28';text=($root.account | ConvertTo-Json -Depth 30)},
    @{id='hero-aurora';title='아로라 · 챔피언 / 반신 / 마법사';text=($aurora | ConvertTo-Json -Depth 30);portrait=$aurora.portraitAsset},
    @{id='hero-walker';title='워커 · 에픽 / 레오프론 / 사수';text=($walker | ConvertTo-Json -Depth 30);portrait=($walker.portraitAsset ?? '')},
    @{id='account-current-v11';title='최신 정정 우선 · 아로라 챔피언 / 워커 에픽 / 공명416';text=($update | ConvertTo-Json -Depth 30)}
)
$arena = Get-Content -LiteralPath (Join-Path $workspace '학습보관/20260917-235030-arena3-dream-pursuit/LEARNING.md') -Raw
$phantom = @'
배틀 모드 > 일반 콘텐츠 > 시즌 자동사냥 스테이지 > 팬텀 도전 141.
사용자 캡처: 아군412레벨, 팬텀26레벨, 메아리+18, 아군10,865K/적5951K.
현재 편성: 루시우스/후긴/로완/세미라/에디. 전방 루시우스, 왼쪽 후긴, 중앙 세미라, 중앙 왼쪽 에디, 오른쪽 뒤 로완.
미실험 추천: 에디 자리만 아로라로 교체. 나머지4명/배치/메아리 유지. 레오프론3명 팬텀 구성 유지 목적. 클리어 결과 없음.
이전 확인 등급: 루시우스 에픽, 후긴 레전드+(최근 승급 재확인 없음), 로완 레전드(최근 승급 재확인 없음), 세미라 신화, 에디 레전드, 아로라 신화+.
등급은 현재 이미지 테두리로 추정하지 않는다. 최종 추천은 사용자 개별 초상화+한국어 이름+확인된 등급+진영+직업 포함. 로완/에디는 기존 사용자 정정 초상 우선; 직전 답변의 임시 이미지 경로를 정정 원본보다 우선하지 않는다.
아레나240레벨/+10, 무한 적 강화 규칙은 이 시즌 팬텀에 적용하지 않는다. 다른 콘텐츠 아로라 성적은 이번 우월성의 증명이 아니다.
현재 전투 결과 미제공: 성공/실패/사망 원인 추정 금지. 다음 비교는 클리어 여부 및 첫 사망 영웅/표시 시간 중심.
참고 외부 설명: https://www.prydwen.gg/afk-journey/characters/shemira (다수 적 상대 설명; 팬텀141 실증 아님).
'@
$root.entries = @($root.entries | Where-Object { $_.id -notin @('arena3-result-current','phantom141-current') }) + @(
    @{id='arena3-result-current';title='아레나3 일반6/6 · 무한8처치 · 꿈의 추격 재개';text=$arena},
    @{id='phantom141-current';title='시즌 팬텀141 · 현재 진형 / 에디→아로라 미실험';text=$phantom}
)
$imageDir = Join-Path $assets 'evidence-current'
[IO.Directory]::CreateDirectory($imageDir) | Out-Null
$sources = @(Get-ChildItem -LiteralPath (Join-Path $workspace '학습보관/20260917-235030-arena3-dream-pursuit') -Filter '*.png')
$sources += Get-Item -LiteralPath "$env:TEMP/codex-clipboard-7899c9c2-f003-42ed-ac80-a874b923c413.png"
foreach ($image in $sources) {
    Copy-Item -LiteralPath $image.FullName -Destination (Join-Path $imageDir $image.Name) -Force
    $id = 'evidence-' + $image.BaseName
    if (-not ($root.entries | Where-Object id -EQ $id)) {
        $root.entries += @{id=$id;title=('원본 캡처 · ' + $image.BaseName);text='사용자 제공 아레나3 또는 팬텀141 근거. 모드명과 일반/무한 표시를 확인. 영웅 개별 초상으로 사용하지 않음.';portrait=('learning/evidence-current/' + $image.Name)}
    }
}
$root.preservationUpdate = @{previousBackup=$backup;latestProposalStatus='UNTESTED';apkVersionCode=11;deviceValidated=$false}
$json = $root | ConvertTo-Json -Depth 100
[IO.File]::WriteAllText($path, $json)
[IO.File]::WriteAllText((Join-Path $result.archive 'game_knowledge_20260917.json'), $json)
$report = "# 전체 학습 보관 및 APK v11 자료`n`n최신: 아로라 챔피언, 워커 에픽, 시즌공명416, 팬텀별27/28. 과거 전투의 등급/레벨을 소급 변경하지 않음.`n`n" + (($root.entries | ForEach-Object { "## $($_.title)`n`n$($_.text)" }) -join "`n`n")
[IO.File]::WriteAllText((Join-Path $result.archive 'LEARNING.md'), $report)
[IO.File]::WriteAllText((Join-Path $repo 'docs/learning-20260917.md'), $report)
Copy-Item -LiteralPath $assets -Destination (Join-Path $result.archive 'apk-learning-assets') -Recurse
$note = "# Complete AFK preservation requested`nAuthoritative latest archive: $($result.archive)`nRead LEARNING.md and game_knowledge_20260917.json. Existing account412, latest promotions, Yeti/Prosperity/Haunting/Arena records preserved. Arena3 normal6/endless8, deaths48 Shemira/Solise and33 Faramor. Phantom141 Edi-to-Aurora is UNTESTED; no battle result. Prior hardcoded exporter notes about account402 or support490 are historical, not current. APK v10 source includes offline reference and transcript, not trained model or automatic recommendation enforcement. Missing external attachments remain reported in manifest; raw inline images remain in full session. Build/device status must be reported separately."
$note = $note.Replace('account412','account416').Replace('APK v10','APK v11') + "`nLatest user updates: Aurora champion; Walker elite+ to epic; resonance416. Contracts: 오렐리안/포이즌 스포아/오우거27, 네크로 드래곤/백야의 글로리 헌터28. Single phantom level26 is historical, not current. ChatGPT learns; Codex codes."
$notePath = Join-Path 'C:/Users/asp92/.codex/memories/extensions/ad_hoc/notes' ((Split-Path $result.archive -Leaf) + '-afk-learning-apk.md')
[IO.File]::WriteAllText($notePath, $note)
$result | Add-Member -NotePropertyName preservedEntries -NotePropertyValue $root.entries.Count
$result | ConvertTo-Json
