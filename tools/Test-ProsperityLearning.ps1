$ErrorActionPreference = 'Stop'
$file = Join-Path (Split-Path $PSScriptRoot -Parent) 'app/src/main/assets/learning/game_knowledge_20260917.json'
$data = Get-Content -LiteralPath $file -Raw -Encoding utf8 | ConvertFrom-Json
if ($data.prosperityParty.observationCount -ne 10 -or $data.prosperityParty.bestObserved.damagePercent -ne 89.6 -or $data.prosperityParty.bestObserved.earlyDeaths -ne $false -or $data.prosperityParty.bestObserved.cleared -ne $false -or $data.prosperityParty.auroraExclusive15Available -ne $false) { throw 'Prosperity evidence constraints lost.' }
if (-not $data.boss.overlay -or -not ($data.entries | Where-Object { $_.id -like 'prosperity-20260917-*' })) { throw 'Existing overlay or new learning entries missing.' }
'Learning scope constraints passed.'
