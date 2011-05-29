package databank;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

public class PostgresDataBank extends DataBank {

	public PostgresDataBank(String DBName) {
		super(DBName);
	}

	@Override
	void establishConnection() throws SQLException {
		String url = "jdbc:postgresql://localhost:5433/" + DBName;
		Properties props = new Properties();
		props.setProperty("user", "knowledge");
		props.setProperty("password", "Kn0ledge");
		try {
			if (conn == null) {
				Class.forName("org.postgresql.Driver");
				conn = DriverManager.getConnection(url, props);
			}
			if (conn.isClosed()) {
				Class.forName("org.postgresql.Driver");
				conn = DriverManager.getConnection(url, props);
			}
		} catch (ClassNotFoundException e) {
			System.out.println(e.toString());
			System.exit(0);
		}
	}

	@Override
	public void initDB() {
		try {
			establishConnection();
			Statement stat = conn.createStatement();
			stat.executeUpdate("delete from sentence_word_link");
			stat.executeUpdate("delete from sentence_word;");
			stat.executeUpdate("delete from sentences;");
//			stat.executeUpdate("delete from words;");
			stat.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
