package kr.blogspot.charlie0301.model;

import java.text.ParseException;
import java.util.Comparator;
import java.util.Date;

import kr.blogspot.charlie0301.impl.db.IDatabaseRecord;
import kr.blogspot.charlie0301.impl.util.Utils;

import org.json.simple.JSONObject;

import android.util.Log;
import android.util.SparseArray;

public class Entry implements IDatabaseRecord {	

	private final static String LOG_TAG = "Section";

	private String id;	
	private Long date;		// optional	
	private String leftAccount;
	private String leftAccountID;
	private String rightAccount;
	private String rightAccountID;
	private String item;
	private Double amount;
	private Double  balance = 0.0;		// optional
	private String memo;
	private String appID;
	private Double dateValue;

	public static final SparseArray<String> columns = new SparseArray<String>();    

	static {
		columns.append(0, "id");
		columns.append(1, "date");
		columns.append(2, "leftAccount");
		columns.append(3, "leftAccountID");
		columns.append(4, "rightAccount");
		columns.append(5, "rightAccountID");
		columns.append(6, "item");
		columns.append(7, "amount");
		columns.append(8, "balance");
		columns.append(9, "memo");
		columns.append(10, "appID");
		columns.append(11, "dateValue");
	}

	// Only for the database item inserting.
	public Entry(){
		super();

		id = "";
		date = 0L;		// optional
		leftAccount = "";
		leftAccountID = "";
		rightAccount = "";
		rightAccountID = "";
		item = "";
		amount = 0.0;
		balance = 0.0;		// optional
		memo = "";
		appID = "";
		dateValue = 0.0;
	}

	public Entry(String id, String leftAccount,
			String leftAccountID, String rightAccount, String rightAccountID,
			String item, Double amount, String memo, String appID) {
		this();

		this.id = id;
		this.leftAccount = leftAccount;
		this.leftAccountID = leftAccountID;
		this.rightAccount = rightAccount;
		this.rightAccountID = rightAccountID;
		this.item = item;
		this.amount = amount;
		this.memo = memo;
		this.appID = appID;
	}


	public Entry(JSONObject entry) {

		this(entry.get("entry_id").toString(), entry.get("l_account").toString(), 
				entry.get("l_account_id").toString(), entry.get("r_account").toString(), entry.get("r_account_id").toString(), 
				entry.get("item").toString(), Double.valueOf(entry.get("money").toString()), entry.get("memo").toString(), entry.get("app_id").toString());

		Long dateLong = 0L;

		String dateString = entry.get("entry_date").toString();
		this.dateValue = Double.parseDouble(dateString);
		int pos = dateString.indexOf(".");
		if(pos > 0){
			dateString = dateString.substring(0, pos - 1);
		}

		try {
			Date date = Utils.getServerDateFormat().parse(dateString);
			dateLong = date.getTime();
		} catch (ParseException e) {
			e.printStackTrace();
		}
		setDate(dateLong);
	}


	public String getId() {
		return id;
	}


	public Long getDate() {
		return date;
	}


	public String getLeftAccount() {
		return leftAccount;
	}


	public String getLeftAccountID() {
		return leftAccountID;
	}


	public String getRightAccount() {
		return rightAccount;
	}


	public String getRightAccountID() {
		return rightAccountID;
	}


	public String getItem() {
		return item;
	}


	public Double getAmount() {
		return amount;
	}


	public Double getBalance() {
		return balance;
	}


	public String getMemo() {
		return memo;
	}


	public String getAppID() {
		return appID;
	}

	public void setDate(Long date){
		this.date = date;
	}

	public void setBalance(String balance) {
		this.balance = Double.valueOf(balance);
	}

	public void setBalance(Double balance) {
		this.balance = balance;
	}

	public Double getDateValue(){
		return this.dateValue;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();

		sb.append("\n-[Section : " + id + " ]------------------------------");
		sb.append("\n   date = " + date);
		sb.append("\n   dateValue = " + dateValue);
		sb.append("\n   leftAccount = " + leftAccount);
		sb.append("\n   leftAccountID = " + leftAccountID);
		sb.append("\n   rightAccount = " + rightAccount);
		sb.append("\n   rightAccountID = " + rightAccountID);
		sb.append("\n   amount = " + amount);
		sb.append("\n   balance = " + balance);
		sb.append("\n   memo = " + memo);
		sb.append("\n   appID = " + appID);
		sb.append("\n---------------------------------------------------------------------");

		return sb.toString();
	}


	@Override
	public boolean equals(Object o) {

		if(false == (o instanceof Entry)){
			return false;
		}

		return this.id.equals(((Entry)o).id);
	}
	
	@Override
	public String getKeyValue() {
		return this.id;
	}

	@Override
	public SparseArray<String> getColumns() {
		return columns;
	}

	@Override
	public boolean setValues(SparseArray<String> values) {
		int key = 0;
		String value = "";

		for(int i = 0; i < values.size() ; i++){
			key = values.keyAt(i);
			value = values.get(key);

			switch(key){
			case 0 :
				this.id = value;
				break;
			case 1 :
				this.date = Long.parseLong(value);
				break;
			case 2 :
				this.leftAccount = value;
				break;
			case 3 :
				this.leftAccountID = value;
				break;
			case 4 :
				this.rightAccount  = value;
				break;
			case 5 :
				this.rightAccountID = value;
				break;
			case 6 :
				this.item = value;
				break;
			case 7 :
				this.amount = Double.parseDouble(value);
				break;
			case 8 :
				this.balance = Double.parseDouble(value);
				break;
			case 9 :
				this.memo = value;
				break;
			case 10 :
				this.appID = value;
				break;
			case 11 :
				this.dateValue = Double.parseDouble(value);
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
			return id;
		case 1 :
			return date.toString();
		case 2 :
			return leftAccount;
		case 3 :
			return leftAccountID;
		case 4 :
			return rightAccount;
		case 5 :
			return rightAccountID;
		case 6 :
			return item;
		case 7 :
			return amount.toString();
		case 8 :
			return balance.toString();
		case 9 :
			return memo;
		case 10 :
			return appID;
		case 11 :
			return dateValue.toString();
		default :
			Log.e(LOG_TAG, "Invalid columnID!!!");
			break;
		}
		return "";
	}

	@Override
	public SparseArray<String> getValues() {
		SparseArray<String> values = new SparseArray<String>();

		values.append(0, id);
		values.append(1, date.toString());
		values.append(2, leftAccount);
		values.append(3, leftAccountID);
		values.append(4, rightAccount);
		values.append(5, rightAccountID);
		values.append(6, item);
		values.append(7, amount.toString());
		values.append(8, balance.toString());
		values.append(9, memo);
		values.append(10, appID);
		values.append(11, dateValue.toString());

		return values;
	}



	public static class DateDescCompare implements Comparator<Entry>{

		@Override
		public int compare(Entry lhs, Entry rhs) {
			return -1 * lhs.getDateValue().compareTo(rhs.getDateValue());
		}
		
	}
	
}
