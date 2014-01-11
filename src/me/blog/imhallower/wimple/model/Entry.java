package me.blog.imhallower.wimple.model;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.json.simple.JSONObject;

public class Entry {	

	private final String id;	
	private Long date;
	private final String leftAccount;
	private final String leftAccountID;
	private final String rightAccount;
	private final String rightAccountID;
	private final String item;
	private final Float amount;
	private Float balance = 0f;
	private final String memo;
	private final String appID;



	public Entry(String id, String leftAccount,
			String leftAccountID, String rightAccount, String rightAccountID,
			String item, Float amount, String memo, String appID) {
		super();
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
				entry.get("item").toString(), Float.valueOf(entry.get("money").toString()), entry.get("memo").toString(), entry.get("app_id").toString());
		
		SimpleDateFormat df = new SimpleDateFormat("yyyyMMdd");
		Long dateLong = 0L;
		
		String dateString = entry.get("entry_date").toString();
		int pos = dateString.indexOf(".");
		if(pos > 0){
			dateString = dateString.substring(0, pos - 1);
		}
		
		try {
			Date date = df.parse(dateString);
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


	public Float getAmount() {
		return amount;
	}


	public Float getBalance() {
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
		this.balance = Float.valueOf(balance);
	}

	public void setBalance(Float balance) {
		this.balance = balance;
	}


	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();

		sb.append("-[Section : " + id + " ]------------------------------");
		sb.append("   date = " + date);
		sb.append("   leftAccount = " + leftAccount);
		sb.append("   leftAccountID = " + leftAccountID);
		sb.append("   rightAccount = " + rightAccount);
		sb.append("   rightAccountID = " + rightAccountID);
		sb.append("   amount = " + amount);
		sb.append("   balance = " + balance);
		sb.append("   memo = " + memo);
		sb.append("   appID = " + appID);
		sb.append("---------------------------------------------------------------------");

		return sb.toString();
	}


}
