# 은행 알림 로컬 검수 · AI 분류 입력

기존 외부입력(`api/entries/outside.json`) 경로와 별개로, 은행 알림을 단말에
저장해 두었다가 사용자가 Wimple에서 직접 검수·확정해 거래로 등록하는
기능. 학습된 매핑과 단말 내 AI(Gemini Nano)로 자동 분류를 보조한다.

---

## 1. 요구사항

| # | 항목 |
|---|---|
| R1 | 외부입력과 동시 사용 가능. 둘 다 켜진 경우 사용자에게 중복 기장 가능성 안내 |
| R2 | 알림 분류는 단말 AI(Gemini Nano)만 사용. 미지원 기기에서는 본 기능 설정을 disable |
| R3 | 분류 결과는 거래명·금액·좌/우 항목으로 구성, 모호한 경우 사용자가 선택 |
| R4 | 사용자의 확정·수정 결과는 단말에 저장되어 다음 알림 분류에 활용 |
| R5 | 가맹점 카테고리 분류 외에, **저장된 entry 중 가장 유사한 건**을 찾아 좌/우 항목 제안에 활용 |
| R6 | 사용자가 AI 제안과 다른 좌/우 항목으로 변경하면 자동 학습 (별도 토글 없음) |
| R7 | UI 진입은 `WimpleActivity` 툴바의 검수 큐 뱃지를 통해서 |
| R8 | 검수 항목 입력은 풀스크린 신규 입력 화면 |

---

## 2. 사용자 시나리오

### 2.1 설정

1. 사용자가 설정 → 은행 알림에서 "로컬 검수 큐" 토글 ON.
2. 단말이 Gemini Nano Prompt API를 지원하지 않으면 토글이 비활성. summary에
   "이 기기에서는 지원하지 않습니다" 표기.
3. 외부입력 토글이 함께 켜져 있으면 ON 시점에 한 번 안내 다이얼로그 노출:
   "외부입력과 동시에 사용하면 동일 알림이 두 번 기장될 수 있습니다."

### 2.2 알림 수신 ~ 검수

1. 모니터링 대상 앱 알림이 [BankNotificationListener](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/BankNotificationListener.kt)
   에 도착.
2. 토글 ON이면 **로컬 검수 큐**에도 적재 (외부입력 큐와 병행).
3. Wimple 실행 시 `WimpleActivity` 툴바에 미검수 카운트 뱃지 노출.
4. 사용자가 뱃지를 탭 → `BankNotificationReviewActivity` (큐 화면) 진입.
5. 큐 화면 진입 직후 미분류 항목에 대해 분류 캐스케이드(§4) 실행.
6. 항목별 상태(READY / AMBIGUOUS / UNPARSED)에 따라 카드 액션 노출.
7. 사용자가 [확정] / [선택] / [수동입력] / [무시] 중 하나 수행.
8. [확정] · [선택 후 확정] · 수동입력 완료 시 기존
   `WimpleImpl.makeEntry(...)` 경로로 후잉에 거래 등록.
9. 사용자가 AI 제안과 다르게 좌/우 항목을 수정한 경우, 매핑 테이블에
   upsert (자동 학습).

---

## 3. UI 설계 — 추천안 C (하이브리드)

### 3.1 큐 화면 (`BankNotificationReviewActivity`)

상단:

