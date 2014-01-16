package me.blog.imhallower.wimple.model;

import me.blog.imhallower.wimple.impl.db.IDatabaseRecord;

import org.json.simple.JSONObject;

import android.util.Log;
import android.util.SparseArray;

public class Item implements IDatabaseRecord {	

	private final static String LOG_TAG = "Item";
	
	private String id;		// optional
	private String date;	// optional
	private String leftAccount;
	private String leftAccountID;
	private String rightAccount;
	private String rightAccountID;
	private String item;
	private Double amount;

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
	}

	// This must be used only for DB result inserting.
	public Item(){
		super();
		
		id = "";
		date = "";
		leftAccount = "";
		leftAccountID = "";
		rightAccount = "";
		rightAccountID = "";
		item = "";
		amount = 0.0;		
	}
	
	public Item(String leftAccount,
			String leftAccountID, String rightAccount, String rightAccountID,
			String item, Double amount) {
		super();
		this.id = "";
		this.date = "";
		this.leftAccount = leftAccount;
		this.leftAccountID = leftAccountID;
		this.rightAccount = rightAccount;
		this.rightAccountID = rightAccountID;
		this.item = item;
		this.amount = amount;
	}


	public Item(JSONObject entry) {

		this(entry.get("l_account").toString(), entry.get("l_account_id").toString(), 
				entry.get("r_account").toString(), entry.get("r_account_id").toString(), 
				entry.get("item").toString(), Double.valueOf(entry.get("money").toString()));
		
		try{
			this.id = entry.get("entry_id").toString();
		}catch(Exception e){
			this.id = "";
		}
		
		try{
			this.date = entry.get("entry_date").toString();
		}catch(Exception e){
			this.date = "";
		}
	}

	public String getId() {
		return id;
	}


	public String getDate() {
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
		return getItem();
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
				this.date = value;
				break;
			case 2 :
				this.leftAccount = value;
				break;
			case 3 :
				this.leftAccountID = value;
				break;
			case 4 :
				this.rightAccount = value;
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
			return date;
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
		values.append(1, date);
		values.append(2, leftAccount);
		values.append(3, leftAccountID);
		values.append(4, rightAccount);
		values.append(5, rightAccountID);
		values.append(6, item);
		values.append(7, amount.toString());

		return values;
	}

/*
	@Override
	public boolean equals(Object o) {
		if(false == (o instanceof Item)){
			return false;
		}
		
		Item item = (Item)o;
		return id.equals(item.id);
	}

	int compareTo(Object o){
		if(false == (o instanceof Item)){
			return -1;
		}
		
		Item item = (Item)o;
		return date.compareTo(item.date);
	}
*/
}
