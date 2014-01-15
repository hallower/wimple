package me.blog.imhallower.wimple.model;

import java.text.ParseException;
import java.util.Date;

import me.blog.imhallower.wimple.impl.util.Utils;

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
		
		Long dateLong = 0L;
		
		String dateString = entry.get("entry_date").toString();
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

		sb.append("\n-[Section : " + id + " ]------------------------------");
		sb.append("\n   date = " + date);
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

	

}
