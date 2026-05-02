# Sequences

핵심 흐름 6개. 각 다이어그램은 시간 순서. 화살표 옆에는 호출 메서드 / 메시지명.

## 1. 첫 실행 — 인증·초기화

```
User           SplashScreenActivity     Webview          Whooing                  WimpleImpl
  │  앱 실행          │                    │                │                          │
  │ ────────────────▶│ onCreate           │                │                          │
  │                  │ load tokenSecret   │                │                          │
  │                  │  from SharedPrefs  │                │                          │
  │                  │                    │                │                          │
  │                  │  empty?  ──Yes────▶│ open whooing.com/app_auth/authorize       │
  │                  │                    │                │                          │
  │  로그인 ──────────────────────────────▶│                │                          │
  │                  │                    │ ◀──── PIN ─────│                          │
  │                  │ getAccessToken(PIN)─────────────────────────────────────────▶  │
  │                  │                                                                │
  │                  │                                       POST app_auth/access_token
  │                  │                                       (with app_secret + PIN)
  │                  │                                                                │
  │                  │                          ◀──── token, tokenSecret ────────────│
  │                  │ persist tokens to SharedPrefs                                  │
  │                  │ getUserInfo()──────────────────────────────────────────────▶   │
  │                  │ getDefaultSections()────────────────────────────────────▶      │
  │                  │ getAllAccounts()────────────────────────────────────▶          │
  │                  │ getAllItems()──────────────────────────────────▶               │
  │                  │ ◀── isInitializedFinished=true ───────────────                 │
  │                  │ startActivity(WimpleActivity)                                  │
```

핵심 파일:
- [SplashScreenActivity.kt](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/SplashScreenActivity.kt)
- [WimpleImpl.java](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/impl/WimpleImpl.java) — `getAccessToken`, `getDefaultSections`, `getAllAccounts`

토큰이 SharedPreferences에 남아있으면 webview 단계 생략 → 바로 초기화.

---

## 2. 거래 입력

```
User      TransactionInsertFragment        WimpleImpl                EntryManager           Whooing
 │  값 입력       │                            │                        │                     │
 │ ────────────▶│  (UI 검증)                  │                        │                     │
 │  전송 탭     │                              │                        │                     │
 │ ────────────▶│ wimple.makeEntry(...)──────▶│                        │                     │
 │              │                             │ em.makeEntry()────────▶│                     │
 │              │                             │                        │ Thread.start()       │
 │              │                             │                        │   PostEntryTaskThread│
 │              │                             │                        │                      │
 │              │                             │                        │ POST api/entries.json│
 │              │                             │                        │ ────────────────────▶│
 │              │                             │                        │ ◀──── 200/4xx ───────│
 │              │                             │                        │ EntryDBHandler.insert│
 │              │                             │                        │ wimpl.sm(CMD_ENTRY_  │
 │              │                             │                        │   INSERT, status, …) │
 │              │                             │                        │                      │
 │              │ handleMessage(CMD_ENTRY_INSERT) ◀── (Handler dispatch)│                     │
 │              │ Toast(success/fail)         │                        │                      │
 │              │ clearForm()                 │                        │                      │
```

핵심 파일:
- [TransactionInsertFragment.kt](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/TransactionInsertFragment.kt)
- [EntryManager.kt](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/impl/EntryManager.kt)

수정·삭제도 같은 패턴 (`PutEntryTaskThread`, `DeleteEntryTaskThread`).

---

## 3. 거래 목록 새로고침

```
User      TransactionListFragment        WimpleImpl              EntryManager     Whooing
 │  pull-to-refresh                          │                       │             │
 │ ────────▶│                                │                       │             │
 │          │ getAllEntries(latest, oldest)─▶│                       │             │
 │          │                                │ em.getAllEntries()──▶│              │
 │          │                                │                       │ Thread       │
 │          │                                │                       │ GET api/entries.json
 │          │                                │                       │   ─────────▶│
 │          │                                │                       │ ◀── rows ───│
 │          │                                │                       │ filter x0 (deleted)
 │          │                                │                       │ EntryDBHandler.insert
 │          │                                │                       │ sm(CMD_GET_ENTRIES, …)
 │          │ handleMessage(CMD_GET_ENTRIES) ◀──────────────         │              │
 │          │ adapter.notifyDataSetChanged() │                        │             │
```

스크롤 끝 도달 시 동일 흐름이지만 `oldestDate`를 더 과거로 잡아서 페이지네이션.

---

## 4. 은행 알림 자동 캡처 → 후잉 외부입력 전송

