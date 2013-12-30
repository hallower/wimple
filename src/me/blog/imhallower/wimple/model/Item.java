package me.blog.imhallower.wimple.model;

import org.json.simple.JSONObject;

public class Item {	

	private final String id;	
	private String date;
	private final String leftAccount;
	private final String leftAccountID;
	private final String rightAccount;
	private final String rightAccountID;
	private final String item;
	private final Double amount;


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

		sb.append("-[Item : " + id + " ]------------------------------");
		sb.append("   date = " + date);
		sb.append("   leftAccount = " + leftAccount);
		sb.append("   leftAccountID = " + leftAccountID);
		sb.append("   rightAccount = " + rightAccount);
		sb.append("   rightAccountID = " + rightAccountID);
		sb.append("   amount = " + amount);
		sb.append("---------------------------------------------------------------------");

		return sb.toString();
		*/
		return getItem();
	}


}
