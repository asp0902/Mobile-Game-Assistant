# Initial formation implementation and validation

The initial selection runtime now connects per-card OCR and portrait recognition to
`InitialFormationAdvisor`, `TrackingViewModel`, the detailed Compose UI, and the
existing non-interactive overlay. Unselected offers never enter owned-hero state.

## Recognition

- Four vertically arranged offers are separated by right-hand selection text anchors.
  Portrait rectangles use measured relative layout and the existing game viewport.
  Missing or irregular anchors suppress highlighting rather than fabricate a button.
- Each portrait receives only OCR wholly inside its own rectangle. With no name OCR,
  RGB template comparison still runs. Existing confirmation/margin thresholds remain.
- Nine references are exact crops from named user detail captures, not from the
  selection test image. Provenance, rectangles and original SHA-256 values are in
  `initial-formation-reference-crops.csv`. Runtime assets never reference PC Temp.
- Existing hero IDs are reused. Verified references take precedence over name-only
  entries. Missing portrait files are tolerated; decoded references are cached.
- Artifact names are matched within their card. Headline-only identification is
  explicitly inferred and cannot authorize selection highlighting. Conflicting
  names remain unknown. Artifact icon classification is not implemented.
- Random offers are hidden. Unknown rarity remains unknown: the nine reference
  captures being Epic does not establish the rarity of a later offer.
- Hero/equipment/artifact detail evidence overrides a visible background selection
  title. New frames clear the previous overlay; existing latest-result guards remain.

## Recommendation

Weights are developer heuristics for evidenced compatibility, not win rates or
official game scores: confirmed healing +2; damage/support role coverage +1;
Wilder healing targets +2 each; compatible sustained/growing Wilder roles +1;
the verified damage buff with Goblin Mask +2. There is no artifact-name base rank.
Unknown identities are excluded from ranking, not assigned weak performance.

The reference selection recommends row 3, Serenity's Spring (평정의 샘물),
conditionally: Mei and Florabelle are the two eligible Wilders; Lubomir provides
separate healing and is not an artifact healing target. The first five seconds,
unknown tier-to-healing mapping and hidden/random alternatives remain uncertain.

Only base effects are evaluated. XP 24/46 effects are excluded. Goblin Mask's HP
reduction returns over 20 seconds, and Divine Summoning costs the rearmost ally
75% maximum HP and 80% attack. Neither outcome guarantees kills or a net benefit.
Ties are explicitly conditional; they do not select the earliest slot.

## Executed checks

- 104 JUnit tests passed, including five new initial-formation tests.
- Four cards and three artifact bindings from transcribed OCR coordinates.
- Actual RGB recognition of all nine small selection portraits against separate
  named detail-popup crops, with existing catalog templates included as competitors.
- Random question-mark pixels do not confirm a known identity.
- Reordered offer data changes the selected slot while preserving the matching
  artifact/hero recommendation. Missing identities and ties do not invent a winner.
- Base-effect conditions, Wilder target exclusion, unknown artifacts and popup
  classification are tested. Existing mode tests also execute.
- Existing test defects were corrected without changing their production policies:
  a Java fixture passed null to non-null RecognitionSource; ambiguous recognition
  expected a candidate below the existing margin threshold; three Artisans tests
  expected an unsupported specific text-only choice, five results from six names,
  or SKIP where the current safety policy returns CHECK.

This is separate-capture portrait validation within the same user-provided example,
not a claim of generalization to arbitrary offers. OCR coordinates in the JVM tests
are manually transcribed; ML Kit on-device OCR and visual UI/overlay behavior have
not been exercised on a device. No device installation was performed. No synthetic
reference images, fictional hero stats, win rates, or grade mappings were added.

## Windows test invocation

The original Unicode workspace path produced ClassNotFoundException for all test
classes although compilation had succeeded. A short ASCII directory junction to
the same project allowed the unmodified Gradle test task to execute successfully:

```powershell
$project = 'C:\Users\asp92\OneDrive\바탕 화면\AFKː 새로운 여정\Mobile-Game-Assistant'
$alias = 'C:\Users\asp92\.codex\tmp\afk-initial-20260907'
if (!(Test-Path -LiteralPath $alias)) {
    New-Item -ItemType Junction -Path $alias -Target $project
}
& "$alias\gradlew.bat" -p $alias :app:testDebugUnitTest :app:assembleDebug
```

The junction references the same files; it is not a separate checkout. No permanent
Gradle classpath workaround or additional dependency is added. The APK is generated
at the original project's `app/build/outputs/apk/debug/app-debug.apk` as requested.
