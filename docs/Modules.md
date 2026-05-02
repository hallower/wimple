# Modules

패키지·클래스를 역할별로 정리. 한 줄 설명 + 파일 링크.

## UI 레이어 (`kr.blogspot.charlie0301.wimple`)

### Activities
| 클래스 | 역할 |
|---|---|
| [SplashScreenActivity](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/SplashScreenActivity.kt) | 첫 화면. 후잉 로그인 webview + token 교환 + 초기 데이터 로딩. |
| [WimpleActivity](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/WimpleActivity.kt) | 메인 호스트. drawer + toolbar + fragment 컨테이너. |
| [PaymentNoticeActivity](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/PaymentNoticeActivity.kt) | API 한도 초과 시 안내. |
| [PostNewsActivity](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/PostNewsActivity.kt) | 외부 앱에서 공유 인텐트 받아 후잉 BBS에 게시. |
| [BankNotificationListActivity](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/BankNotificationListActivity.kt) | 미전송 은행 알림 큐 표시·삭제. |
| [UnsupportedBankNotificationListActivity](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/UnsupportedBankNotificationListActivity.kt) | 후잉이 파싱 실패한 알림 표시·재전송·삭제. |
| [BankAppPickerActivity](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/BankAppPickerActivity.kt) | 시스템 설치된 앱 중 은행/카드/페이 앱 선택 UI. |
| [OpenSourceLicensesActivity](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/OpenSourceLicensesActivity.kt) | OSS 라이선스 전문 표시. |

### 핵심 기능 Fragments
| Fragment | 화면 |
|---|---|
| [TransactionInsertFragment](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/TransactionInsertFragment.kt) | 거래 입력 / 수정 / 매월 거래 등록. 계산기 패드, 자주 쓰는 거래. |
| [TransactionListFragment](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/TransactionListFragment.kt) | 거래 목록 + 검색 + 매월 거래 표시. |
| [FinancialStateSummaryFragment](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/FinancialStateSummaryFragment.kt) | 자산부채 종합 (BS) 차트. |
| [SavingStateSummaryFragment](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/SavingStateSummaryFragment.kt) | 자산 상세. |
| [DebtStateSummaryFragment](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/DebtStateSummaryFragment.kt) | 부채 상세. |
| [IncomeExpenseSummaryFragment](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/IncomeExpenseSummaryFragment.kt) | 비용수익 종합 (PL) 차트, 예산 비교. |
| [IncomeSummaryFragment](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/IncomeSummaryFragment.kt) / [ExpenseSummaryFragment](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/ExpenseSummaryFragment.kt) | 수익 / 지출 상세. |

### 두 칸 (sw600dp+) Pair Fragments
[TwoPaneFragment](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/TwoPaneFragment.kt) 베이스로 좌우 fragment 동시 호스팅:
- [TransactionPairFragment](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/TransactionPairFragment.kt) — 거래 입력 + 거래 목록
- [IncomeExpensePairFragment](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/IncomeExpensePairFragment.kt) — 수익 + 지출
- [SavingDebtPairFragment](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/SavingDebtPairFragment.kt) — 자산 + 부채

### 설정 Fragments
[SettingsHostFragment](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/SettingsHostFragment.kt) +
[SettingsFragment](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/SettingsFragment.kt) (공통 키 상수) +
헤더별:
- [GeneralSettingsFragment](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/GeneralSettingsFragment.kt) — 로그아웃, 섹션, FAB, 생체인증
- [EntrySettingsFragment](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/EntrySettingsFragment.kt) — 메모창, 매월 거래 표시
- [BankNotificationSettingsFragment](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/BankNotificationSettingsFragment.kt) — 은행 알림 모든 옵션
- [FinancialStateSettingsFragment](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/FinancialStateSettingsFragment.kt) / [IncomeExpenseSettingsFragment](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/IncomeExpenseSettingsFragment.kt) — 그래프 옵션
- [AboutSettingsFragment](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/AboutSettingsFragment.kt) — 문의 / OSS 라이선스

