# Phase 0 — Android Skeleton + MediaProjection

- 상태: 승인됨
- 기준일: 2026-08-19
- 추적 이슈: [#13](https://github.com/asp0902/Mobile-Game-Assistant/issues/13)
- 범위: 프로젝트 구조와 화면 캡처 경계 확정. Recognition/Rule Engine 구현은 포함하지 않는다.

## 플랫폼 결정

| 항목 | 값 | 이유 |
|---|---:|---|
| `compileSdk` | 36 | 현재 안정 Android SDK 기준 |
| `targetSdk` | 36 | 최신 안정 동작 제한과 Google Play 요구사항 대응 |
| `minSdk` | 23 | 향후 ML Kit Text Recognition 최소 요구사항과 일치 |
| 언어/UI | Kotlin / Jetpack Compose | 프로젝트 요구사항 |
| 아키텍처 | 단일 `app` 모듈 + MVVM | Phase 0에 충분하며 조기 멀티 모듈화를 피함 |

Android 17/API 37은 현재 Preview이므로 사용하지 않는다.

## 패키지 구조

```text
app/src/main/java/com/asp0902/mobilegameassistant/
├── AssistantApp.kt
├── MainActivity.kt
├── tracking/
│   ├── TrackingScreen.kt
│   └── TrackingViewModel.kt
└── capture/
    ├── MediaProjectionService.kt
    ├── CaptureSession.kt
    └── CaptureState.kt
```

Phase 0에서는 사용하지 않는 인터페이스, 빈 패키지, 별도 도메인 계층을 만들지 않는다. Hilt, Room, DataStore는 빌드 연결만 준비하며 가짜 DB/DAO/설정 모델은 추가하지 않는다.

## 권한 흐름

```text
사용자: 트래킹 시작
  → Android 13+: 알림 권한 요청
  → MediaProjection 화면 공유 동의 요청
  → 동의 결과를 MediaProjectionService에 전달
  → mediaProjection 유형 Foreground Service 시작
  → startForeground()
  → getMediaProjection()
  → callback 등록
  → VirtualDisplay 1개 생성
  → Tracking
```

- 사용자의 명시적 탭 전에는 권한 요청이나 캡처를 시작하지 않는다.
- 알림 권한 거부는 Foreground Service 시작 자체를 막지 않는다.
- Android 14 이상에서는 세션마다 새 동의를 받고, 동의 토큰과 `MediaProjection` 인스턴스를 재사용하지 않는다.
- `AccessibilityService`, 자동 터치, 게임 조작 코드는 두지 않는다.

## Foreground Service 생명주기

```text
Idle → AwaitingConsent → Starting → Tracking ↔ Capturing
  ↑                                            │
  └──────── Stop / 취소 / 오류 / onStop ───────┘
```

- 서비스는 `START_NOT_STICKY`로 동작한다.
- 알림에 `트래킹 중지` 액션을 제공한다.
- 종료 시 `VirtualDisplay`, `ImageReader`, `MediaProjection`을 순서와 무관하게 안전하게 한 번만 해제한다.
- 시스템이 화면 공유를 중단하면 `MediaProjection.Callback.onStop()`에서 동일한 정리 경로를 실행한다.
- 캡처 세션이나 동의 토큰은 저장하지 않는다.

## 단일 프레임 캡처

트래킹 동안 `VirtualDisplay` 하나를 유지하되 평상시 surface는 비운다. 사용자가 `분석`을 누를 때만 다음을 수행한다.

1. 현재 크기의 `ImageReader(maxImages = 2)` 생성
2. `VirtualDisplay.setSurface(imageReader.surface)` 연결
3. 최신 `Image` 한 장 취득
4. row padding을 제거해 `Bitmap` 변환
5. surface 분리 후 `Image`와 `ImageReader` 닫기
6. 가장 최근 `Bitmap`만 메모리에 유지해 디버그 UI에 표시

회전이나 캡처 영역 크기 변경은 기존 `VirtualDisplay.resize()`로 반영한다. Compose가 표시 중일 수 있는 이전 `Bitmap`을 임의로 `recycle()`하지 않는다.

## 이미지 전달

```text
MediaProjectionService
  → CaptureSession: StateFlow<CaptureState>
  → TrackingViewModel
  → TrackingScreen 디버그 프리뷰
```

서비스 내부 Binder나 BroadcastReceiver는 만들지 않는다. 앱 프로세스 안에서 Hilt singleton `CaptureSession`의 `StateFlow`를 공유한다.

향후 Phase 1의 Recognition 진입점은 캡처 완료 상태의 `Bitmap`이다. Recognition은 원본 프레임을 읽기만 하고, 캡처 생명주기나 추천 로직을 소유하지 않는다.

## Phase 0 제외 범위

- 지속 캡처와 자동 분석
- OCR, OpenCV, ML Kit 런타임 연결
- Recognition/Rule Engine/Overlay 구현
- Room 엔티티와 DataStore 설정 스키마
- 이미지 파일 저장 및 업로드
- 게임 입력과 자동 조작

## 공식 근거

- [Android SDK 플랫폼 릴리스](https://developer.android.com/tools/releases/platforms)
- [Android 17 Preview SDK 설정](https://developer.android.com/about/versions/17/setup-sdk)
- [Google Play target API 요구사항](https://developer.android.com/google/play/requirements/target-sdk?hl=ko)
- [MediaProjection 가이드](https://developer.android.com/media/grow/media-projection)
- [MediaProjectionManager API](https://developer.android.com/reference/android/media/projection/MediaProjectionManager)
- [Foreground Service 유형: mediaProjection](https://developer.android.com/develop/background-work/services/fgs/service-types#media-projection)
- [알림 런타임 권한](https://developer.android.com/develop/ui/compose/notifications/notification-permission)
- [VirtualDisplay API](https://developer.android.com/reference/android/hardware/display/VirtualDisplay)
- [ML Kit Text Recognition Android 요구사항](https://developers.google.com/ml-kit/vision/text-recognition/v2/android)
