$ErrorActionPreference='Stop'
$path=Join-Path (Split-Path $PSScriptRoot -Parent) 'app/src/main/assets/learning/game_knowledge_20260917.json'
$root=Get-Content -LiteralPath $path -Raw -Encoding utf8 | ConvertFrom-Json
if ($root.yeti.runs.Count -ne 8 -or $root.yeti.bestObservedRun -ne 'yeti-704') { throw 'Yeti ledger missing' }
if (($root.yeti.runs | Where-Object id -EQ 'yeti-704').deathStatus -ne 'USER_CONFIRMED_NONE') { throw 'Survival correction missing' }
if ($root.policy.replacementRule.minimum -ne 'equal_or_higher_confirmed_rank') { throw 'Rank policy missing' }
if (-not $root.prosperityParty -or -not $root.boss.overlay -or -not $root.conversationIndex) { throw 'Historical integration missing' }
Write-Output 'Yeti learning checks passed'
