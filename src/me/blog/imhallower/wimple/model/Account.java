package me.blog.imhallower.wimple.model;

import org.json.simple.JSONObject;

public class Account {

	private final String what;
	private final String id;	
	private final String type;
	private final String title;
	private final String description;
	private final String openedDate;
	private final String closedDate;
	private final String category;

	private Boolean useDate;
	private Boolean payDate;
	private Boolean payAccount;

	public Account(String what, String id, String type, String title,
			String description, String openedDate, String closedDate,
			String category) {
		super();
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

	
}
