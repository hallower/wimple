# Architecture

## 레이어 구조

```
+-------------------------------------------------------------+
|  Activities & Fragments  (kr.blogspot.charlie0301.wimple)   |
|  - SplashScreenActivity  (login flow)                       |
|  - WimpleActivity        (drawer host, menu, toolbar)        |
|  - *Fragment             (입력/목록/요약/설정)               |
|  - *Activity             (PostNews, BankAppPicker, …)        |
|  - widget/* adapters & item views                            |
+----------------------↕--------------------------------------+
|  WimpleListenerBinder  ←  IWimpleResponseListener (callbacks)|
+----------------------↕--------------------------------------+
|  Impl layer  (kr.blogspot.charlie0301.wimple.impl)           |
|  - WimpleImpl  (singleton; orchestrates everything)          |
|  - EntryManager / ItemManager  (per-domain task threads)     |
|  - RestAPIInvoker             (Jersey-based REST client)     |
|  - BankNotifications           (notification → outside.json) |
|  - WhooingNotifications        (toolbar badge fetcher)       |
|  - util/*  (Calculator, ChartUtils, DateFormatUtils, …)      |
+----------------------↕--------------------------------------+
|  Persistence (DB + SharedPreferences)                        |
|  - DatabaseHandler         (SQLite open helper)              |
|  - 7종 *DBHandler          (Account / Entry / Item / …)      |
|  - SharedPreferences       (session, settings, widget cache) |
+----------------------↕--------------------------------------+
|  External: Whooing REST  https://whooing.com/api/...         |
+-------------------------------------------------------------+
```

각 레이어 역할:

- **UI**: 화면 그리기 + 사용자 입력 수집. WimpleImpl을 직접 호출.
- **Impl**: 비즈니스 로직, 인증, REST 호출, 캐시 관리. UI 콜백은 메시지 핸들러로 비동기 전달.
- **Persistence**: SQLite로 후잉 응답을 캐싱. SharedPreferences로 로그인 토큰·설정·위젯 상태 저장.
- **External**: Whooing REST 외에 어떤 서버와도 통신하지 않음.

## 핵심 엔진: `WimpleImpl`

[WimpleImpl.java](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/impl/WimpleImpl.java)
는 ~2200줄짜리 싱글턴 (`getInstance()`). 거의 모든 비즈니스 로직의 진입점입니다.

- **상태**: 인증 토큰, 섹션 ID, 사용자 정보, 7종 DB handler, semaphore 맵 등
- **API 메서드**: `getAllEntries`, `getAllAccounts`, `getFinancialState`, `makeEntry` …
- **응답 전달**: 백그라운드 Thread에서 REST 호출 → 완료되면 `sm(cmd, status, ...)`로
  메인 핸들러에 메시지 전송 → `WimpleListenerBinder`가 받아 등록된
  `IWimpleResponseListener`에 dispatch → 현재 Fragment의 `handleMessage`가 처리.

## 스레딩 모델

- **메인 스레드**: 모든 UI 변경, DB read (SQLite는 thread-safe 하지만 단순화 위해
  주로 메인에서 read), `SharedPreferences` 접근, `RemoteViews` 변경.
- **백그라운드 Thread**: 네트워크 호출. `WimpleImpl` 안의 task class들이 `Thread`를
  내부에서 직접 생성·시작 (`new XxxTaskThread().start()`). Coroutine·RxJava 미사용.
- **세마포어**: `apiAvailableSemaphore` 맵 — 각 API 키별로 동시 1회만 호출되도록 제한
  (`tryAcquire()` 후 finally에서 release).
- **메인 → 메인 콜백 전달**: `Handler` 기반 `Message` 라우팅
  ([CommandID.kt](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/CommandID.kt) 참고).

## Listener / Handler 패턴

```
Fragment              WimpleListenerBinder       WimpleImpl
   |  setActivity()         |                        |
   |----------------------->|                        |
   |  (registers as         |   register listener    |
   |   IWimpleResponseListener) ------------------->|
   |                        |                        |
   |  invoke API            |                        |
   |---------------------------------------------->  |
   |                        |                        |  Thread.start()
   |                        |                        |    └─ REST call
   |                        |                        |    └─ DB cache
   |                        |    sm(CMD_X, ...)      |
   |                        | <----------------------|
   |  Message               |                        |
   |  (dispatched on main)  |                        |
   | <----------------------|                        |
   |  handleMessage(msg)    |                        |
   |  └─ updateUI()         |                        |
```

UI는 직접 callback을 받지 않고 `Message` 객체를 통해 cmd ID + payload로 전달받습니다.
[WimpleListenerBinder.kt](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/WimpleListenerBinder.kt)
가 listener 인터페이스를 메시지로 변환해주는 어댑터 역할.

## 화면 구성 (`WimpleActivity` + Fragment)

- **`WimpleActivity`** = drawer + toolbar host. 각 메뉴 항목은 fragment id로 매핑.
- **`MenuFragment`** enum이 `menuId → Fragment factory` 매핑 테이블.
- **`replaceWimpleFragment(menuId)`** 가 fragment 교체.
- **두 칸 레이아웃** (sw600dp+): `TwoPaneFragment` / `TransactionPairFragment` /
  `IncomeExpensePairFragment` / `SavingDebtPairFragment` 등이 좌우 두 fragment를
  동시에 호스팅.

## 데이터 플로우 (대표 케이스: 거래 입력)

```
[User] ──tap submit──▶ TransactionInsertFragment
                                │
                                ▼
                     WimpleImpl.makeEntry(...)
                                │
                                ▼
                     EntryManager.makeEntry()
                                │
                                ▼ (Thread)
                     PostEntryTaskThread.run()
                       ├─ RestAPIInvoker.POST(api/entries.json, …)
                       ├─ EntryDBHandler.insert (cache update)
                       └─ sm(CMD_ENTRY_INSERT, success, msg)
                                │
                                ▼ (Handler dispatch)
                     WimpleListenerBinder
                                │
                                ▼
                     TransactionInsertFragment.handleMessage()
                                │
                                ▼
                     UI 토스트 / 화면 갱신
```

## 인증 / 세션 보존

- 첫 로그인은 `SplashScreenActivity` 안의 webview로 후잉 페이지에서 진행. 결과 PIN을
  받아 `app_auth/access_token`으로 token 교환.
- 받은 `token` / `tokenSecret`은 `WimpleImpl.settingsKey`
  (`SharedPreferences`)에 저장, 재실행 시 자동 로드.
- 모든 API 호출 시 `RestAPIInvoker`가 `X-API-KEY` 헤더 생성:
  `app_id=…,token=…,nounce=…,timestamp=…,signiture=sha1(app_secret|tokenSecret)`.
- `app_secret` 자체는 `WIMPLE_APP_SECRET` 환경변수 또는 `local.properties`에서
  읽어 `BuildConfig`로 주입 (소스코드 비포함).
