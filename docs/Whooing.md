# Whooing 정보

Wimple 코드를 이해하려면 [whooing.com](https://whooing.com) 의 데이터 모델과 API를
어느 정도 알아야 함. 후잉 측 공식 문서는 <https://whooing.com/api/md>.

이 문서는 Wimple이 실제로 사용하는 만큼만 정리.

## 후잉이란

후잉은 **복식부기(double-entry bookkeeping) 가계부** 웹서비스. 일반 가계부와 달리
모든 거래가 양쪽 계정의 변동으로 표현됨 (`수익이 있으면 자산이 같이 증가`,
`비용을 쓰면 자산이 같이 감소`). 회계의 '차변/대변' 모델을 그대로 적용.

## 핵심 개념

### 사용자 (user) → 섹션 (section) → 계정 (account) → 거래 (entry)

- **user**: 후잉 계정 1명.
- **section**: 가계부 단위. 한 사용자가 여러 섹션을 둘 수 있음 (예: 개인 / 사업).
  Wimple은 한 번에 하나의 default section만 보여주며, 설정에서 전환 시 앱이
  재시작됨.
- **account**: 섹션 안에서 사용하는 계정. 5종류로 분류됨 (아래).
- **entry**: 거래 1건. 왼쪽(차변) 계정 + 오른쪽(대변) 계정 + 금액으로 구성.

### 5가지 계정 분류 (`Account.what` 값)

| `what` 값 | 한글 | 용도 |
|---|---|---|
| `assets` | 자산 | 현금, 통장, 주식, 부동산 등 보유 자원 |
| `debts` | 부채 | 카드빚, 대출 등 갚아야 할 돈 |
| `capital` | 자본 | 순자산 (자산 - 부채) — 보통 "이월" 계정 |
| `income` | 수익 | 월급, 이자 등 들어오는 돈 |
| `expenses` | 비용 | 식비, 교통비 등 나가는 돈 |

> 주의: API 응답은 `incomes` / `expenses` 같은 **복수형**으로 옴. Wimple 코드는
> `account.getWhat()` (또는 Account 모델 `what` 필드)로 비교.

### 계정 ID 형식

`x{seq}` 문자열. 예: `x21`, `x35`. 자릿수는 가변 (`x0` ~ `xNN+`).

특수 ID:
- **`x0`** — 후잉이 deleted entry를 표시할 때 쓰는 tombstone. Wimple은 무시함
  ([EntryManager.kt:122](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/impl/EntryManager.kt#L122)).

### 거래(entry) 구조

API의 `results.rows[i]`:

| 필드 | 의미 |
|---|---|
| `entry_id` | 고유 ID |
| `entry_date` | `yyyyMMdd` |
| `l_account` | 왼쪽(차변) 계정 이름 |
| `l_account_id` | 왼쪽 계정 ID (`x{seq}`) |
| `r_account` | 오른쪽(대변) 계정 이름 |
| `r_account_id` | 오른쪽 계정 ID |
| `item` | 거래명 (예: "점심", "월급") |
| `money` | 금액. 부호 없음. |
| `memo` | 메모 |
| `app_id` | 어떤 앱에서 입력했는지 (Wimple = `140`) |

### 복식부기 → 수익/지출 분류 룰

Wimple 위젯이 일별 수익·지출을 집계할 때 쓰는 룰
([MonthlySummaryWidgetProvider.kt fetchMonth](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/MonthlySummaryWidgetProvider.kt)):

| 거래 형태 | l_account.what | r_account.what | 분류 |
|---|---|---|---|
| 수익 발생 (월급 등) | `assets` | `income` | **+income** |
| 수익 환불 / 취소 | `income` | `assets` | **-income** |
| 비용 지출 (식비 등) | `expenses` | `assets` | **+expense** |
| 비용 환불 | `assets` | `expenses` | **-expense** |
| 자산 ↔ 자산 (이체) | `assets` | `assets` | 무시 |
| 부채 상환 | `debts` | `assets` | 무시 |
| 그 외 (자본 조정 등) | — | — | 무시 |

`pl.json_array` (서버 측 손익 집계) 와 동일한 결과를 클라이언트에서 재현하기 위한
경험적 룰. 후잉 측 공식 분류 룰이 아니라는 점에 주의.

---

## Wimple이 사용하는 API 엔드포인트

전체 정의는 [WimpleImpl.Path](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/impl/WimpleImpl.java) (line 353+).

### 인증
| 메서드·경로 | 용도 |
|---|---|
| `POST app_auth/request_token` | 임시 토큰 요청 |
| (webview) `app_auth/authorize` | 사용자가 후잉 페이지에서 PIN 발급 |
| `POST app_auth/access_token` | PIN → 정식 token / tokenSecret 교환 |

### 사용자 / 섹션 / 계정
| 메서드·경로 | 용도 |
|---|---|
| `GET api/user.json` | 프로필, 잔여 API 횟수 |
| `GET api/sections.json` | 섹션 목록 |
| `GET api/sections/default.json` | 기본 섹션 |
| `GET api/accounts.json_array?section_id=…` | 모든 계정 (5종 그룹별) |

### 거래 (entry)
| 메서드·경로 | 용도 |
|---|---|
| `GET api/entries.json_array?section_id=…&start_date=…&end_date=…&limit=…` | 거래 목록 |
| `GET api/entries/latest.json_array` | 최신 거래 (페이징 없이) |
| `POST api/entries.json` | 새 거래 등록 |
| `PUT api/entries/{id}` | 거래 수정 |
| `DELETE api/entries/{id}` | 거래 삭제 |

### 외부입력 (외부 자동 등록)
| 메서드·경로 | 용도 |
|---|---|
| `POST api/entries/outside.json` | 알림·SMS 같은 외부 텍스트를 후잉 파서에 던짐 |
| `POST api/entries/outside_report.json` | 파싱 실패 보고 |

후잉이 알림 본문을 자체 룰로 파싱해서 entry로 만들어주는 엔드포인트. Wimple은 이걸
은행/카드 푸시 알림 자동 기록에 사용
([BankNotifications.kt:29](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/impl/BankNotifications.kt#L29)).

### 자주쓰는·최신·매월 item
| 메서드·경로 | 용도 |
|---|---|
| `GET api/frequent_items.json_array` | 자주 쓰는 거래 |
| `GET api/entries/latest_items.json_array` | 최근 거래명 |
| `GET api/monthly_items.json_array` | 매월 자동 등록 거래 |
| `DELETE api/monthly_items/slot1/{id}` | 매월 거래 삭제 |

### 자산부채 / 비용수익
| 메서드·경로 | 용도 |
|---|---|
| `GET api/bs.json_array?date=…` | BS (대차대조표) — 자산부채 화면 |
| `GET api/pl.json_array?start_date=…&end_date=…` | PL (손익계산) — 비용수익 화면 |
| `GET api/budget/income.json_array` | 수익 예산 |
| `GET api/budget/expenses.json_array` | 지출 예산 |

### 알림 / 게시판
| 메서드·경로 | 용도 |
|---|---|
| `GET api/notifications.json` | 안 읽은 알림 수 (`results.notification.badgeCount`). Wimple toolbar에 종 아이콘 표시. |
| (PUT api/notifications.json — Wimple은 호출 X. 사용자가 직접 사이트에서 읽어야 함.) | |
| `POST api/bbs/moneynews.json` | 머니뉴스 게시 |

---

## 인증 헤더 (`X-API-KEY`)

모든 API 호출 시 `RestAPIInvoker`가 다음 형식의 헤더를 붙임
([RestAPIInvoker.kt](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/impl/RestAPIInvoker.kt)):

```
X-API-KEY: app_id=<APP_ID>,token=<TOKEN>,nounce=<INC>,timestamp=<MS>,signiture=<SHA1>
```

- `app_id` — Wimple은 `140` (후잉에서 발급).
- `token` — `app_auth/access_token` 응답에서 받은 값. SharedPreferences에 보관.
- `nounce` — 앱 내부 카운터 (`WimpleImpl.sequence` 시작값 10000, 매 호출 +1).
- `timestamp` — `currentTimeMillis()`.
- `signiture` (오타지만 후잉 서버 사양 그대로) — `sha1(app_secret + "|" + tokenSecret)`.

`app_secret`은 [BuildConfig.WIMPLE_APP_SECRET](../wimple/app/build.gradle) 에서
주입 — `local.properties` (gitignored)나 환경변수 `WIMPLE_APP_SECRET`에 저장된 값.

---

## API 호출 한도 / 에러 코드

후잉은 사용자 1명당 일일 API 호출 횟수에 무료/유료 등급별 한도가 있음. 주요 에러
코드 ([RestResponseHandler.kt](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/impl/RestResponseHandler.kt)):

| code | 의미 | Wimple 처리 |
|---|---|---|
| 200 | 성공 | 정상 |
| 402 | 보고서 조회 횟수 초과 | `PaymentNoticeActivity` 띄워 업그레이드 안내 |
| 405 | 인증 만료 / 권한 해제 | 로그아웃 처리 + 로그인 화면으로 |

응답에 `rest_of_api` 필드가 있어 매 호출마다 잔여 횟수 동기화 → drawer header에 표시.

알림 폴링 권장 주기는 후잉 측이 5분 이상으로 안내 (현재 Wimple은 앱 시작 시 1회만
호출).

---

## 외부입력 페이로드 형식

후잉이 알림 본문을 SMS처럼 파싱하므로 Wimple은 다음 형식으로 가공해서 보냄
([BankNotifications.buildPayloadFromArray](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/impl/BankNotifications.kt)):

```
{bank} {title?} {text} {MM/dd HH:mm?}\n
```

- 은행 라벨이 맨 앞 (괄호 없이) — 실제 SMS 관습 모방.
- `title`이 라벨과 같으면 생략 (MG새마을금고처럼 알림 제목이 앱 이름인 경우 중복 방지).
- 본문에 이미 `MM/dd` 패턴이 있으면 timestamp 추가하지 않음.
- 줄바꿈 → 공백, 2칸 이상 공백 → 1칸 (은행 앱이 정렬용으로 빈칸을 남발하는 케이스 대응).

페이로드는 `section_id=<id>&rows=<URL-encoded>` 로 `outside.json`에 POST.

응답:
- `code=200, results.cnt = N` → 후잉이 `N`건을 임시저장소에 등록.
- `code=200, results.cnt = 0` → 받았지만 파싱 실패. Wimple은 warning 로그를 남기고
  drop (서버 파서 미지원이라 재시도 무의미).
- `code=400` → 영구 거부. Wimple의 rejected store로 이동. 사용자가 설정에서 확인·재시도·삭제 가능.
- 기타 (5xx, network) → pending에 남겨 다음 onResume에서 재시도.