### 보조 컴포넌트
| 클래스 | 역할 |
|---|---|
| [MenuFragment](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/MenuFragment.kt) | enum: drawer 메뉴 id ↔ Fragment 팩토리 매핑 |
| [FloatingActionButtonController](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/FloatingActionButtonController.kt) | FAB 위치·아이콘·드래그 처리 |
| [BiometricOnboarding](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/BiometricOnboarding.kt) | 첫 로그인 후 생체인증 활성화 안내 |
| [WimpleListenerBinder](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/WimpleListenerBinder.kt) | impl ↔ fragment 메시지 라우팅 어댑터 |
| [CommandID](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/CommandID.kt) | UI ↔ impl 메시지 종류 식별자 |
| [IWimpleFragment](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/IWimpleFragment.kt) | 메인 fragment 공통 인터페이스 (`handleMessage`, `setActivityInstance`) |

### 위젯 / 어댑터 (`widget/`)
- [AccountExpandableListAdapter](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/widget/AccountExpandableListAdapter.java) / [AccountStateExpandableListAdapter](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/widget/AccountStateExpandableListAdapter.java) — 거래 입력 / 자산부채 화면의 계정 목록.
- [accountstate/](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/widget/accountstate/) / [budgetstate/](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/widget/budgetstate/) / [entry/](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/widget/entry/) — 각 row의 ItemView 와 ListAdapter 쌍.
- [DatePickerFragment](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/widget/DatePickerFragment.kt) — 날짜 선택 다이얼로그.

### 홈 화면 위젯
| 클래스 | 역할 |
|---|---|
| [MonthlySummaryWidgetProvider](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/MonthlySummaryWidgetProvider.kt) | AppWidgetProvider. 캐시 우선 렌더링, 월 이동·새로고침 핸들러, 백그라운드 fetch. |
| [MonthlySummaryWidgetService](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/MonthlySummaryWidgetService.kt) | RemoteViewsService. 캐시만 읽어 GridView 셀 생성. |

### 시스템 서비스 / 리시버
| 클래스 | 역할 |
|---|---|
| [BankNotificationListener](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/BankNotificationListener.kt) | NotificationListenerService. 선택된 은행 앱의 알림을 잡아 [BankNotifications](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/impl/BankNotifications.kt) 큐에 넣고, 임계 도달 시 forward. |

---

## Impl 레이어 (`kr.blogspot.charlie0301.wimple.impl`)

| 클래스 | 역할 |
|---|---|
| [WimpleImpl](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/impl/WimpleImpl.java) | 싱글턴 코어. 인증 / 세션 / API 메서드 / DB handler 보유. UI에서 `WimpleImpl.getInstance()`로 접근. |
| [IWimpleImpl](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/impl/IWimpleImpl.java) | 패키지 내부용 인터페이스 (Manager 클래스가 의존). |
| [EntryManager](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/impl/EntryManager.kt) | 거래 fetch / 등록 / 수정 / 삭제 task thread 모음. |
| [ItemManager](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/impl/ItemManager.kt) | 자주쓰는·최신·매월 item fetch task. |
| [RestAPIInvoker](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/impl/RestAPIInvoker.kt) | Jersey 1.x 기반 REST 클라이언트. `X-API-KEY` 헤더 생성, GET/POST/PUT/DELETE. |
| [RestResponseHandler](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/impl/RestResponseHandler.kt) | 401·402·405 등 공통 에러 코드 처리 (재로그인, 한도 초과 화면). |
| [BankNotifications](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/impl/BankNotifications.kt) | 은행 알림 캐시 (stored / pending / rejected) + per-package 그룹 전송. |
| [WhooingNotifications](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/impl/WhooingNotifications.kt) | 툴바 알림 인디케이터용 `api/notifications.json` GET (PUT은 호출 X). |

