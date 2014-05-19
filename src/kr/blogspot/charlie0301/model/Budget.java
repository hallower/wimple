package kr.blogspot.charlie0301.model;

import kr.blogspot.charlie0301.impl.db.IDatabaseRecord;

import org.json.simple.JSONObject;

import android.util.Log;
import android.util.SparseArray;

public class Budget implements IDatabaseRecord {	

	private final static String LOG_TAG = "Budget";
	
	public final static String SUMMARYACCOUNTID = "summary";

	private String accountID;
	private Double budget;
	private Double current;
	private Double remains;
	private String type;	// income, expense
	
	public static final SparseArray<String> columns = new SparseArray<String>();    

	static {
		columns.append(0, "accountid");
		columns.append(1, "budget");
		columns.append(2, "current");
		columns.append(3, "remains");
		columns.append(4, "type");
	}

	// This must be used only for DB result inserting.
	public Budget(){
		super();

		accountID = "";
		budget = 0.0;
		current = 0.0;
		remains = 0.0;
		type = "";
	}

	public Budget(String accountID, Double budget, Double current, Double remains, boolean isIncome) {
		super();
		this.accountID = accountID;
		this.budget = budget;
		this.current = current;
		this.remains = remains;
		this.type = isIncome?"income":"expense";
	}

	public Budget(JSONObject item, boolean isIncome) {

		this(item.get("account_id").toString(), 
				Double.valueOf(item.get("budget").toString()),
				Double.valueOf(item.get("money").toString()),
				Double.valueOf(item.get("remains").toString()),
				isIncome
				);
	}


	
	public String getAccountID() {
		return accountID;
	}

	public void setAccountID(String accountID) {
		this.accountID = accountID;
	}

	public Double getBudget() {
		return budget;
	}

	public void setBudget(Double budget) {
		this.budget = budget;
	}

	public Double getCurrent() {
		return current;
	}

	public void setCurrent(Double current) {
		this.current = current;
	}

	public Double getRemains() {
		return remains;
	}

	public void setRemains(Double remains) {
		this.remains = remains;
	}

	public String getType() {
		return type;
	}

	public boolean isIncome() {
		return (type.compareTo("income") == 0);
	}
	public void setType(String type) {
		this.type = type;
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
		int key = 0;
		String value = "";

		for(int i = 0; i < values.size() ; i++){
			key = values.keyAt(i);
			value = values.get(key);

			switch(key){
			case 0 :
				this.accountID = value;
				break;
			case 1 :
				this.budget = Double.valueOf(value);
				break;
			case 2 :
				this.current = Double.valueOf(value);
				break;
			case 3 :
				this.remains = Double.valueOf(value);
				break;
			case 4 :
				this.type = value;
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
			return budget.toString();
		case 2 :
			return current.toString();
		case 3 :
			return remains.toString();
		case 4 :
			return type;
			
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
		values.append(1, budget.toString());
		values.append(2, current.toString());
		values.append(3, remains.toString());
		values.append(4, type);
		return values;
	}

	
	@Override
	public boolean equals(Object o) {
		if(false == (o instanceof Budget)){
			return false;
		}

		Budget item = (Budget)o;
		return accountID.equals(item.accountID);
	}

}
