# 원본과 이 포크

이 저장소는 [DylanVann/react-native-fast-image](https://github.com/DylanVann/react-native-fast-image)의 포크예요. 원본을 그대로 따라가고, Frontia 앱에 필요한 변경만 얹어요.

## 기준

| 항목          | 값                                                                                                |
| ------------- | ------------------------------------------------------------------------------------------------- |
| 기준 릴리스   | `v10.11.0` (`b90ee41`)                                                                            |
| 포함한 원본   | 원본 `main`의 `d86d925`까지(릴리스 뒤 테스트 커밋 하나)                                           |
| 리모트        | `upstream` = `https://github.com/DylanVann/react-native-fast-image.git`, `origin` = 이 포크       |
| 패키지        | `@frontia/react-native-fast-image`                                                                |
| 버전          | `<원본 버전>-frontia.<n>`. 원본 버전이 같으면 `n`을 올리고, 원본을 새로 받으면 `n`은 1부터예요    |
| 원본과의 차이 | `git diff --stat v10.11.0 HEAD -- android/src/main ios common src ':!src/**/*.test.*'`로 확인해요 |

## 우리 변경

원본과 다른 제품 코드는 Android와 JS뿐이에요. iOS(`ios/`, `common/cpp`)는 원본과 같아요.

| 변경                                     | 파일                                                                                                                                                                                                                                            | 이유                                                                                                                                                                                                                                                                                                                                      |
| ---------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Android 중앙 cover를 하드웨어 비트맵으로 | `FastImageHardware.java`, `FastImageHardwareDrawable.java`, `FastImageSourceSize.java`(`scaleTypeOptions`, `capture`), `FastImageViewWithUrl.java`(`renderingOptions`, `setImageDrawable`)                                                      | 원본은 cover 이미지를 `CenterCrop`으로 잘라 소프트웨어 비트맵으로 만들어요. 소프트웨어 비트맵은 그릴 때 GPU 텍스처로 한 번 더 올라가서, 배너·썸네일이 많은 화면에서 메모리를 두 번 써요. crop 변환을 빼고 하드웨어 비트맵을 허용해 GPU 메모리 한 곳에만 두고, 자르지 않은 디코드는 8MP로 제한하고, 소프트웨어 Canvas에는 사본으로 그려요. |
| `maxPixelDensity` prop (Android)         | `src/index.tsx`, `src/specs/FastImageViewNativeComponent.ts`, `src/backgroundProps.ts`, `src/index.web.tsx`, `src/index.js.flow`, `FastImageViewManager.java`, `FastImageViewWithUrl.java`(`decodePixelScale`, `decodeBox`, `renderingOptions`) | 화면 밀도가 3배나 4배인 기기에서 배너·썸네일·흐린 배경은 2배 밀도로 충분해요. 지정한 뷰만 요청 크기를 `maxPixelDensity / 화면 밀도`로 줄여요. 지정하지 않은 뷰는 원본과 같은 요청을 만들어요.                                                                                                                                             |
| 위 두 변경의 단위 테스트                 | `android/src/test/java/com/fastimage/FastImageHardwareTest.java`, `FastImageDecodeDensityTest.java`, `FastImageScaleResizeTest.java`, `src/index.test.tsx`                                                                                      | 원본을 받을 때 두 변경이 그대로인지 확인해요.                                                                                                                                                                                                                                                                                             |
| 패키지 이름과 배포 정보                  | `package.json`(`name`, `version`, 주소, `frontiaCapabilities`), `RNFastImage.podspec`, `README.md` 첫 문단, 예제 `metro.config.js`·`app.config.js`, `scripts/verify.mts`                                                                        | npm의 `@frontia` 범위로 발행해요. 예제 화면은 원본의 `react-native-fast-image` import를 그대로 두고 metro가 패키지 이름으로 풀어요. `frontiaCapabilities.androidMaxPixelDensity`는 앱이 `maxPixelDensity` 지원 여부를 확인하는 표시예요.                                                                                                  |

하드웨어 비트맵과 `maxPixelDensity`가 원본에 반영되면 해당 줄을 지우고 원본 구현을 써요.

## 지원 범위

지원 범위는 원본과 같아요([docs/formats.md](docs/formats.md)). 앱의 최소 버전(Android 7, API 24)에서 알아 둘 것은 이거예요.

**Android 7부터 8.1까지(API 24–27)에서 움직이는 WebP와 AVIF는 지원하지 않아요.** 움직이는 WebP는 Android 9(API 28)부터, AVIF는 Android 14부터 지원해요.

지원하지 않는 범위에서 이 형식을 로드하면 이렇게 돼요.

- API 28 미만에서는 움직이는 이미지용 디코더(`FastImageAnimated`, Glide의 `AnimatedImageDecoder`)가 등록되지 않아요. 파일은 Android의 일반 비트맵 디코더로 가요.
- Android가 첫 프레임을 디코드하면 정지 이미지로 보이고 `onLoad`가 와요.
- Android가 디코드하지 못하면 `onError`(`error` 문자열)와 `onLoadEnd`(`ok: false`)가 와요. 뷰는 `placeholder`가 있으면 그것을 보여 주고, 없으면 비어 있어요.
- 디코드 실패는 Glide의 디코드 스레드에서 예외로 잡혀 로드 실패로 전달돼요. 이 라이브러리와 Glide의 코드에는 앱이 종료되는 경로가 없어요.
- 내려받은 파일은 디스크 캐시에 남아요. 같은 주소를 다시 로드하면 네트워크 요청 없이 같은 결과가 나와요.
- `FastImage.preload`는 원격 이미지를 내려받아 크기만 읽어요. 뷰에서 디코드하지 못하는 파일도 `ok: true`가 될 수 있어요.

## 원본 변경을 받는 절차

원본의 릴리스 태그를 일반 머지로 받아요. rebase, squash, force push는 쓰지 않아요. 원본 커밋이 `main` 이력에 그대로 있어야 다음 머지가 같은 변경을 다시 충돌시키지 않아요.

1. 새 릴리스를 확인해요.

    ```bash
    git fetch upstream --tags
    git log --oneline <지금 기준 태그>..<새 태그>
    ```

2. `main`에서 브랜치를 만들고 태그를 머지해요.

    ```bash
    git switch -c chore/upstream-<새 태그> origin/main
    git merge --no-ff <새 태그>
    ```

3. 충돌을 풀어요. 우리 변경이 있는 자리는 위 표의 파일이에요.
    - `package.json`: `name`, `version`, 주소, `frontiaCapabilities`는 우리 값을 두고 나머지는 원본을 받아요.
    - `README.md`: 첫 문단만 우리 것이에요. props 절은 `website`에서 `bun run generate`로 다시 만들어요.
    - `CHANGELOG.md`: 원본 그대로 받아요.
    - `FastImageViewWithUrl.java`, `FastImageSourceSize.java`: 원본 구조를 받고 그 위에 우리 변경을 다시 얹어요.
4. `package.json`의 `version`을 `<새 원본 버전>-frontia.1`로 바꾸고, 이 문서의 기준 표를 고쳐요.
5. 검사를 돌려요.

    ```bash
    bun install --frozen-lockfile
    bun run lint && bun run format:check && bun run typecheck
    bun run build:package && bun run test
    (cd website && bun install --frozen-lockfile && bun run check)
    (cd ReactNativeFastImageExample && bun install --frozen-lockfile \
        && cd android && ./gradlew :frontia_react-native-fast-image:testDebugUnitTest)
    ```

6. PR을 `main`으로 열고 **merge commit**으로 머지해요.
7. Android 실기기에서 배너의 하드웨어 디코드, `maxPixelDensity`를 지정한 화면과 지정하지 않은 긴 본문의 화질을 확인한 뒤 발행해요.

## CI

`ci`(lint, 형식, 타입, 빌드, JS 테스트, 예제 타입, 문서 검사)와 `e2e`(예제 앱 회귀와 Android 단위 테스트)는 원본의 워크플로 그대로예요. 발행(`release` job)은 원본 저장소에서만 돌아요. 이 포크는 사람이 직접 발행해요.
