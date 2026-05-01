package io.sketch.mochaagents.examples;

import io.sketch.mochaagents.agents.CodeAgent;
import io.sketch.mochaagents.models.InferenceClientModel;
import io.sketch.mochaagents.tools.BaseTool;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;

public class TextToSqlExample {

    public static class SqlEngineTool extends BaseTool {
        private final Connection connection;

        public SqlEngineTool(Connection connection) {
            super("sql_engine", "Allows SQL queries on receipts table.");
            this.connection = connection;
        }

        public String call(String query) {
            StringBuilder output = new StringBuilder();
            try (Statement stmt = connection.createStatement()) {
                ResultSet rs = stmt.executeQuery(query);
                ResultSetMetaData metaData = rs.getMetaData();
                int columnCount = metaData.getColumnCount();
                
                for (int i = 1; i <= columnCount; i++) {
                    output.append(metaData.getColumnName(i)).append("\t");
                }
                output.append("\n");
                
                while (rs.next()) {
                    for (int i = 1; i <= columnCount; i++) {
                        output.append(rs.getString(i)).append("\t");
                    }
                    output.append("\n");
                }
            } catch (SQLException e) {
                output.append("SQL Error: ").append(e.getMessage());
            }
            return output.toString();
        }
    }

    public static void main(String[] args) throws SQLException {
        Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        
        String createTable = """
            CREATE TABLE receipts (
                receipt_id INTEGER PRIMARY KEY,
                customer_name VARCHAR(16),
                price FLOAT,
                tip FLOAT
            )
            """;
        
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createTable);
            
            stmt.execute("INSERT INTO receipts VALUES (1, 'Alan Payne', 12.06, 1.20)");
            stmt.execute("INSERT INTO receipts VALUES (2, 'Alex Mason', 23.86, 0.24)");
            stmt.execute("INSERT INTO receipts VALUES (3, 'Woodrow Wilson', 53.43, 5.43)");
            stmt.execute("INSERT INTO receipts VALUES (4, 'Margaret James', 21.11, 1.00)");
        }

        SqlEngineTool sqlTool = new SqlEngineTool(connection);

        try (CodeAgent agent = CodeAgent.builder()
            .tool(sqlTool)
            .model(new InferenceClientModel("meta-llama/Meta-Llama-3.1-8B-Instruct"))
            .build()) {

            agent.run("Give me the name of the client with the most expensive receipt?");
        } finally {
            connection.close();
        }
    }
}
