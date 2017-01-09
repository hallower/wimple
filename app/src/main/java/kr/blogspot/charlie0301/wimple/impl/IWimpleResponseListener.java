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
	public void onGetAuthTempToken(boolean status, String tempToken);
	
	public void onGetAuthAccessToken(boolean status, Map<String, String> result);
	
	/*
	 * R, User
	 */
	public void onGetUserInfoResponseReceived(boolean status, UserInfo info);
	
	public void onGetAllSectionResponseReceived(boolean status, Collection<Section> list);
	
	public void onGetAllAccountResponseReceived(boolean status, Collection<Account> list);
	
	/*
	 * R, Entry
	 */
	public void onGetEntriesResponseReceived(boolean status, Collection<Entry> list);
	
	public void onGetLatestEntriesResponseReceived(boolean status, Collection<Entry> list);
	
	
	/*
	 * C, U, D, Entry
	 */
	public void onMakeEntryResponseReceived(boolean status, String entryDate);
	
	public void onModifyEntryResponseReceived(boolean status, Entry entry);

	public void onRemoveEntryResponseReceived(boolean status, String id);
	
	public void onPostPaymentsResponseReceived(boolean status);
	
	/*
	 * R, D, Items
	 */
	public void onGetFrequentItemsResponseReceived(boolean status, Collection<Item> list);
	
	public void onGetLatestItemsResponseReceived(boolean status, Collection<Item> list);
	
	public void onGetMonthlyItemsResponseReceived(boolean status, ArrayList<Item> list);
	
	public void onRemoveMonthlyItemResponseReceived(boolean status, String id);
	
	/*
	 * R, Financial State
	 */
	public void onGetFinancialStateResponseReceived(boolean status, Collection<AccountState> list);
	
	/*
	 * R, Income and Expense
	 */
	public void onGetIncomeAndExpenseResponseReceived(boolean status, Collection<AccountState> list);
	
	public void onGetBudgetResponseReceived(boolean status, boolean isIncome, Map<String, Budget> list);
	
	/*
	 * P MoneyNews
	 */
	public void onPostNewsResponseReceived(boolean status, String id);
}