- 외부입력 동시 사용 시 1회성 배너 ("외부입력도 켜져 있어 중복 기장될 수
  있습니다 [설정으로]")
- "READY N건 모두 확정" 일괄 버튼 (READY ≥ 1일 때 활성)
- 정렬: AMBIGUOUS / UNPARSED 우선 노출

카드 레이아웃:

```
┌──────────────────────────────────────────┐
│ GS25 강남점               12,000원       │ ← 추출된 거래명·금액
│ 식비  ←  현금                            │ ← 추정 좌/우 항목
│ 카카오뱅크 · 09:32                       │ ← 출처/시각
│ ▸ 알림 원문 보기                         │ ← 토글로 raw 본문 확인
│                  [무시]  [선택]  [확정]   │ ← 상태별 액션
└──────────────────────────────────────────┘
```

상태별 액션:

| 상태 | 조건 | 액션 |
|---|---|---|
| `READY` | 매핑 hit 또는 AI 고신뢰도 단일 후보 | [확정] 1탭 → `makeEntry()` |
| `AMBIGUOUS` | AI 후보 2개 이상 / 신뢰도 임계 미만 | [선택] → 풀스크린 디테일 화면 |
| `UNPARSED` | 금액·가맹점 추출 실패 | [수동입력] → 풀스크린 디테일, 본문만 prefill |
| `CONFIRMED` | makeEntry 성공 | 짧게 표시 후 큐에서 제거 |

기타:

- 길게 누르면 큐에서 제거 (기존 [BankNotificationListActivity](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/BankNotificationListActivity.kt) 패턴 일치).
- 툴바 메뉴: "모두 무시" / "매핑 관리".

### 3.2 풀스크린 입력 화면 (`BankNotificationReviewDetailActivity`)

- 상단 패널: 알림 원문(앱 라벨 / 시각 / title / text) — 접고 펼치기 가능
- 본문: [TransactionInsertFragment](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/TransactionInsertFragment.kt)
  의 입력 위젯 호스팅 (계산기, [AccountExpandableListAdapter](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/widget/AccountExpandableListAdapter.java)
  좌/우 선택 등 재사용)
- 거래명·금액·좌/우 항목 모두 사용자 수정 가능
- 하단: [확정] 버튼. 확정 시 `makeEntry()` + 매핑 학습(자동)

---

## 4. AI 분류 캐스케이드

```
[은행 알림]
   │
   ▼
1단: MerchantMappingDB 정확 일치
   │
hit?├─ YES ──▶ 매핑된 (좌/우, 거래명) → READY
no  ▼
2단: Prompt API + EntryDB 유사도
   │  입력 = 알림 본문 + 후보 entry 요약 JSON
   │  출력 = { best_match_entry_id, confidence,
   │           extracted: { kind, merchant, amount } }
   ▼
검증: best_match_entry_id 가 EntryDB에 살아있고
       l/r account가 AccountDB에 살아있는지 확인
   │
confidence ≥ 0.8 ──▶ 그 entry의 좌/우 차용 → READY
0.5 ≤ conf < 0.8 ──▶ 후보 2~3개 + extracted → AMBIGUOUS
conf < 0.5 / 추출 실패 / 검증 실패 ──▶ UNPARSED
```

### 4.1 EntryDB 후보 추리기

전체를 AI에 넣으면 프롬프트가 비대해지므로 1차 필터:

- 최근 90일 + 매월거래 제외 entries 중
- 알림 본문에서 추출한 토큰과 `Entry.title` / `memo` 부분 일치 OR
- 알림 금액과 ±20% 범위 매칭
- union으로 최대 50건만 AI에 전달

### 4.2 프롬프트 출력 강제

Prompt API 응답을 다음 JSON 스키마로 강제:

```json
{
  "best_match_entry_id": "string",
  "confidence": 0.0,
  "extracted": {
    "kind": "expense | income | transfer",
    "merchant": "string",
    "amount": 0
  }
}
```

파싱 실패 시 즉시 UNPARSED 처리. AI 환각으로 존재하지 않는 entry id를
반환할 수 있어 항상 EntryDB·AccountDB로 재검증.

### 4.3 동시성

큐 화면 진입 시 미분류 항목을 **직렬** 호출 + 진행률 표시. ML Kit GenAI는
single-shot 호출이라 병렬도 가능하지만 메모리 부담을 고려.

### 4.4 GenAI 가용성 제약

- 포그라운드에서만 동작 (`ErrorCode.BACKGROUND_USE_BLOCKED`).
  → 분류는 백그라운드 알림 수신 시점이 아닌, 큐 화면 진입 시점에서 수행.
- 기기 요건: Pixel 9+ / Galaxy S24+ 등 일부 플래그십. 미지원 기기에서는
  설정 토글 자체를 disable (R2).
- `FeatureStatus`: AVAILABLE / DOWNLOADABLE / UNAVAILABLE 분기. DOWNLOADABLE은
  토글은 활성화하되 첫 사용 시 다운로드 트리거 안내.

---

## 5. 자동 학습 정책

매핑 키: 정규화된 가맹점 문자열 + 거래종류(`expense`/`income`/`transfer`).
공백·특수문자 정리, lowercase. 거래종류를 키에 포함하는 이유는 환불·입금
케이스에서 좌/우가 반대로 잡혀야 하기 때문.

| 사용자 액션 | MerchantMappingDB 동작 |
|---|---|
| READY 그대로 [확정] | 매핑에서 온 거면 `last_used` 갱신, AI에서 온 거면 신규 insert |
| AMBIGUOUS에서 선택 후 확정 | 선택한 좌/우로 신규 insert |
| 좌/우 수정 후 확정 | 수정 후 좌/우로 **upsert** (자동 학습) |
| [무시] | 학습 없음 |

---

## 6. 데이터 모델

### 6.1 `LocalReviewQueue` (SharedPreferences JSON)

[BankNotifications](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/impl/BankNotifications.kt)
와 동일 패턴. 외부입력 큐와 별도 인스턴스/키로 보관.

```json
[
  {
    "t": 1714800000000,
    "p": "com.kakaobank.channel",
    "label": "카카오뱅크",
    "title": "출금 12,000원",
    "text": "GS25 강남점 12,000원 출금 ...",
    "id": "uuid",
    "state": "PENDING | READY | AMBIGUOUS | UNPARSED | CONFIRMED",
    "classification": { /* §4.2 출력 캐시 */ }
  }
]
```

### 6.2 `merchant_mapping` 테이블 (SQLite)

| 컬럼 | 타입 | 비고 |
|---|---|---|
| `merchant_norm` | TEXT | PK 일부, 정규화 가맹점명 |
| `kind` | TEXT | PK 일부, expense/income/transfer |
| `l_account_type` | TEXT | |
| `l_account_id` | TEXT | |
| `r_account_type` | TEXT | |
| `r_account_id` | TEXT | |
| `last_used` | INTEGER | epoch ms, 정렬·가지치기용 |
| `hit_count` | INTEGER | 학습 강도 표시용 |

PK = (merchant_norm, kind). upsert 시 hit_count 증가.

---

## 7. 영향 받는 파일

### 7.1 신규

| 파일 | 역할 |
|---|---|
| `impl/LocalReviewQueue.kt` | 외부입력과 별개 큐 (SharedPreferences JSON) |
| `impl/BankNotificationClassifier.kt` | Prompt API 래핑 + EntryDB 후보 추리기 + 매핑 조회/upsert + 결과 검증 |
| `impl/db/MerchantMappingDBHandler.kt` | 학습 매핑 SQLite 핸들러 |
| `BankNotificationReviewActivity.kt` | 큐 리스트 화면 (§3.1) |
| `BankNotificationReviewDetailActivity.kt` | 풀스크린 입력 화면 (§3.2) |

### 7.2 수정

| 파일 | 변경 |
|---|---|
| [BankNotificationListener.kt](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/BankNotificationListener.kt) | 토글 ON이면 LocalReviewQueue에도 enqueue |
| [BankNotificationSettingsFragment.kt](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/BankNotificationSettingsFragment.kt) | GenAI status check + 신규 토글, 미지원 시 disable, 동시 사용 안내 |
| [WimpleActivity.kt](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/WimpleActivity.kt) | 툴바 검수 큐 뱃지 + 진입 핸들러 |
| [DatabaseHandler.java](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/impl/db/DatabaseHandler.java) | `merchant_mapping` 테이블 추가 + DB 버전 bump |
| [WimpleImpl.java](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/impl/WimpleImpl.java) | `MerchantMappingDBHandler` 보유 + classifier 진입점 |
| `wimple/app/build.gradle` | ML Kit GenAI Prompt API 의존성 추가 |

---

## 8. 구현 단계

각 단계가 독립 PR 가능.

| Phase | 범위 | 검증 가능 지점 |
|---|---|---|
| 1. 큐 인프라 | LocalReviewQueue + Listener 분기 + 설정 토글(미지원 disable) | 알림이 두 큐에 동시 적재되는지 로그로 확인 |
| 2. 큐 화면 | `BankNotificationReviewActivity`, 툴바 뱃지, 분류 없이 raw 본문만 표시 + [수동입력] 동작 | UI 흐름 확인 |
| 3. 매핑 학습 | `MerchantMappingDBHandler` + 1단(정확 일치) 분류 + 자동 upsert | 같은 가맹점 두 번째부터 자동 채움 |
| 4. AI 유사도 | `BankNotificationClassifier` + Prompt API + EntryDB 후보 추리기 + confidence 분기 | 새 가맹점 AI 제안 동작 |
| 5. 마무리 | 외부입력 동시 사용 배너, 일괄 확정, 풀스크린 디테일의 알림 원문 패널 | 통합 테스트 |

---

## 9. 미결 사항

- **DB 마이그레이션 정책** — `merchant_mapping` 추가 시 [DatabaseHandler](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/impl/db/DatabaseHandler.java)
  의 onUpgrade 처리 방향. 기존 캐시 drop 가능 여부 (후잉 재페치 가능하므로
  일반적으론 OK).
- **Gradle 의존성 추가** — ML Kit GenAI Prompt API 정확한 아티팩트명·버전은
  추가 시점 공식 문서로 재확인 필요.
- **CONFIRMED 카드 잔류 시간** — 확정 직후 짧게 표시 후 사라지게 할지 즉시
  사라지게 할지 (UX 결정).
