# Initial formation recognition and recommendation

Source: user-provided screenshot codex-clipboard-9303acbe-8431-4acf-b29e-df3bef748512.png and source inspection on 2026-09-07.
Status: verified transcription and implementation requirements; not a trained model or runtime recommendation implementation.

## Current implementation

- HonorDuelScreenClassifier recognizes the screen title or generic selection text signals.
- HonorDuelShopAnalyzer retains whole-screen OCR blocks but does not extract initial-formation offers.
- Initial formation does not call extractSlots or extractOwnedHeroes.
- TrackingViewModel returns a fixed HonorDuelInitialFormation state and instruction overlay without ranking candidates.
- HeroRecognitionCatalog supports name OCR and portrait templates, but initial-formation portraits are not routed to it. Some catalog entries have names only.
- No definitions for the three observed artifacts were found in the searched app assets and docs.

## Verified screenshot observations

Screen title: "초기 진형을 선택하세요!".

| Row | Visible description | Visible artifact name | Portraits | Displayed number |
| --- | --- | --- | --- | --- |
| 1 | 반신 영웅을 소환해 함께 전투 | 신성한 소환 | 페르세우스 / 딜그레이 / 보라시아 (left to right, confirmed by detail captures) | 60 |
| 2 | 속전속결 전술 | 고블린 가면 | 카세디아 / 카짐 / 스모키와 미르키 (left to right, confirmed by detail captures) | 60 |
| 3 | 와일더스 연맹 영웅 중심 | 평정의 샘물 | 메이 / 루보미르 / 프라벨 (left to right, confirmed by detail captures) | 60 |
| 4 | 랜덤 진형(리스크 UP, 수익 UP) | Hidden/random, no confirmed name | 3 question marks | 70 |

Each row has a selection button. These are example offers, not a permanent row-to-artifact or row-to-hero mapping.
The numerical resource meaning is unconfirmed; do not treat it as a purchase price or current balance.
Artifact descriptions above are card headlines. Verified detail effects from subsequent user captures are recorded below.

## Verified artifact detail captures

All three detail panels display artifact experience 0/24. The 24 and 46 experience effects are locked in these captures. Never apply locked effects to the initial offer's current power.

### 신성한 소환

Source: codex-clipboard-b5eef305-34d5-42ab-8e66-1b99ab5d260d.png

- Base: At battle start, the rearmost allied hero loses 75% maximum HP and 80% attack. Summon one random Celestial hero inheriting that maximum HP and attack. The summoned hero wears the same equipment and additionally gains 400 initial energy.
- Preserve the original wording about inherited stats: "해당 최대 HP와 공격력을 계승한". Do not invent a precise inheritance formula or a distribution of possible summons from this panel alone.
- Experience 24: The summoned Celestial hero's basic attributes increase by an additional 30%.
- Experience 46: Choose one Legendary equipment item.
- Evaluation implication (inference): Depends on the rearmost ally, its equipment and the random summon. Evaluate the loss to that ally as well as the summon benefit; do not assume that an extra hero has no cost.

### 고블린 가면

Source: codex-clipboard-5dad586c-b716-4188-969c-8c7e2448b25e.png

- Base: At battle start, every enemy hero loses 50% of its current HP. The removed HP is returned to the enemy over the following 20 seconds.
- Experience 24: HP return duration is extended to 30 seconds.
- Experience 46: Choose one Legendary equipment item.
- Evaluation implication (inference): Creates a temporary early kill window. Do not model it as permanent 50% damage, a maximum-HP reduction, or a guaranteed instant kill. The panel does not establish the exact HP return tick schedule.

### 평정의 샘물

Source: codex-clipboard-519f607e-699f-4ef0-97d5-848b4f3f39f7.png

- Base: Starting 5 seconds after battle begins, then every 10 seconds, heal all allied Wilder heroes for 8% / 10% / 20% of maximum HP depending on hero tier.
- The panel does not identify which named tier corresponds to each percentage. Preserve the ordered values without inventing a tier mapping.
- Experience 24: When the effect triggers, deal true damage to all enemies equal to 80% of excess healing. The displayed amount is the total damage dealt across all enemies, not the amount independently dealt to every enemy. Exact distribution is not specified.
- Experience 46: Choose one Legendary equipment item.
- Evaluation implication (inference): Depends on the actual Wilder members and their tiers and survival. Do not heal every faction or treat the card headline as proof that all three offered heroes are Wilders. Do not award overheal damage before experience 24.

