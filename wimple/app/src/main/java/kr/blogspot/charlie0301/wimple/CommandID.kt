package kr.blogspot.charlie0301.wimple

/**
 * UI-layer Handler message codes routed through [WimpleActivity]'s mainHandler.
 *
 * Distinct from `WimpleImpl.CommandID` (impl layer) — that one carries internal
 * REST/auth events inside `WimpleImpl` and is intentionally kept separate.
 */
object CommandID {
    private const val CMD_BASE = 10000

    const val EXIT = CMD_BASE + 1
    const val TOAST_LONG = CMD_BASE + 3
    const val TOAST_SHORT = CMD_BASE + 5
    const val FATAL_ERROR = CMD_BASE + 6
    const val GET_PIN = CMD_BASE + 7
    const val SHOW_STATUS = CMD_BASE + 8
    const val UPDATE_USER_INFO = CMD_BASE + 9
    const val GET_ALL_ACCOUNT_RECEIVED = CMD_BASE + 11
    const val WIMPLE_LOGGIN_SUCCESS = CMD_BASE + 13
    const val WIMPLE_LOGGIN_FAILED = CMD_BASE + 15
    const val WIMPLE_LOGGOUT = CMD_BASE + 17
    const val GET_ALL_SECTION_RECEIVED = CMD_BASE + 19
    const val GET_MAKE_ENTRY_RESPONSE_RECEIVED = CMD_BASE + 21
    const val GET_FREQUENT_ITEMS_RESPONSE_RECEIVED = CMD_BASE + 23
    const val GET_LATEST_ENTRY_RESPONSE_RECEIVED = CMD_BASE + 25
    const val GET_LATEST_ITEMS_RESPONSE_RECEIVED = CMD_BASE + 27
    const val GET_ENTRIES_RECEIVED = CMD_BASE + 29
    const val MODIFY_ENTRY = CMD_BASE + 31
    const val ADD_MONTHLY_ITEM = CMD_BASE + 32
    const val GET_MODIFY_ENTRY_RESPONSE_RECEIVED = CMD_BASE + 33
    const val GET_MONTHLY_ITEMS_RESPONSE_RECEIVED = CMD_BASE + 35
    const val WIMPLE_PROFILE_PICTURE_UPDATED = CMD_BASE + 37
    const val REMOVE_ENTRY_RESPONSE_RECEIVED = CMD_BASE + 39
    const val REMOVE_MONTHLY_ITEMS_RESPONSE_RECEIVED = CMD_BASE + 41
    const val GET_FINANCIAL_STATE_RESPONSE_RECEIVED = CMD_BASE + 43
    const val GET_INCOME_AND_EXPENSE_RESPONSE_RECEIVED = CMD_BASE + 45
    const val GET_BUDGET_RESPONSE_RECEIVED = CMD_BASE + 47
    const val POST_PAYMENT_RESPONSE_RECEIVED = CMD_BASE + 49
}
