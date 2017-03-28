package kr.blogspot.charlie0301.wimple.impl.db;

final class SQLQueries {

	static final int DB_VERSION = 3;
	
	static final String dbName = "wimple";


	static final String dropTable = "DROP TABLE IF EXISTS ";


	static final String findAll = "SELECT * FROM ";
	static final String findSome = "SELECT * FROM %s WHERE %s = ?";
	static final String findSomeWithWhere = "SELECT * FROM %s WHERE %s";


	static final String selDistinct = "SELECT DISTINCT %s FROM %s";

	
	static final String countAll = "SELECT rowid FROM ";
	static final String countSome = "SELECT rowid FROM %s WHERE %s = ?";
	static final String countSomeWithWhereStatement = "SELECT rowid FROM %s WHERE %s";
	
	
	static final String deleteSome = "DELETE FROM %s WHERE %s = ?";
	static final String deleteSomeWithWhereStatement = "DELETE FROM %s WHERE %s";
	
	
	
	
	
}