### `impl/db/` — SQLite 캐시
| 클래스 | 역할 |
|---|---|
| [DatabaseHandler](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/impl/db/DatabaseHandler.java) | SQLite open helper, 공통 CRUD. |
| [SQLQueries](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/impl/db/SQLQueries.java) | 공통 SQL 헬퍼. |
| [IDatabaseRecord](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/impl/db/IDatabaseRecord.java) / [DatabaseRecordImpl](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/impl/db/DatabaseRecordImpl.java) | 모델 클래스가 구현하는 DB 변환 인터페이스. |
| [AccountDBHandler](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/impl/db/AccountDBHandler.java) | 계정 캐시. |
| [AccountStateDBHandler](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/impl/db/AccountStateDBHandler.java) | 자산부채 행별 잔액. |
| [BudgetDBHandler](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/impl/db/BudgetDBHandler.java) | 예산 캐시. |
| [EntryDBHandler](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/impl/db/EntryDBHandler.java) | 거래 캐시. |
| [IncomeExpenseDBHandler](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/impl/db/IncomeExpenseDBHandler.java) | 비용수익 행별 합계. |
| [ItemDBHandler](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/impl/db/ItemDBHandler.java) | 자주쓰는·최신·매월 item. |
| [SectionDBHandler](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/impl/db/SectionDBHandler.java) | 섹션 목록. |
| [UserInfoDBHandler](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/impl/db/UserInfoDBHandler.java) | 사용자 정보. |

### `impl/util/` — 유틸리티
| 클래스 | 역할 |
|---|---|
| [Calculator](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/impl/util/Calculator.java) | 거래 입력 화면의 사칙연산 계산기. |
| [ChartUtils](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/impl/util/ChartUtils.java) | MPAndroidChart 도넛 차트 helper. |
| [DateFormatUtils](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/impl/util/DateFormatUtils.java) | `yyyyMMdd` 등 후잉 서버 포맷 변환. |
| [DrawableBitmapCache](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/impl/util/DrawableBitmapCache.java) | 비트맵 LRU 캐시. |
| [ImageUtils](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/impl/util/ImageUtils.java) | 원형 비트맵 등 가공. |
| [KoreanWordSearch](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/impl/util/KoreanWordSearch.kt) | 자주 쓰는 거래 한글 초성 검색. |
| [LocalFile](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/impl/util/LocalFile.java) | 내부 저장소 read/write. |
| [RemoteContent](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/impl/util/RemoteContent.java) | URL → byte 다운로드 (프로필 사진 등). |
| [SSLClientHelper](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/impl/util/SSLClientHelper.java) | Jersey HTTPS 신뢰 정책 (전체 신뢰 — 개선 여지). |
| [Utils](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/impl/util/Utils.java) | sha1, JSON helper, … 잡유틸. |
| [WidgetItem](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/impl/util/WidgetItem.java) | (앱 내) ImageView 비트맵 교체 helper. |
| [AndroidServiceIteratorProvider](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/impl/util/AndroidServiceIteratorProvider.java) | Jersey OSGi 서비스 iterator를 Android용으로 우회. R8 비활성 이유의 핵심. |

---

## Model 레이어 (`kr.blogspot.charlie0301.wimple.model`)

| 모델 | 의미 | 비고 |
|---|---|---|
| [Account](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/model/Account.java) | 후잉의 계정 (자산/부채/수익/비용/자본). | `what`이 분류, `id`는 `xN` 형식. |
| [AccountState](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/model/AccountState.java) | 특정 계정의 잔액·상태. | BS 화면 데이터. |
| [Budget](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/model/Budget.java) | 항목별 예산. | PL의 예산 비교. |
| [Entry](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/model/Entry.java) | 거래 1건. | `Item` 상속. `l_account*`, `r_account*`, `money`, `entry_date`, `memo`. |
| [Item](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/model/Item.java) | 거래의 공통 골격. | Entry / Frequent / Monthly item의 공통 부모. |
| [Section](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/model/Section.java) | 가계부 단위. | 한 사용자가 여러 섹션 보유 가능. |
| [UserInfo](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/model/UserInfo.java) | 사용자 프로필. | API count, 프로필 이미지 등. |

---

## Listener 인터페이스

| 인터페이스 | 역할 |
|---|---|
| [IWimpleResponseListener](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/impl/IWimpleResponseListener.java) | API 응답 콜백. `WimpleListenerBinder`가 메시지로 변환. |
| [IWimpleStatusListener](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/impl/IWimpleStatusListener.java) | 인증·초기화 상태 변경 알림. |