These details replace the missing artifact-effect evidence. They support later recommendation rules but do not alone establish a best offer without recognized heroes. This document is not automatically loaded by the APK.

## Verified heroes for the observed first offer

The following identities are confirmed by named detail panels and matching portraits on the first offer. All three display 에픽. This is the observed offer only, not a fixed roster for 신성한 소환.

| Portrait position | Name | Faction | Role | Attack type | Range | Detail capture |
| --- | --- | --- | --- | --- | --- | --- |
| Left | 페르세우스 | 레오프론 | 전사 | 물리 공격 | 1 | codex-clipboard-96950323-dc06-4599-86eb-7f6fbecc7777.png |
| Middle | 딜그레이 | 레오프론 | 마법사 | 마법 공격 | 6 | codex-clipboard-cf9a85bf-3b95-481e-a2b1-c9201c2b1c2a.png |
| Right | 보라시아 | 트라이브 | 마법사 | 마법 공격 | 10 | codex-clipboard-c31cd35b-9eb4-46e7-988a-ace8c0efac3e.png |

Visible skill indicators on each panel: ultimate 5, then 4, 4, 3, one locked slot, then 1. Skill names and detailed effects are not shown; do not infer them from icons or flavor text.

Recognition references: the small portraits on the offer and the enlarged portraits in the corresponding detail panels provide paired visual identity evidence. Recording this evidence does not add portrait templates to the app or prove recognition accuracy.

Evaluation constraints:
- The observed roster contains two Lightbearers and one Mauler; none of these three is the randomly summoned Celestial. The headline describes the artifact effect, not the offered heroes' faction.
- Roles show one warrior and two mages; do not relabel the warrior as a confirmed tank or assume healing/CC/burst skills without skill descriptions.
- Range 10 does not establish that 보라시아 will be the rearmost ally. The artifact donor depends on actual battle placement, not horizontal offer order or attack range alone.
- Remaining offers must also be identified before establishing a best formation among all offers.

## Verified heroes for the observed second offer

All three display 에픽. These identities belong to this observed 속전속결 전술 offer, not every future 고블린 가면 offer.

| Portrait position | Name | Faction | Role | Attack type | Range | Detail capture |
| --- | --- | --- | --- | --- | --- | --- |
| Left | 카세디아 | 레오프론 | 마법사 | 마법 공격 | 3 | codex-clipboard-7edb3a69-8d30-4f10-963b-ad9a9e4a885d.png |
| Middle | 카짐 | 트라이브 | 사수 | 물리 공격 | 7 | codex-clipboard-bb7a0a14-e170-4e11-98e3-ab0d524e399d.png |
| Right | 스모키와 미르키 | 트라이브 | 서포터 | 마법 공격 | 8 | codex-clipboard-bf79bae7-5136-48f2-a04c-174c62b73bdb.png |

Visible descriptions:
- 카세디아: "전장으로 돌입하여 데미지를 생성하는 마법사로, 아군에 데미지 증가 버프를 부여한다."
- 카짐: "자신의 맹세를 지키는 사수. 에어본된 적군을 예리하게 노린다."
- 스모키와 미르키: "주변 아군에게 지속적으로 치료와 버프를 부여하는 서포터로, 전투가 진행되면서 점점 강해진다."

Visible skill indicators: ultimate 5, then 4, 4, 3, one locked slot, then 1. Detailed skill values, timings and targets are not shown.

Evaluation constraints:
- Verified role coverage is mage, marksman and support, with one Lightbearer and two Maulers. No hero in this offered trio is labeled tank or warrior.
- 카세디아's description supports a damage-buff tag but does not quantify burst damage or prove that she inflicts airborne control.
- 카짐's description mentions airborne enemies; do not infer that this trio supplies the required airborne control without skill evidence.
- 스모키와 미르키's description confirms nearby healing/buffs and growth over the battle. Keep positioning and time dependence in later evaluations.
- 고블린 가면 provides a temporary early HP advantage. Damage support is relevant, but these panels alone cannot prove kills before HP returns or justify a guaranteed ranking.
- Named detail portraits and their matching small portraits on row 2 are identity references; no app portrait templates or runtime scoring are added by this document.

