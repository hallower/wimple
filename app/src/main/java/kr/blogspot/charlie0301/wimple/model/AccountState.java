package kr.blogspot.charlie0301.wimple.model;

import java.util.Comparator;

import kr.blogspot.charlie0301.wimple.impl.db.IDatabaseRecord;

import org.json.JSONObject;

import android.util.Log;
import android.util.SparseArray;

public class AccountState implements IDatabaseRecord {	

	private final static String LOG_TAG = "AccountState";

	private String accountID;
	private String accountName;
	private String category;
	private Double amount;
	private Integer seq;
	private Boolean group;
	
	public static final SparseArray<String> columns = new SparseArray<>();

	static {
		columns.append(0, "accountid");
		columns.append(1, "accountname");
		columns.append(2, "category");
		columns.append(3, "amount");
		columns.append(4, "seq");
		columns.append(5, "group_");
	}

	// This must be used only for DB result inserting.
	public AccountState(){
		super();

		accountID = "";
		accountName = "";
		category = "";
		amount = 0.0;
		seq = 9999;
		group = false;
	}

	public AccountState(String accountID, String accountName,
			String category, Double amount) {
		super();
		this.accountID = accountID;
		this.accountName = accountName;
		this.category = category;
		this.amount = amount;
		//this.seq = Integer.valueOf(accountID.substring(1, accountID.length()));
	}

	public AccountState(JSONObject item, String category) {
		this(item.optString("account_id"), "name",
				category, Double.valueOf(item.optString("money")));
	}

	public String getAccountID() {
		return accountID;
	}

	public void setAccountID(String accountID) {
		this.accountID = accountID;
	}

	public String getAccountName() {
		return accountName;
	}

	public void setAccountName(String accountName) {
		this.accountName = accountName;
	}

	public String getCategory() {
		return category;
	}

	public void setCategory(String category) {
		this.category = category;
	}

	public Double getAmount() {
		return amount;
	}

	public void setAmount(Double amount) {
		this.amount = amount;
	}

	public void setSeq(int seq){
		this.seq = seq;
	}
	
	public Integer getSeq() {
		return seq;
	}

	public void setGroup(boolean group){
		this.group = group;
	}
	
	public Boolean getGroup() {
		return group;
	}
	
	@Override
	public String toString() {
		/*
		StringBuilder sb = new StringBuilder();

		sb.append("\n-[Item : " + id + " ]------------------------------");
		sb.append("\n   date = " + date);
		sb.append("\n   leftAccount = " + leftAccount);
		sb.append("\n   leftAccountID = " + leftAccountID);
		sb.append("\n   rightAccount = " + rightAccount);
		sb.append("\n   rightAccountID = " + rightAccountID);
		sb.append("\n   amount = " + amount);
		sb.append("\n---------------------------------------------------------------------");

		return sb.toString();
		 */
		return getAccountID();
	}

	@Override
	public String getKeyValue() {
		return this.accountID;
	}

	@Override
	public SparseArray<String> getColumns() {
		return columns;
	}

	@Override
	public boolean setValues(SparseArray<String> values) {
		int key;
		String value;

		for(int i = 0; i < values.size() ; i++){
			key = values.keyAt(i);
			value = values.get(key);

			switch(key){
			case 0 :
				this.accountID = value;
				break;
			case 1 :
				this.accountName = value;
				break;
			case 2 :
				this.category = value;
				break;
			case 3 :
				this.amount = Double.valueOf(value);
				break;
			case 4 :
				this.seq = Integer.valueOf(value);
				break;
			case 5 :
				this.group = Boolean.valueOf(value);
				break;
				
			default :
				Log.e(LOG_TAG, "Invalid columnID!!!");
				break;
			}			
		}		

		return true;
	}

	@Override
	public String getValue(int columnID) {
		switch(columnID){
		case 0 :
			return accountID;
		case 1 :
			return accountName;
		case 2 :
			return category;
		case 3 :
			return amount.toString();
		case 4 :
			return seq.toString();
		case 5 :
			return group.toString();
			
		default :
			Log.e(LOG_TAG, "Invalid columnID!!!");
			break;
		}			
		return "";
	}

	@Override
	public SparseArray<String> getValues() {
		SparseArray<String> values = new SparseArray<String>();

		values.append(0, accountID);
		values.append(1, accountName);
		values.append(2, category);
		values.append(3, amount.toString());
		values.append(4, seq.toString());
		values.append(5, group.toString());
		return values;
	}

	
	@Override
	public boolean equals(Object o) {
		if(!(o instanceof AccountState)){
			return false;
		}

		AccountState item = (AccountState)o;
		return (0 == accountID.compareTo(item.accountID));
	}

	public static class DateDescCompare implements Comparator<AccountState>{

		@Override
		public int compare(AccountState lhs, AccountState rhs) {
			return -1 * lhs.getSeq().compareTo(rhs.getSeq());
		}

	}

	public static class DateAscCompare implements Comparator<AccountState>{

		@Override
		public int compare(AccountState lhs, AccountState rhs) {
			
			int category = lhs.getCategory().compareTo(rhs.getCategory());
			if(0 != category){
				return category;
			}
			return lhs.getSeq().compareTo(rhs.getSeq());
		}

	}
	

}
