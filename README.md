# Open Dashcam

`Open Dashcam`는 Android에서 동작하는 대시캠(블랙박스)형 영상 녹화 앱입니다.  
기존 대시캠 앱들의 레거시 구조와, 속도/시간 메타데이터가 영상에 충분히 삽입되지 않는 문제를 개선하기 위해 개발했습니다. 특히 시간 메타데이터를 영상에 명확히 포함해 사고 발생 시 증빙 활용도를 높이고, 실제 사용 환경을 고려해 디테일한 기능을 사용자가 ON/OFF 할 수 있도록 설계했습니다.  
CameraX 기반으로 연속 분할 녹화, 워터마크 오버레이, 저장소 정책 관리, 녹화 파일 관리(재생/공유/삭제)를 제공합니다.

## 주요 기능

- 연속 분할 녹화: `1분 / 3분 / 5분 / 10분` 단위로 자동 분할 저장
- 카메라 선택: 전면/후면 렌즈 전환
- 해상도 선택: `UHD(2160p)`, `FHD(1080p)`, `HD(720p)`, `SD`
- 저장 위치 선택:
  - 내장 저장소(앱 전용 디렉터리)
  - SD/외장 저장소(SAF 폴더 선택)
- 저장 공간 부족 시 정책:
  - 녹화 중지
  - 가장 오래된 파일 삭제 후 녹화 재시도
- 워터마크:
  - 시간
  - 위치(GPS 좌표, 권한 허용 시)
  - 영상 하단 푸터 오버레이(REC/GPS 상태 포함)
- 디테일 옵션 제어: 시간 워터마크, 위치 워터마크, 녹화 푸터 오버레이 ON/OFF
- 녹화 파일 목록 화면:
  - 재생
  - 공유
  - 삭제

## 요구 사항

- Android Studio (최신 Stable 권장)
- Android SDK 36
- JDK 11
- Android 기기/에뮬레이터 API 24 이상

## 권한

- 필수:
  - `CAMERA`
  - `RECORD_AUDIO`
- 선택:
  - `ACCESS_FINE_LOCATION` (위치 워터마크 사용 시)

## 빌드 및 실행

### Android Studio

1. 프로젝트 열기
2. Gradle Sync 완료
3. 디바이스 선택 후 `Run` 실행

### CLI

```bash
./gradlew :app:assembleDebug
./gradlew :app:installDebug
```

## 사용 방법

1. 앱 실행 후 카메라/마이크 권한을 허용합니다.
2. `설정`에서 저장 위치, 렌즈, 해상도, 분기 길이, 워터마크 옵션을 설정합니다.
3. 저장 위치를 `SD/외장 저장소`로 선택한 경우 폴더를 먼저 지정합니다.
4. `녹화 시작` 버튼으로 연속 분할 녹화를 시작합니다.
5. `녹화 파일 보기`에서 파일을 재생/공유/삭제할 수 있습니다.

## 저장 경로 및 파일명

- 내장 저장소:
  - `Android/data/com.example.openblackbox/files/Movies/OpenBlackBox/`
- 파일명 패턴:
  - `blackbox_yyyyMMdd_HHmmss_###.mp4`

외장 저장소 모드에서는 사용자가 선택한 SAF 폴더에 같은 파일명 규칙으로 저장됩니다.

## 동작 참고 사항

- 모든 화면은 가로 모드(`sensorLandscape`)로 고정됩니다.
- 녹화 중에는 저장 위치/렌즈/해상도 변경이 비활성화됩니다.
- 위치 워터마크는 `last known location`을 사용하며, 위치 정보를 못 얻으면 `위치 없음`으로 표시됩니다.
- 앱이 `onStop` 상태로 전환되면 현재 녹화가 중지됩니다.

## 프로젝트 구조

- `app/src/main/java/com/example/openblackbox/MainActivity.kt`: 카메라 프리뷰, 녹화, 세그먼트 로테이션, 워터마크 오버레이
- `app/src/main/java/com/example/openblackbox/RecordingsActivity.kt`: 녹화 파일 목록/재생/공유/삭제
- `app/src/main/java/com/example/openblackbox/StorageRepository.kt`: 파일 생성/조회/삭제, 저장소 처리
- `app/src/main/java/com/example/openblackbox/AppSettings.kt`: 사용자 설정(SharedPreferences)
- `app/src/main/java/com/example/openblackbox/CameraOptions.kt`: 저장/렌즈/해상도/세그먼트/저장소 정책 enum
