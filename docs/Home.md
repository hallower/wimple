# Wimple Wiki

[Wimple](https://github.com/hallower/wimple) — 후잉(Whooing) 서비스의 제3자 Android
클라이언트.

이 wiki는 코드를 읽거나 수정하기 위한 개발자용 문서입니다. 일반 사용자
대상 사용 설명서는 [USAGE.md](../USAGE.md), 빌드·라이선스 등은
[README.md](../README.md)를 참고하세요.

## 목차

| 문서 | 내용 |
|---|---|
| [Architecture](Architecture.md) | 전체 구조 / 레이어 / 스레딩 모델 / Listener 패턴 |
| [Modules](Modules.md) | 주요 클래스·패키지 한 줄 설명 |
| [Sequences](Sequences.md) | 인증·거래 입력·은행 알림·위젯 등 핵심 흐름 |
| [Whooing](Whooing.md) | 후잉 서비스의 데이터 모델과 사용하는 API |
| [Usage](../USAGE.md) | 일반 사용자용 사용 설명서 (root) |
| [README](../README.md) | 프로젝트 소개·빌드·라이선스 (root) |

## 한 눈에 보는 Wimple

- **언어**: Kotlin + Java 혼용. UI는 Kotlin 위주, `WimpleImpl` 등 코어 impl은 Java 잔존.
- **빌드**: AGP 8 / `compileSdk 36` / `minSdk 26` (Android 8+).
- **외부 통신**: Whooing REST API만 호출 (`https://whooing.com`). Jersey 1.x 클라이언트.
- **저장소**: 단말 SQLite 캐시 (`AccountDBHandler` 등 7종) + `SharedPreferences`.
- **분배**: Google Play (`com.blogspot.charlie0301`) + 본 OSS repo.

## 주요 기능 → 코드 진입점

| 기능 | 진입점 |
|---|---|
| 거래 입력·수정 | [TransactionInsertFragment.kt](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/TransactionInsertFragment.kt) |
| 거래 목록 | [TransactionListFragment.kt](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/TransactionListFragment.kt) |
| 자산부채 (BS) | [FinancialStateSummaryFragment.kt](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/FinancialStateSummaryFragment.kt) |
| 비용수익 (PL) | [IncomeExpenseSummaryFragment.kt](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/IncomeExpenseSummaryFragment.kt) |
| 은행 알림 자동 기록 | [BankNotificationListener.kt](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/BankNotificationListener.kt), [BankNotifications.kt](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/impl/BankNotifications.kt) |
| 홈 화면 위젯 | [MonthlySummaryWidgetProvider.kt](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/MonthlySummaryWidgetProvider.kt) |
| 후잉 알림 인디케이터 | [WhooingNotifications.kt](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/impl/WhooingNotifications.kt) |
| 인증·세션·사용자 정보 | [WimpleImpl.java](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/impl/WimpleImpl.java), [SplashScreenActivity.kt](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/SplashScreenActivity.kt) |