```
[Bank App]──post notification──▶[Android NotificationListener]
                                          │
                                          ▼
                          BankNotificationListener.onNotificationPosted(sbn)
                                          │
                                          │ ◇ 사용자가 선택한 패키지인지 확인
                                          │ ◇ FLAG_ONGOING_EVENT 제외
                                          │ ◇ U+2068/U+2069 bidi mark strip
                                          │ ◇ EXTRA_BIG_TEXT 우선
                                          │
                                          ▼
                          BankNotifications.add(pkg, label, title, text, time)
                            └─ 직전 entry와 동일하면 dedup
                            └─ stored JSONArray에 append
                            └─ count >= threshold ?
                                └─ Yes ─▶ forwardToWhooing()
                                          │
                                          ▼
                                  Thread.start() (sending lock)
                                          │
                                          ▼
                                  doSynchronousSend()
                                  ├─ pending ← stored 머지, stored 비움
                                  ├─ pending을 패키지별로 그룹핑
                                  ├─ for each group:
                                  │     payload = "bank title text MM/dd"...
                                  │     POST api/entries/outside.json
                                  │     code 2xx ─▶ pending에서 제거
                                  │     code 4xx ─▶ rejected store로 이동
                                  │     code 5xx/null ─▶ pending 유지 (재시도)
                                  └─ 토스트 알림
```

핵심 파일:
- [BankNotificationListener.kt](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/BankNotificationListener.kt)
- [BankNotifications.kt](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/impl/BankNotifications.kt)

앱 재진입 시 `WimpleActivity.onResume()`에서 `BankNotifications.retryIfPending(this)`
호출 → 직전에 실패해 pending에 남은 batch 자동 재시도.

각 그룹을 따로 보내는 이유는 후잉 서버가 한 payload를 한 은행 포맷으로만 파싱하기
때문 (개발자 포럼에서 흥반장이 권장).

---

## 5. 홈 화면 위젯 — 첫 추가 + 갱신

```
User       Launcher        MonthlySummaryWidgetProvider     SharedPrefs       Whooing
 │ 추가     │                       │                            │              │
 │────────▶│ onUpdate(ids)─────────▶│                            │              │
 │         │                        │ renderWidget(id)            │              │
 │         │                        │   readCache → null          │              │
 │         │                        │   updateAppWidget(loading)  │              │
 │         │                        │ triggerFetch(id)            │              │
 │         │                        │ Thread.start()              │              │
 │         │                        │   fetchMonth()              │              │
 │         │                        │     GET api/entries.json_array────────────▶│
 │         │                        │     ◀───── rows ────────────────────────── │
 │         │                        │     집계 (l_account/r_account의 what 분류) │
 │         │                        │   writeCache ──────────────▶│              │
 │         │                        │   renderWidget(id)          │              │
 │         │                        │     readCache → 데이터       │              │
 │         │                        │     updateAppWidget(footer + grid 인텐트) │
 │         │                        │   notifyAppWidgetViewDataChanged          │
 │         │  ◀────────────────────                                              │
 │         │ ─▶ MonthlySummaryWidgetService.onGetViewFactory                     │
 │         │     Factory.onDataSetChanged                                         │
 │         │       readCache → perDay map                                         │
 │         │       readCache → cellSizes                                          │
 │         │     for position 0..41:                                              │
 │         │       Factory.getViewAt(p) → RemoteViews(셀)                         │
```

월 이동 / 새로고침 / "오늘로" 버튼 모두 같은 패턴 (offset 변경 후 cache 확인 → fetch
또는 즉시 표시). [Architecture#캐시-우선-원리](Architecture.md) 참고.

핵심 파일:
- [MonthlySummaryWidgetProvider.kt](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/MonthlySummaryWidgetProvider.kt)
- [MonthlySummaryWidgetService.kt](../wimple/app/src/main/java/kr/blogspot/charlie0301/wimple/MonthlySummaryWidgetService.kt)

---

## 6. 위젯 리사이즈 → 텍스트 크기 재계산

```
User       Launcher                   Provider                  SharedPrefs
 │ 위젯 크기 변경                       │                              │
 │────────▶│ ACTION_APPWIDGET_OPTIONS_CHANGED                          │
 │         │ ──────────────────────────▶│                              │
 │         │                            │ onAppWidgetOptionsChanged(...)│
 │         │                            │ renderWidget(id)              │
 │         │                            │   computeAndStoreSizes        │
 │         │                            │     getAppWidgetOptions       │
 │         │                            │     min(widthBudget, heightBudget) ─▶ amountSp
 │         │                            │     daySp = amountSp × 1.4   │
 │         │                            │     write daySp / amountSp ──▶│
 │         │                            │   updateAppWidget(scaled chrome)
 │         │                            │ notifyAppWidgetViewDataChanged
 │         │                            │                               │
 │         │ Factory.onDataSetChanged   │                               │
 │         │   readCache (셀 데이터)     │                               │
 │         │   readCache (셀 사이즈) ◀───────────────────────────────────│
 │         │ Factory.getViewAt with new sizes                           │
```

크기 계산은 가로(셀 폭 ÷ 5문자) ∩ 세로(행 높이 ÷ 4.4) 양쪽 만족하는 sp.
핵심: `MonthlySummaryWidgetProvider.computeAndStoreSizes()`.
