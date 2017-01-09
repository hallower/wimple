package kr.blogspot.charlie0301.wimple.impl.db;

public final class SQLQueries {

	public static final int DB_VERSION = 3;
	
	public static final String dbName = "wimple";
	
	public static final String dropTable = "DROP TABLE IF EXISTS ";
	
	
	public static final String findAll = "SELECT * FROM ";
	public static final String findSome = "SELECT * FROM %s WHERE %s = ?";
	public static final String findSomeWithWhere = "SELECT * FROM %s WHERE %s";

	public static final String selDistinct = "SELECT DISTINCT %s FROM %s";

	
	public static final String countAll = "SELECT rowid FROM ";
	public static final String countSome = "SELECT rowid FROM %s WHERE %s = ?";
	public static final String countSomeWithWhereStatement = "SELECT rowid FROM %s WHERE %s";
	
	
	public static final String deleteSome = "DELETE FROM %s WHERE %s = ?";
	public static final String deleteSomeWithWhereStatement = "DELETE FROM %s WHERE %s";	
	
	
	
	
	
}

