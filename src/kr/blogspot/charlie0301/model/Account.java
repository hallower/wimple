package kr.blogspot.charlie0301.model;

import kr.blogspot.charlie0301.impl.db.IDatabaseRecord;

import org.json.simple.JSONObject;

import android.util.Log;
import android.util.SparseArray;

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

	public static final SparseArray<String> columns = new SparseArray<String>();    

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
	}



	public Account(String what, JSONObject account) {

		this(what, account.get("account_id").toString(), account.get("type").toString(), 
				account.get("title").toString(), account.get("memo").toString(), account.get("open_date").toString(), 
				account.get("close_date").toString(), account.get("category").toString());
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



	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();

		sb.append("\n-[Account : " + what + " - " + id + " ]------------------------------");
		sb.append("\n   type = " + type);
		sb.append("\n   title = " + title);
		sb.append("\n   description = " + description);
		sb.append("\n   openedDate = " + openedDate);
		sb.append("\n   closedDate = " + closedDate);
		sb.append("\n   category = " + category);
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

		int key = 0;
		String value = "";
		
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
		default :
			Log.e(LOG_TAG, "Invalid columnID!!!");
			break;
		}
		return "";
	}


	@Override
	public SparseArray<String> getValues() {
		SparseArray<String> values = new SparseArray<String>();

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

		return values;
	}

}
