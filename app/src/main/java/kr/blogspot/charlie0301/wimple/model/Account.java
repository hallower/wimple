package kr.blogspot.charlie0301.wimple.model;

import android.util.Log;
import android.util.SparseArray;

import org.json.JSONObject;

import java.util.Comparator;

import kr.blogspot.charlie0301.wimple.impl.db.IDatabaseRecord;

public class Account implements IDatabaseRecord {

	private final static String LOG_TAG = "Account";

	private String what;
	private String id;	
	private String type;
	private String title;
	private String description;
	private String openedDate;
	private String closedDate;
	private String category;

	private Boolean useDate;
	private Boolean payDate;
	private Boolean payAccount;
	private Integer seq;
	
	public static final SparseArray<String> columns = new SparseArray<>();

	static {
		columns.append(0, "what");
		columns.append(1, "id");
		columns.append(2, "type");
		columns.append(3, "title");
		columns.append(4, "description");
		columns.append(5, "openedDate");
		columns.append(6, "closedDate");
		columns.append(7, "category");
		columns.append(8, "useDate");
		columns.append(9, "payDate");
		columns.append(10, "payAccount");
		columns.append(11, "seq");
	}

	// This must be used only for DB result inserting.
	public Account(){
		super();
		
		what = "";
		id = "";	
		type = "";
		title = "";
		description = "";
		openedDate = "";
		closedDate = "";
		category = "";

		useDate = false;
		payDate = false;
		payAccount = false;
		seq = 999;
	}

	public Account(String what, String id, String type, String title,
			String description, String openedDate, String closedDate,
			String category) {
		this();
		
		this.what = what;
		this.id = id;
		this.type = type;
		this.title = title;
		this.description = description;
		this.openedDate = openedDate;
		this.closedDate = closedDate;
		this.category = category;
		
		//this.seq = Integer.valueOf(id.substring(1, id.length()));
	}



	public Account(String what, JSONObject account) {

		this(what, account.optString("account_id"), account.optString("type"),
				account.optString("title"), account.optString("memo"), account.optString("open_date"),
				account.optString("close_date"), account.optString("category"));
	}



	public String getWhat() {
		return what;
	}

	public String getId() {
		return id;
	}

	public String getType() {
		return type;
	}

	public String getTitle() {
		return title;
	}

	public String getDescription() {
		return description;
	}

	public String getOpenedDate() {
		return openedDate;
	}

	public String getClosedDate() {
		return closedDate;
	}

	public String getCategory() {
		return category;
	}

	public Boolean getUseDate() {
		return useDate;
	}

	public Boolean getPayDate() {
		return payDate;
	}

	public Boolean getPayAccount() {
		return payAccount;
	}

	public void setUseDate(Boolean useDate) {
		this.useDate = useDate;
	}

	public void setPayDate(Boolean payDate) {
		this.payDate = payDate;
	}

	public void setPayAccount(Boolean payAccount) {
		this.payAccount = payAccount;
	}

	public void setSeq(int seq){
		this.seq = seq;
	}
	
	public Integer getSeq(){
		return this.seq;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();

		sb.append("\n-[Account : ").append(what).append(" - ").append(id).append(" (").append(type).append(") ]------------------------------");
		sb.append("\n   type = ").append(type);
		sb.append("\n   title = ").append(title);
		sb.append("\n   description = ").append(description);
		sb.append("\n   openedDate = ").append(openedDate);
		sb.append("\n   closedDate = ").append(closedDate);
		sb.append("\n   category = ").append(category);
		sb.append("\n   seq = ").append(seq);
		sb.append("\n---------------------------------------------------------------------");

		return sb.toString();
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

		int key;
		String value;
		
		for(int i = 0; i < values.size() ; i++){
			key = values.keyAt(i);
			value = values.get(key);
		
			switch(key){
			case 0 :
				this.what = value;
				break;
			case 1 :
				this.id = value;
				break;
			case 2 :
				this.type = value;
				break;
			case 3 :
				this.title = value;
				break;
			case 4 :
				this.description = value;
				break;
			case 5 :
				this.openedDate = value;
				break;
			case 6 :
				this.closedDate = value;
				break;
			case 7 :
				this.category = value;
				break;
			case 8 :
				this.useDate = Boolean.parseBoolean(value);
				break;
			case 9 :
				this.payDate = Boolean.parseBoolean(value);
				break;
			case 10 :
				this.payAccount = Boolean.parseBoolean(value);
				break;
			case 11 :
				this.seq = Integer.valueOf(value);
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
			return what;
		case 1 :
			return id;
		case 2 :
			return type;
		case 3 :
			return title;
		case 4 :
			return description;
		case 5 :
			return openedDate;
		case 6 :
			return closedDate;
		case 7 :
			return category;
		case 8 :
			return useDate.toString();
		case 9 :
			return payDate.toString();
		case 10 :
			return payAccount.toString();
		case 11 :
			return seq.toString();
		default :
			Log.e(LOG_TAG, "Invalid columnID!!!");
			break;
		}
		return "";
	}


	@Override
	public SparseArray<String> getValues() {
		SparseArray<String> values = new SparseArray<>();

		values.append(0, what);
		values.append(1, id);
		values.append(2, type);
		values.append(3, title);
		values.append(4, description);
		values.append(5, openedDate);
		values.append(6, closedDate);
		values.append(7, category);
		values.append(8, useDate.toString());
		values.append(9, payDate.toString());
		values.append(10, payAccount.toString());
		values.append(11, seq.toString());

		return values;
	}

	@Override
	public boolean equals(Object o) {
		if(!(o instanceof Account)){
			return false;
		}

		Account item = (Account)o;
		return (0 == id.compareTo(item.id));
	}

	public static class DateDescCompare implements Comparator<Account>{

		@Override
		public int compare(Account lhs, Account rhs) {
			return -1 * lhs.getSeq().compareTo(rhs.getSeq());
		}

	}

	public static class DateAscCompare implements Comparator<Account>{

		@Override
		public int compare(Account lhs, Account rhs) {
			return lhs.getSeq().compareTo(rhs.getSeq());
		}

	}
}
