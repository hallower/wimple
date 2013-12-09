package me.blog.imhallower.wimple.impl.db;


public class BadgeDBHandler {

	/*   
	 * columns.put(0, "badge_id");        
	 * columns.put(1, "title");
	 * columns.put(2, "category");
	 * columns.put(3, "description");
	 * columns.put(4, "url");
	 * columns.put(5, "unlockedDate");
	 * columns.put(6, "user_id");
	 */

	//private static String LOG_TAG = "BadgeDBHandler";
	private static String tableName = "badge";
	private static String createSchema = "CREATE TABLE IF NOT EXISTS " + tableName + "(" +
			"badge_id TEXT PRIMARY KEY, " +
			"title TEXT, " +
			"category TEXT, " +
			"description TEXT, " +
			"url TEXT, " +
			"unlockedDate TEXT, " +
			"user_id TEXT " +
			") ";
/*
	private static DatabaseHandler dbHandler = null;

	public BadgeDBHandler(Context context) {
		super();

		dbHandler = new DatabaseHandler(context, createSchema, tableName);        
		dbHandler.setColumns(SessionData.columns);
		//Log.d(LOG_TAG, "Database Hander :" + LOG_TAG + " is created!!!");
	}



	public boolean cleanAndInsert(Collection<Badge> sessions) {
		boolean res = false;

		dbHandler.deleteAll();
		
		if(sessions.isEmpty()){
			return true;
		}
		
		dbHandler.addItems(sessions);
		dbHandler.showAll();
		return res;
	}

	public Collection<Badge> all(){
		Collection<Badge> badges = new ArrayList<Badge>();
		Collection<IDatabaseRecord> records = dbHandler.getItems("user_id", Promise.getInstance().getProfileID());

		for(IDatabaseRecord record : records){
			Badge badge = new Badge();
			if(false == badge.setValues(record.getValues())){
				continue;
			}
			badges.add(badge);            
		}

		dbHandler.showAll();
		return badges;
	}
*/
}
