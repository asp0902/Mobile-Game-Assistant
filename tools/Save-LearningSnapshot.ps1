param(
    [string]$SessionPath = 'C:\Users\asp92\.codex\sessions\2026\08\25\rollout-2026-08-25T16-25-17-01a037cf-2522-74c0-9787-cf38606eb2fa.jsonl'
)
$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
$workspace = Split-Path $repo -Parent
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$archive = Join-Path $workspace "학습보관\$stamp"
$assets = Join-Path $repo 'app\src\main\assets\learning'
$utf8 = [System.Text.UTF8Encoding]::new($false)
foreach ($path in @($archive, "$archive\media", "$assets\portraits-20260917", "$assets\conversation-20260917")) {
    [System.IO.Directory]::CreateDirectory($path) | Out-Null
}

# Preserve the entire source first. The APK gets conversation text, not private tool/system logs.
Copy-Item -LiteralPath $SessionPath -Destination "$archive\session-original.jsonl"
$messages = [System.Collections.Generic.List[object]]::new()
$media = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
$parseIssues = [System.Collections.Generic.List[object]]::new()
$conversationIndex = [System.Collections.Generic.List[object]]::new()
$lineNumber = 0
$messageNumber = 0
$writer = [System.IO.StreamWriter]::new("$archive\conversation.jsonl", $false, $utf8)
try {
    foreach ($line in [System.IO.File]::ReadLines("$archive\session-original.jsonl")) {
        $lineNumber++
        try { $event = $line | ConvertFrom-Json -Depth 100 } catch {
            $parseIssues.Add(@{ line = $lineNumber; error = $_.Exception.Message }); continue
        }
        if ($event.type -ne 'response_item' -or $event.payload.type -ne 'message' -or $event.payload.role -notin @('user', 'assistant')) { continue }
        $payload = $event.payload
        $texts = @($payload.content | Where-Object { $_.type -in @('input_text', 'output_text', 'text') } | ForEach-Object { $_.text })
        $body = $texts -join "`n`n"
        $writer.WriteLine((@{ timestamp = $event.timestamp; role = $payload.role; channel = $payload.channel; text = $body } | ConvertTo-Json -Depth 10 -Compress))
        foreach ($match in [regex]::Matches($body, '(?i)[A-Z]:[\\/][^\r\n<>"|*?]*?\.(?:png|jpe?g|webp|mp4|mov|pdf|zip)')) {
            [void]$media.Add($match.Value.Replace('/', '\'))
        }
        # Exclude app metadata from the on-device reader, while retaining it in the raw archive.
        if ([string]::IsNullOrWhiteSpace($body) -or $body.StartsWith('<environment_context>') -or $body.StartsWith('<recommended_plugins>')) { continue }
        $messageNumber++
        $heading = (($body -replace '\s+', ' ').Trim())
        if ($heading.Length -gt 90) { $heading = $heading.Substring(0, 90) }
        for ($offset = 0; $offset -lt $body.Length; $offset += 12000) {
            $part = [int]($offset / 12000) + 1
            $name = '{0:D5}-{1:D3}.txt' -f $messageNumber, $part
            $text = "[$($event.timestamp)] $($payload.role) / $($payload.channel)`n`n" + $body.Substring($offset, [Math]::Min(12000, $body.Length - $offset))
            [System.IO.File]::WriteAllText("$assets\conversation-20260917\$name", $text, $utf8)
            $conversationIndex.Add(@{ id = "conversation-$messageNumber-$part"; title = "$messageNumber.$part $($payload.role): $heading"; asset = "learning/conversation-20260917/$name" })
        }
    }
} finally { $writer.Dispose() }

$root = @'
{
  "schemaVersion": 1,
  "snapshotDate": "2026-09-17",
  "scope": "사용자 제공 AFK 대화와 2026-09-16 전투 스냅샷. 계정 최신 상태로 자동 간주하지 않음.",
  "nameCorrections": [
    {"wrong":"휴긴","correct":"후긴"},
    {"wrong":"Odie","correct":"에디"},
    {"wrong":"레니아","correct":"레미아"}
  ],
  "policy": {
    "priority": ["user_correction", "capture_name_and_rank", "ocr_or_portrait", "inference"],
    "requiredHeroDisplay": ["name", "faction", "role", "rank_and_date_or_CHECK", "user_portrait_or_CHECK"],
    "neverInfer": ["rank_from_border", "survival_from_damage_totals", "shield_from_healing_column", "unlock_from_hero_ownership", "clear_from_proposal", "live_attempts_from_old_messages"],
    "nonSeasonExclusions": ["season_echoes", "phantom_contract", "season_only_effects"],
    "readOnly": true,
    "autoTouch": false
  },
  "account": {
    "asOf": "2026-09-16",
    "seasonResonance": 402,
    "totalPowerMillion": 155,
    "classEquipment": {"마법사":390,"탱커":390,"전사":370,"레인저":348,"사수":353},
    "supportEquipment": {"userText":490,"capture":390,"status":"CONFLICT"},
    "phantomContractHeroLevel":26,
    "separateContractLevels":{"아우렐리안":20,"포이즌 스포어":20,"오우거":20,"네크로 드래곤":19,"백야의 영광 사냥꾼":19},
    "unownedHistorical": ["플로라벨", "릴리 메이", "코린"],
    "warnings": ["과거 계정 스냅샷: 현재 보유/등급을 재확인해야 한다.","코린과 콜린은 서로 다른 영웅이다.","진영 환수의 3명 조건은 시즌 환영 스테이지의 기록이며 다른 보스 모드에 자동 적용하지 않는다."]
  },
  "heroes": [
    {"id":"damian","name":"데미안","faction":"와일더스","role":"서포터","rank":"CHECK","portraitFile":"damian.png","portraitStatus":"USER_CONFIRMED","portraitConfirmedAt":"2026-09-17"},
    {"id":"lucius","name":"루시우스","faction":"레오프론","role":"탱커","rank":"에픽","rankEvidence":"사용자 정정 기록, 현재 승급 변동 미확인","owned":true,"portraitFile":"lucius-clear.png"},
    {"id":"hugin","name":"후긴","faction":"레오프론","role":"서포터","rank":"CHECK","historicalRank":"레전드+","rankEvidence":"최신 캡처에서 승급 재확인 안 됨","owned":true,"attackType":"물리","range":20,"portraitFile":"hugin.png"},
    {"id":"rowan","name":"로완","faction":"레오프론","role":"서포터","rank":"CHECK","historicalRank":"레전드","owned":true,"portraitFile":"rowan-corrected.png"},
    {"id":"aurora","name":"아로라","faction":"반신","role":"마법사","rank":"CHECK","owned":true,"range":5,"portraitFile":"aurora.png"},
    {"id":"matt_gabumon","name":"매튜&파피몬","faction":"이계","role":"마법사","rank":"CHECK","owned":true,"range":4,"portraitFile":"yamato.png"},
    {"id":"rolan","name":"로란 대제","faction":"반신","role":"서포터","rank":"CHECK","owned":true,"range":10,"exclusiveUnlocked":null,"portraitFile":"rolan.png"},
    {"id":"eddy","name":"에디","faction":"트라이브","role":"사수","rank":"CHECK","historicalRank":"레전드","owned":true,"portraitFile":"odie-corrected.png"},
    {"id":"erinndor","name":"에린도르","faction":"그레이브본","role":"마법사","rank":"CHECK","attackType":"마법","range":8,"capturedLevel":398,"capturedPowerK":1174},
    {"id":"kai","name":"카이","faction":"트라이브","role":"서포터","rank":"레전드","rankEvidence":"이전 사용자 캡처, 최신 변동 CHECK","attackType":"물리","range":4,"capturedLevel":398,"capturedPowerK":1364},
    {"id":"kruger","name":"크루거","faction":"트라이브","role":"전사","rank":"엘리트+","rankEvidence":"이전 사용자 캡처, 최신 변동 CHECK","attackType":"물리","range":1,"capturedLevel":398,"capturedPowerK":948},
    {"id":"nerion","name":"네리온","faction":"그레이브본","role":"사수","rank":"엘리트+","rankEvidence":"이전 사용자 캡처, 최신 변동 CHECK","attackType":"마법","range":7,"capturedLevel":398,"capturedPowerK":861},
    {"id":"shakitalis","name":"샤키탈리스","faction":"반신","role":"사수","rank":"CHECK","historicalRank":"에픽","range":5,"portraitFile":"shakitalis.png"},
    {"id":"cantias","name":"칸티아스","faction":"악마","role":"서포터","rank":"CHECK","range":6},
    {"id":"pandora","name":"판도라","faction":"이계","role":"서포터","rank":"CHECK","range":5}
  ],
  "echoes": [
    {
      "id":"recovery","name":"회복술","level":18,"seasonOnly":true,
      "stats":{"공격력%":6.5,"HP%":9.1,"활력":18.2},
      "nextPreview":{"공격력%":7,"HP%":9.8,"활력":19.6},
      "base":"전투 시작 6초부터 10초마다 HP가 가장 낮은 아군을 파티 공격력 50%만큼 치료. 이후 다른 HP 최저 아군에게 2회 튕기며 매번 치료 효과 25% 감소.",
      "unlockedUpgrades":["치료 계수 55%","치료 계수 60%","치료 계수 65%"],
      "effective":{"startSeconds":6,"intervalSeconds":10,"initialHealPartyAttackPercent":65,"bounces":2,"healReductionEachBouncePercent":25},
      "locked":[{"level":20,"effect":"치료 계수 70%"},{"level":25,"effect":"치료 계수 75%"},{"level":30,"effect":"3회 튕김, 튕길 때 치료 감소율 20%"}],
      "requiredSeasonResonanceShown":425,
      "sources":["codex-clipboard-79547258-ec1a-43bd-904b-22def36559a8.png","codex-clipboard-c5702d83-9cbf-463f-bbc7-11392f812121.png","codex-clipboard-8c32aa0d-0d60-4b4d-ba4e-9fc6178471e8.png"]
    },
    {
      "id":"rupture","name":"파진술","level":18,"seasonOnly":true,
      "stats":{"공격력%":7.8,"HP%":14.3,"급속":7.8},
      "nextPreview":{"공격력%":8.4,"HP%":15.4,"급속":8.4},
      "base":"전투 시작 3초부터 12초마다 6초간 아군 격려. 공격력 14%, 물리·마법 방어력 30% 증가, 초당 에너지 20 회복.",
      "unlockedUpgrades":["공격력 보너스 16%","공격력 보너스 18%","공격력 보너스 20%"],
      "effective":{"startSeconds":3,"intervalSeconds":12,"durationSeconds":6,"attackBonusPercent":20,"physicalAndMagicDefensePercent":30,"energyPerSecond":20},
      "locked":[{"level":20,"effect":"공격력 22%"},{"level":25,"effect":"공격력 24%"},{"level":30,"effect":"시전 시 아군 급속 추가 20"}],
      "requiredSeasonResonanceShown":425,
      "sources":["codex-clipboard-8e317c9c-90a3-44c6-b3de-295c703ce906.png","codex-clipboard-5b4044b0-8323-459b-8d5b-7e3d2f18ddbe.png","codex-clipboard-7249da63-4c5e-448a-96e6-8b3076f2d4dc.png"]
    },
    {
      "id":"light_spear","name":"관일술","level":18,"seasonOnly":true,
      "stats":{"공격력%":10.4,"물리·마법 방어력%":28.6,"HP%":10.4},
      "nextPreview":{"공격력%":11.2,"물리·마법 방어력%":30.8,"HP%":11.2},
      "base":"아군 궁극기마다 빛의 창 1개 생성. 3개 모이면 적이 가장 많은 축을 향해 3방향 발사. 각 창이 명중한 적 모두에게 파티 공격력 60% 고정 피해. 8초간 목표가 입히는 피해 20% 감소, 최대 2스택. 각 창의 첫 명중 적 2초 스턴.",
      "unlockedUpgrades":["피해 계수 64%","피해 계수 68%","피해 계수 72%"],
      "effective":{"alliedUltimatesRequired":3,"spears":3,"trueDamagePartyAttackPercentPerHit":72,"enemyDamageDealtReductionPercent":20,"debuffSeconds":8,"maxStacks":2,"firstHitStunSeconds":2},
      "locked":[{"level":20,"effect":"피해 계수 76%"},{"level":25,"effect":"피해 계수 80%"},{"level":30,"effect":"스킬 누적 3회 시전 후 각 창이 추가 1회 되돌아옴. 되돌아오는 피해량은 40%로 감소"}],
      "warnings":["단일 보스에 세 창이 모두 명중한다고 가정하지 않음","목표가 받는 피해 증가가 아니라 목표가 입히는 피해 감소"],
      "requiredSeasonResonanceShown":425,
      "sources":["codex-clipboard-42ef0cb8-1ede-458d-be7e-67618300964b.png","codex-clipboard-446a94ce-7dd8-4e9e-93ec-0dc088e6a721.png","codex-clipboard-e80a6b73-bf60-4d81-8858-91085b109110.png"]
    },
    {
      "id":"meteor","name":"성운술","level":10,"seasonOnly":false,"maxLevel":true,
      "stats":{"급속":4.8,"HP%":10.2},
      "base":"아군 궁극기 4회마다 전장 모든 적에게 현재 HP 16% 고정 피해, 상한 파티 공격력 60%. 4초간 공격 속도 60 감소.",
      "unlockedUpgrades":["현재 HP 24%, 상한 파티 공격력 90%로 증가","발동 조건 아군 궁극기 3회로 감소"],
      "effective":{"alliedUltimatesRequired":3,"currentEnemyHpPercent":24,"damageCapPartyAttackPercent":90,"damageType":"고정","attackSpeedReduction":60,"debuffSeconds":4},
      "locked":[],
      "sources":["codex-clipboard-2d2f0234-a722-4b97-acd4-e44c739c44c2.png","codex-clipboard-d3148d61-a0d8-49e7-9917-048c0af1553d.png"]
    },
    {
      "id":"enlightenment","name":"계몽술","level":10,"seasonOnly":false,"maxLevel":true,
      "stats":{"공격 속도":7.2,"HP%":11.4},
      "base":"전투 시작 시 가장 후방 아군 1명에게 15초간 공격 속도 80 및 제어 면역.",
      "unlockedUpgrades":["공격 속도 100으로 증가","지속 시간 20초로 증가"],
      "effective":{"target":"시작 시 최후방 아군 1명","attackSpeedBonus":100,"durationSeconds":20,"controlImmune":true},
      "locked":[],"warnings":["제어 면역을 보스 공포 수치 면역으로 해석하지 않음"],
      "sources":["codex-clipboard-072de9eb-0afb-4c7b-a5a1-0c3f45576367.png","codex-clipboard-0db44e68-c01a-41b2-aff4-043398ac68cd.png"]
    },
    {
      "id":"flame","name":"열염술","level":10,"seasonOnly":false,"maxLevel":true,
      "stats":{"방어 관통":7.8,"물리 방어력%":13.8,"마법 방어력%":13.8},
      "base":"5초마다 HP가 가장 낮은 적 1명에게 파티 공격력 20% 마법 피해.",
      "unlockedUpgrades":["기본 피해 파티 공격력 30%","대상이 잃은 HP 4% 추가 피해, 추가 피해 상한 파티 공격력 20%"],
      "effective":{"intervalSeconds":5,"target":"HP 최저 적 1명","magicDamagePartyAttackPercent":30,"extraLostHpPercent":4,"extraDamageCapPartyAttackPercent":20},
      "locked":[],
      "sources":["codex-clipboard-5373f3a3-ed67-4ae0-a609-c125953ed3b3.png","codex-clipboard-f06da21c-8068-43e9-88fd-bf4362f69e53.png"]
    },
    {
      "id":"ironwall","name":"철벽술","level":10,"seasonOnly":false,"maxLevel":true,
      "stats":{"급속":6,"활력":9},
      "base":"전투 시작 시 최전방 아군 1명에게 제거 불가 축복. 전투 종료까지 물리·마법 방어력 15%, 피격 에너지 회복 20 증가. 즉시 및 12초마다 대상 최대 HP 20% 실드, 지속 6초.",
      "unlockedUpgrades":["축복의 물리·마법 방어력 20%","대상 사망 시 최전방 생존 아군에게 전투당 1회 이전"],
      "effective":{"target":"시작 시 최전방 아군 1명","physicalAndMagicDefensePercent":20,"hitEnergyRecoveryBonus":20,"shieldStartSeconds":0,"shieldIntervalSeconds":12,"shieldMaxHpPercent":20,"shieldDurationSeconds":6,"transferOnDeathLimit":1,"undispellableBlessing":true},
      "locked":[],"warnings":["전원 실드가 아닌 단일 대상 실드"],
      "sources":["codex-clipboard-9ad49992-1268-4a06-83ea-2d641287f0eb.png","codex-clipboard-a81407bd-edfd-401f-84b6-284e7e30af8f.png"]
    },
    {
      "id":"revival","name":"소생술","level":9,"seasonOnly":false,
      "stats":{"공격력%":4.2,"물리 방어력%":26.4,"마법 방어력%":26.4},
      "base":"시작 5초부터 10초마다 HP가 가장 낮은 아군 3명 각각 최대 HP 7% 회복.",
      "unlockedUpgrades":["회복량 최대 HP 10%"],
      "effective":{"startSeconds":5,"intervalSeconds":10,"targets":3,"healEachMaxHpPercent":10},
      "locked":[{"level":10,"effect":"대상 4명"}],
      "sources":["codex-clipboard-6f4eaa50-d020-4841-8305-34c3f72d7fc4.png","codex-clipboard-6c780b7b-9ae7-45a1-8bd6-e50d18504ac3.png"]
    },
    {
      "id":"binding","name":"구속술","level":8,"seasonOnly":false,
      "stats":{"공격력%":6.1,"HP%":7.2},"nextPreview":{"공격력%":6.6,"HP%":7.8},
      "base":"시작 3초부터 12초마다 가장 후방 적 영웅 2명에게 파티 공격력 25% 마법 피해 및 1.5초 구속. 구속된 적은 행동 불가.",
      "unlockedUpgrades":["구속 시간 2.5초"],
      "effective":{"startSeconds":3,"intervalSeconds":12,"targets":2,"target":"적 최후방 영웅","magicDamagePartyAttackPercent":25,"bindSeconds":2.5},
      "locked":[{"level":10,"effect":"시전 간격 10초"}],
      "sources":["codex-clipboard-eefa5c25-1540-49ee-b0c5-cf1105f44985.png","codex-clipboard-112c69c6-7e87-43f4-9aba-448afc5a6fe7.png"]
    }
  ],
  "boss": {
    "name":"망령의 소굴","isSeasonBoss":true,"seasonEchoesAllowed":true,"seasonEvidence":"사용자가 명시적으로 정정",
    "level":270,"faction":"기타","role":"마법사","attackType":"마법","range":20,
    "mechanics":[
      "공포의 안개: 전투 20/45/70초에 적 전체 240% 피해와 공포 20. 이후 초당 공포 1 추가, 발동마다 중첩.",
      "거대한 그림자: 시작 시 상대에게 에너지와 공포 부여(정확한 초기 수치 CHECK). 공포는 HP 피해를 낮춤. 실드가 있으면 매초 실드를 소모해 공포를 감소시킴.",
      "고독한 탐식: 일반 공격이 공포 50 초과 대상도 공격. 액티브 전체 160% 피해, 고립 대상 공포 10 추가. 고립 판정 거리 CHECK.",
      "가시 울타리: 전방 3칸 폭 범위 120% 피해와 에어본."
    ],
    "warnings":["치료는 공포 직접 제거로 확인되지 않음","피해·피격 합계로 사망 시점이나 제어 시간을 단정하지 않음","초록 통계는 치료량이며 실드량과 같지 않음","결과 승리 배너와 목표 100% 달성은 구분","이계의 미궁이나 심층 보스의 기록으로 분류하지 않음"],
    "overlay":"망령의 소굴 · 2026-09-16 저장 기록\n시즌 메아리 사용 가능. 실드와 공포 관리가 중요합니다.\n최고 관측 505M / 98.0% (100% 미달). 후속 배치 447M / 86.8%.\n로완→로란은 미실험안. 앱 학습 자료에서 초상·배치 근거·제한 확인. 현재 편성/남은 횟수 자동 판정 아님."
  },
  "battleRuns": [
    {"id":"run-416","status":"OBSERVED_RESULT","totalDamageM":416,"damagePercent":80.7,"rank":155,"echo":"철벽술 +10","party":["루시우스","후긴","로완","에디","아로라"],"damageM":{"루시우스":9.595,"후긴":3.672,"로완":16.693,"에디":112,"아로라":273},"healingM":{"루시우스":3.207},"receivedM":{"루시우스":10.373,"후긴":10.182,"로완":10.361,"에디":10.339,"아로라":50.496},"bossDamageM":31.350,"bossReceivedM":1505,"sources":[],"sourceNote":"대화 첫 두 첨부 이미지, 명시 파일 경로 없음. 원본 세션에 보존."},
    {"id":"run-456","status":"OBSERVED_RESULT","totalDamageM":456,"damagePercent":88.5,"rank":157,"echo":"철벽술 +10","party":["루시우스","후긴","로완","에디","아로라"],"damageM":{"루시우스":8.988,"후긴":4.617,"로완":17.006,"아로라":326,"에디":99.252},"healingM":{"루시우스":2.707},"receivedM":{"루시우스":11.482,"후긴":7.552,"로완":10.597,"아로라":26.201,"에디":11.153},"bossDamageM":6.237,"bossReceivedM":1508,"sources":["codex-clipboard-4d04805d-abc9-47cb-b3a1-2899dc186d4b.png","codex-clipboard-b51be7ce-6d70-430d-b391-209ebc4227ae.png"]},
    {"id":"run-463","status":"OBSERVED_RESULT","totalDamageM":463,"damagePercent":89.9,"rank":166,"echo":"파진술 +18","party":["루시우스","후긴","로완","에디","아로라"],"damageM":{"루시우스":9.665,"후긴":4.944,"로완":12.969,"아로라":348,"에디":86.631},"healingM":{"루시우스":3.226},"receivedM":{"루시우스":12.031,"후긴":9,"로완":7.148,"아로라":31.037,"에디":12.080},"bossDamageM":8.220,"bossReceivedM":1819,"sources":["codex-clipboard-942b6230-de85-443f-b87c-2774790c5c22.png","codex-clipboard-9759b83e-3de3-4cc7-aaa6-4508c4baca73.png"]},
    {"id":"run-505","status":"BEST_OBSERVED_NOT_CLEAR","totalDamageM":505,"damagePercent":98.0,"rank":193,"echo":"파진술 +18","party":["루시우스","후긴","로완","아로라","매튜&파피몬"],"damageM":{"루시우스":7.752,"후긴":3.326,"로완":0.581,"아로라":226,"매튜&파피몬":266},"healingM":{"루시우스":3.406},"receivedM":{"루시우스":14.731,"후긴":13.191,"로완":0.284,"아로라":31.053,"매튜&파피몬":13.605},"bossDamageM":7.337,"bossReceivedM":2074,"formationSource":"codex-clipboard-13be466c-0348-4f1a-8b74-ea225a54f87c.png","sources":["codex-clipboard-13be466c-0348-4f1a-8b74-ea225a54f87c.png","codex-clipboard-95add248-166e-4543-a181-6b79aaaa0dfc.png","codex-clipboard-65876029-2f8e-474a-abbf-b8fb5fac2e31.png"]},
    {"id":"run-447","status":"OBSERVED_REGRESSION","totalDamageM":447,"damagePercent":86.8,"rank":196,"echo":"파진술 +18","party":["루시우스","후긴","로완","아로라","매튜&파피몬"],"damageM":{"루시우스":8.544,"후긴":4.408,"로완":16.467,"아로라":250,"매튜&파피몬":167},"healingM":{"루시우스":2.542},"receivedM":{"루시우스":12.787,"후긴":8.443,"로완":11.599,"아로라":26.647,"매튜&파피몬":4.389},"bossDamageM":4.876,"bossReceivedM":1955,"formationSource":"codex-clipboard-ec3d6f81-e281-4df3-abba-ab13d73a69ba.png","sources":["codex-clipboard-ec3d6f81-e281-4df3-abba-ab13d73a69ba.png","codex-clipboard-2289e362-9882-4713-a0d7-35b71623a57f.png","codex-clipboard-2491f38a-f56c-4e0b-98e7-8ee5af730027.png"]}
  ],
  "proposal": {
    "status":"UNTESTED","replace":{"out":"로완","in":"로란 대제"},
    "party":["루시우스","후긴","로란 대제","아로라","매튜&파피몬"],"echo":"파진술 +18",
    "formation":"505M / 98% 당시 배치를 복원한 뒤 로완 자리만 로란으로 교체하는 제안. 최신 447M 배치를 기준으로 하지 않음.",
    "rationale":"외부 설명 기준 로란의 누적 피해 상위 2명 공격력 강화 및 사기 60% 이상 급속 50을 노림. 현재 계정의 상세 스킬 해금·실전 효과는 별도 확인 필요.",
    "risks":["로완 제거에 따른 에너지·궁극기 순환 저하","전용 스킬 궁극기 복제 해금 미확인: 기대치 제외","비보스용 스탯 감소 면역을 보스 공포 면역으로 적용하지 않음","공포 해제 또는 100% 돌파 보장 없음","진영 버프 변화만으로 극적인 상승을 약속하지 않음"],
    "source":"https://www.afk.global/afk-journey/characters/rolan",
    "sourceType":"external_description_not_user_account_unlock",
    "result":null
  },
  "corrections": [
    "505M/98%와 447M/86.8%는 망령의 소굴 시즌 보스 기록이다. 이전 기억 요약의 이계의 미궁 1라운드/심층 보스 분류는 잘못되었다.",
    "직전 배치 조정은 총피해 58M 감소. 매튜&파피몬 -99M, 아로라 +24M. 여러 상대 위치가 달라져 로완 이동 하나만의 인과로 단정할 수 없다.",
    "로완 개인 피해 0.581M 또는 피격 0.284M만으로 조기 사망이나 무용함을 단정하지 않는다.",
    "최고 관측 기록은 505M/98%이며 100% 클리어 기록은 없다. 515M 부근이라는 목표 피해 추정은 표시 반올림을 포함한 단순 역산이지 보스 확정 HP가 아니다.",
    "사용자는 3회만 남았다고 말한 뒤 두 결과를 더 제공했다. 당시 1회 잔여 추정은 현재 날짜의 도전 횟수가 아니다.",
    "한글 이름은 후긴·에디·레미아. 로완/에디 초상은 사용자가 정정한 이미지를 우선한다.",
    "회복술/파진술/관일술 +18 및 일반 메아리 6종의 스킬을 이미 제공받았다. 자료가 없다고 재요청하지 않는다.",
    "보유 영웅이라는 이유만으로 신화+ 전용 스킬, 강화 효과, 비보스 면역을 추천 근거에 추가하지 않는다."
  ],
  "labyrinthHistorical": {
    "scope":"망령의 소굴과 별개인 이계의 미궁",
    "difficulty":5,"explorationRosterLimit":10,"battlePartyLimit":5,"adjustedLevel":100,
    "tierRule":"엘리트+는 레전드로 보정, 레전드 초과 등급은 강등되지 않는다고 안내받음. 현재 규칙 변경 여부 미확인.",
    "regularFloors":15,"deepRanking":"심층은 층수와 보스 피해를 구분",
    "historicalReset":"월요일 09:00 KST라는 과거 안내. 최신 일정 확인 전 고정 알림에 사용하지 않음.",
    "proposalStatus":"과거 10인 명단과 교체안은 해당 결과가 없으면 미검증",
    "oldFixedPartyRule":"기존 20260821 데이터의 신태일&아구몬/매튜&파피몬 고정은 당시 특정 런 조건. 모든 미궁이나 시즌 보스에 강제하지 않음."
  }
}
'@ | ConvertFrom-Json -AsHashtable -Depth 100

# Actual five result pairs, not six: preserve this count from records rather than narrative.
$ledger = [System.Collections.Generic.List[object]]::new()
$portraitSources = @{
    'damian.png' = "$assets\portraits-20260917\damian.png"
    'shakitalis.png' = "$assets\portraits-20260917\shakitalis.png"
    'lucius-clear.png' = "$env:TEMP\afk-haunting-portraits\lucius-clear.png"
    'hugin.png' = "$env:TEMP\afk-haunting-portraits\hugin.png"
    'aurora.png' = "$env:TEMP\afk-haunting-portraits\aurora.png"
    'yamato.png' = "$env:TEMP\afk-haunting-portraits\yamato.png"
    'rolan.png' = "$env:TEMP\afk-haunting-portraits\rolan.png"
    'rowan-corrected.png' = "$env:TEMP\codex-clipboard-2d74d6e7-011f-4db3-9f73-319d87585c80.png"
    'odie-corrected.png' = "$env:TEMP\codex-clipboard-aaf75c3e-a7d3-4112-acbb-f16d8ec66614.png"
}
foreach ($hero in $root.heroes) {
    $source = $portraitSources[$hero.portraitFile ?? '']
    if ($source -and [System.IO.File]::Exists($source)) {
        $portraitDestination = "$assets\portraits-20260917\$($hero.portraitFile)"
        if (-not [StringComparer]::OrdinalIgnoreCase.Equals(
            [System.IO.Path]::GetFullPath($source),
            [System.IO.Path]::GetFullPath($portraitDestination))) {
            Copy-Item -LiteralPath $source -Destination $portraitDestination
        }
        $hero.portraitAsset = "learning/portraits-20260917/$($hero.portraitFile)"
        $hero.portraitSource = $source
        [void]$media.Add($source)
    } else { $hero.portraitStatus = 'CHECK' }
}
foreach ($record in @($root.echoes) + @($root.battleRuns)) {
    foreach ($name in $record.sources) { [void]$media.Add((Join-Path $env:TEMP $name)) }
}
[void]$media.Add('C:\Users\asp92\OneDrive\문서\PicPick\2026-09-16 13 59 23.mp4')
$mediaNumber = 0
foreach ($source in $media) {
    $mediaNumber++
    $destination = Join-Path "$archive\media" ('{0:D4}-{1}' -f $mediaNumber, [System.IO.Path]::GetFileName($source))
    try {
        Copy-Item -LiteralPath $source -Destination $destination -ErrorAction Stop
        $ledger.Add(@{ source = $source; preserved = $destination; status = 'COPIED' })
    } catch {
        $ledger.Add(@{ source = $source; status = 'UNAVAILABLE'; error = $_.Exception.Message })
    }
}
[System.IO.File]::WriteAllText("$archive\media-manifest.json", (ConvertTo-Json -InputObject @($ledger.ToArray()) -Depth 10), $utf8)
[System.IO.File]::WriteAllText("$archive\parse-issues.json", (ConvertTo-Json -InputObject @($parseIssues.ToArray()) -Depth 10), $utf8)

$entries = [System.Collections.Generic.List[object]]::new()
function Add-Entry([string]$id, [string]$title, [string]$text, [string]$portrait = '') {
    $entries.Add(@{ id = $id; title = $title; text = $text; portrait = $portrait })
}
Add-Entry 'policy' '필독: 근거 우선순위와 모드 구분' (($root.policy | ConvertTo-Json -Depth 20) + "`n`n사용자 정정 > 이름·등급 동시 캡처 > OCR·초상 인식 > 추론. 알 수 없는 등급은 CHECK. 현재 보유/해금과 과거 스냅샷을 구분합니다.")
Add-Entry 'corrections' '정정 사항: 망령의 소굴은 시즌 보스' ($root.corrections -join "`n`n")
Add-Entry 'account' '계정 스냅샷 · 공명 402 / 서포터 장비 충돌' ($root.account | ConvertTo-Json -Depth 20)
Add-Entry 'boss' '망령의 소굴: 공포·실드·시즌 메아리' (($root.boss.mechanics + $root.boss.warnings) -join "`n`n")
foreach ($echo in $root.echoes) {
    $text = "분류: $(if ($echo.seasonOnly) {'시즌 전용'} else {'일반 과거의 메아리'})`n현재 +$($echo.level). 다음 단계 미리보기는 현재 능력치가 아닙니다.`n`n"
    $text += "능력치`n$($echo.stats | ConvertTo-Json)`n`n기본 설명`n$($echo.base)`n`n해금된 강화`n$($echo.unlockedUpgrades -join "`n")`n`n현재 적용 수치`n$($echo.effective | ConvertTo-Json -Depth 10)`n`n아직 잠긴 강화`n$(ConvertTo-Json -InputObject @($echo.locked) -Depth 10)`n`n주의`n$($echo.warnings -join "`n")`n`n원본`n$($echo.sources -join "`n")"
    if ($echo.nextPreview) { $text += "`n`n다음 단계 미리보기(미적용)`n$($echo.nextPreview | ConvertTo-Json)" }
    if ($echo.requiredSeasonResonanceShown) { $text += "`n화면의 필요 시즌 공명 레벨: $($echo.requiredSeasonResonanceShown)" }
    Add-Entry "echo-$($echo.id)" "$($echo.name) +$($echo.level) · 현재 효과 / 잠금 효과" $text
}
foreach ($hero in $root.heroes) {
    Add-Entry "hero-$($hero.id)" "$($hero.name) · $($hero.faction) / $($hero.role) / $($hero.rank)" ($hero | ConvertTo-Json -Depth 10) ($hero.portraitAsset ?? '')
}
foreach ($run in $root.battleRuns) {
    Add-Entry $run.id "전투 기록: $($run.totalDamageM)M / $($run.damagePercent)% · $($run.status)" (($run | ConvertTo-Json -Depth 20) + "`n단위 M(백만). 표시 반올림 때문에 개별 피해 합과 총피해가 정확히 같지 않을 수 있음. 적 피격 합계는 라운드 기록 피해와 동일 지표로 계산하지 않음.")
}
Add-Entry 'proposal' '미실험: 로완 → 로란 대제 교체안' ($root.proposal | ConvertTo-Json -Depth 20)
Add-Entry 'labyrinth' '별도 콘텐츠: 이계의 미궁 과거 규칙' ($root.labyrinthHistorical | ConvertTo-Json -Depth 20)
Add-Entry 'hugin-detail' '후긴: 확인된 지원 구조와 잠금 주의' '후긴은 레오프론 서포터, 물리 공격, 사거리 20. 뒤 1칸 아군 지원과 공격력·에너지 보조, 6초 간격 단일 실드 설명을 앞선 캡처에서 확인. 전용 스킬의 추가 2명 실드는 현재 해금 확인 없이 적용하지 않음. 단순 개인 피해량으로 지원 기여를 평가하지 않음.'
Add-Entry 'formation' '배치 비교: 505M 기준과 447M 회귀' '505M 기준 배치 원본: codex-clipboard-13be466c-0348-4f1a-8b74-ea225a54f87c.png. 447M 배치 원본: codex-clipboard-ec3d6f81-e281-4df3-abba-ab13d73a69ba.png. 루시우스 전방, 후긴과 아로라 상대 위치 및 로완 중앙/측면 위치가 다름. 정확한 타일은 원본 기준이며 화면 픽셀을 게임 타일로 자동 환산하지 않음. 로란안은 505M 배치를 복원한 다음 로완 슬롯 하나만 교체하는 미실험 제안이다.'
$copied = @($ledger | Where-Object status -EQ 'COPIED').Count
$unavailable = @($ledger | Where-Object status -EQ 'UNAVAILABLE').Count
Add-Entry 'archive' '보존 범위와 원본 자료 위치' "원본 세션 전체, 사용자·답변 원문, 첨부 복사본과 상태 목록을 PC에 보관했습니다.`n$archive`n`n대화 메시지 $messageNumber 개 / APK 원문 페이지 $($conversationIndex.Count) 개.`n미디어 복사 $copied 개 / 접근 불가 $unavailable 개. 접근 불가 자료는 media-manifest.json에 기록하며 복구했다고 주장하지 않습니다. 세션 내부에 포함된 이미지 데이터는 session-original.jsonl에 그대로 남습니다.`n`nAPK에는 정리 데이터·초상·대화 텍스트를 포함합니다. 대용량 원본 영상 및 세션의 시스템/도구 로그는 PC 보관본에만 남깁니다. 원문은 당시 발언이며 오류·철회된 가설을 포함합니다. 정정 사항이 우선합니다."
$root.entries = $entries.ToArray()
$root.conversationIndex = $conversationIndex.ToArray()
$root.archive = @{ localPath = $archive; copiedMediaCount = $copied; unavailableMediaCount = $unavailable; parseIssueCount = $parseIssues.Count; rawSessionPreserved = $true }
$json = $root | ConvertTo-Json -Depth 100
[System.IO.File]::WriteAllText("$assets\game_knowledge_20260917.json", $json, $utf8)
[System.IO.File]::WriteAllText("$archive\game_knowledge_20260917.json", $json, $utf8)
$report = "# AFK 학습 보관 (2026-09-17)`n`n" + (($entries | ForEach-Object { "## $($_.title)`n`n$($_.text)" }) -join "`n`n")
[System.IO.File]::WriteAllText("$archive\LEARNING.md", $report, $utf8)
[System.IO.File]::WriteAllText((Join-Path $repo 'docs\learning-20260917.md'), $report, $utf8)
$noteDir = 'C:\Users\asp92\.codex\memories\extensions\ad_hoc\notes'
[System.IO.Directory]::CreateDirectory($noteDir) | Out-Null
$note = @"
# AFK learning persistence requested by user (2026-09-17)
User explicitly requested complete preservation and APK integration.
Canonical archive: $archive
Structured data: $assets\game_knowledge_20260917.json
Read LEARNING.md and media-manifest.json there for current evidence and missing attachments.
Important correction: 416/456/463/505/447M results belong to seasonal boss 망령의 소굴, NOT 이계의 미궁 or its deep floors. Season echoes are explicitly allowed by user.
Best observed: 505M / 98.0%, Lucius/Hugin/Rowan/Aurora/Matt-and-Gabumon, 파진술18. Later placement 447M/86.8%, not improvement. Rolan replacing Rowan is UNTESTED, not a clear; exclusive ultimate cloning and boss fear immunity are not assumed unlocked/applicable.
All nine echoes are captured: 회복술18, 파진술18, 관일술18, 성운술10, 계몽술10, 열염술10, 철벽술10, 소생술9, 구속술8. Preserve active vs locked effects.
User-facing recommendations require user-sourced portrait, Korean name, faction, class, confirmed rank with date or CHECK. User correction outranks inferred portrait/frame. 후긴, 에디, 레미아 are canonical names; Rowan/Edi corrected portraits replace earlier wrong crops.
Class support equipment remains text490 vs capture390 conflict. Past remaining attempts must not become today's count. No result confirms 100% clear. No saved conjecture becomes observed evidence.
APK source integrates offline learning reader, names/factions/portraits, and bounded read-only boss reference. Build/install/device outcomes must be reported separately; this note does not claim build or install success.
Original session: 01a037cf-2522-74c0-9787-cf38606eb2fa. Retain old learning as history; do not overwrite it with unverified current assumptions.
"@
[System.IO.File]::WriteAllText("$noteDir\$stamp-afk-learning-apk.md", $note, $utf8)
[pscustomobject]@{ archive = $archive; messages = $messageNumber; pages = $conversationIndex.Count; mediaCopied = $copied; mediaUnavailable = $unavailable; parseIssues = $parseIssues.Count; resultRecords = $root.battleRuns.Count; echoes = $root.echoes.Count; memoryNote = "$noteDir\$stamp-afk-learning-apk.md" } | ConvertTo-Json
