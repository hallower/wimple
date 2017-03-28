package kr.blogspot.charlie0301.wimple.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import kr.blogspot.charlie0301.wimple.model.Account;
import kr.blogspot.charlie0301.wimple.model.AccountState;
import kr.blogspot.charlie0301.wimple.model.Budget;
import kr.blogspot.charlie0301.wimple.model.Entry;
import kr.blogspot.charlie0301.wimple.model.Item;
import kr.blogspot.charlie0301.wimple.model.Section;
import kr.blogspot.charlie0301.wimple.model.UserInfo;


public interface IWimpleResponseListener {
	
	/*
	 * Service Control
	 */
	void onGetAuthTempToken(boolean status, String tempToken);
	
	void onGetAuthAccessToken(boolean status, Map<String, String> result);
	
	/*
	 * R, User
	 */
	void onGetUserInfoResponseReceived(boolean status, UserInfo info);
	
	void onGetAllSectionResponseReceived(boolean status, Collection<Section> list);
	
	void onGetAllAccountResponseReceived(boolean status, Collection<Account> list);
	
	/*
	 * R, Entry
	 */
	void onGetEntriesResponseReceived(boolean status, Collection<Entry> list);
	
	void onGetLatestEntriesResponseReceived(boolean status, Collection<Entry> list);
	
	
	/*
	 * C, U, D, Entry
	 */
	void onMakeEntryResponseReceived(boolean status, String entryDate);
	
	void onModifyEntryResponseReceived(boolean status, Entry entry);

	void onRemoveEntryResponseReceived(boolean status, String id);
	
	void onPostPaymentsResponseReceived(boolean status);
	
	/*
	 * R, D, Items
	 */
	void onGetFrequentItemsResponseReceived(boolean status, Collection<Item> list);
	
	void onGetLatestItemsResponseReceived(boolean status, Collection<Item> list);
	
	void onGetMonthlyItemsResponseReceived(boolean status, ArrayList<Item> list);
	
	void onRemoveMonthlyItemResponseReceived(boolean status, String id);
	
	/*
	 * R, Financial State
	 */
	void onGetFinancialStateResponseReceived(boolean status, Collection<AccountState> list);
	
	/*
	 * R, Income and Expense
	 */
	void onGetIncomeAndExpenseResponseReceived(boolean status, Collection<AccountState> list);
	
	void onGetBudgetResponseReceived(boolean status, boolean isIncome, Map<String, Budget> list);
	
	/*
	 * P MoneyNews
	 */
	void onPostNewsResponseReceived(boolean status, String id);
}
