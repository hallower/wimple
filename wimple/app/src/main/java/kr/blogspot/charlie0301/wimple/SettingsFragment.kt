package kr.blogspot.charlie0301.wimple

/**
 * Constants holder for preference keys consumed across the app. The actual settings UI
 * now lives in [SettingsHostFragment] (two-pane / multi-screen) plus the per-category
 * sub-fragments (`GeneralSettingsFragment`, `BankNotificationSettingsFragment`, …).
 *
 * Kept under the original name so existing call sites referencing `SettingsFragment.KEY_*`
 * continue to compile without churn — the `object` form preserves dotted access while
 * making the type un-instantiable.
 */
object SettingsFragment {
    const val KEY_MONTHLY_ITEM_COUNT = "pref_monthlyItemCount"
    const val KEY_MONTHLY_ITEM_DISPLAY = "pref_monthlyItemDisplay"
    const val KEY_FINANCIAL_STATE_SHOW_GROUP = "pref_financialStateShowGroup"
    const val KEY_DISABLE_MEMO = "pref_disableMemo"
    const val KEY_INCOME_EXPENSE_ENABLE_BUDGET = "pref_incomeExpenseStateEnableBudget"
    const val KEY_INCOME_EXPENSE_SHOW_GROUP = "pref_incomeExpenseStateShowGroup"
    const val KEY_FLOATING_BUTTON = "preference_floating_button"
    const val KEY_BIOMETRIC_OPTION = "pref_enableBiometricSignIn"
}