## Verified heroes for the observed third offer

All three display 에픽. These identities belong to this observed offer, not every future 평정의 샘물 offer.

| Portrait position | Name | Faction | Role | Attack type | Range | Detail capture |
| --- | --- | --- | --- | --- | --- | --- |
| Left | 메이 | 와일더스 | 레인저 | 마법 공격 | 3 | codex-clipboard-251f63e5-a8e9-4c94-8804-072e0d8f852a.png |
| Middle | 루보미르 | 그레이브본 | 서포터 | 마법 공격 | 6 | codex-clipboard-add83160-e84f-4eb0-8845-4130db9b331d.png |
| Right | 프라벨 | 와일더스 | 전사 | 물리 공격 | 5 | codex-clipboard-118c53e6-22b8-493e-a8c5-9f507f2adca1.png |

Visible descriptions:
- 메이: "예리하고 민첩한 레인저로 적의 궁극기를 차단할 수 있으며 전투 중 계속 진화한다."
- 루보미르: "영원한 침묵의 꽃밭으로 아군을 치료하고 적군의 생명력을 흡수할 수 있는 음울하고 과묵한 서포터이다."
- 프라벨: "다양한 소환수 생성에 능하며, 소환수를 활용해 지속적으로 데미지를 입힐 수 있는 전사이다."

Visible skill indicators: ultimate 5, then 4, 4, 3, one locked slot, then 1. Exact skill mechanics and numerical values remain unverified.

Evaluation constraints:
- The offer contains two Wilders and one Graveborn. 평정의 샘물's base healing applies to 메이 and 프라벨, not 루보미르.
- The headline "와일더스 연맹 영웅 중심" must not overwrite individual faction recognition.
- 메이 has a confirmed ultimate-interruption description, 루보미르 healing/life-drain, and 프라벨 summon-based sustained damage. These are qualitative tags, not measured damage or win-rate evidence.
- Do not assume 프라벨's summons receive the artifact's hero-only healing, or that his warrior role establishes tank durability.
- The 24-experience overheal damage is still locked at the displayed 0/24. Do not count it as initial damage output.
- All nine visible offered heroes are now identified, but a guaranteed ranking cannot be inferred from identity and role alone. Numerical performance, enemies, positioning, subsequent purchases and random outcomes are not supplied by these captures.
- Named detail portraits and matching row-3 portraits are identity evidence; recording them does not install app templates or implement runtime recommendations.

## Required functionality from the user

Recognize each offer's artifact and heroes, compare offers, and recommend the more advantageous initial formation with reasons.
This supersedes the earlier request to show recognition-only guidance without recommendations.

1. Extract offers by row using OCR/visual anchors, preserving card and selection bounds.
2. Recognize artifact names and bind them to their own row; store unknown names without inventing effects.
3. Recognize each of the three portrait crops independently using existing catalog/correction support. Preserve confidence and unresolved candidates.
4. Do not assign a card's headline faction to every hero on the card.
5. Treat random contents as hidden, not missing OCR or three known heroes.
6. Rank using verified artifact effects, recognized heroes, role coverage and artifact/hero compatibility. Explain uncertainty; do not claim a best choice from arbitrary artifact-name scores.
7. Never merge unselected offers into owned heroes or live currency. Confirm selection before updating run state.
8. Keep recommendation generation separate from automatic clicking.

## Evidence still needed

- Artifact detail evidence above is complete for the displayed base and unlock descriptions; precise inheritance formula and tier-to-healing mapping remain unspecified.
- All nine visible identities are confirmed above. Portrait reference extraction, app catalog integration and recognition validation remain implementation work; no more identity captures are required for these nine.
- Confirmation of what the 60/70 resource values represent.
- Representative different offers to check row extraction and avoid memorizing this screenshot.

Do not use this file's existence as evidence that these features are already present in the APK.
