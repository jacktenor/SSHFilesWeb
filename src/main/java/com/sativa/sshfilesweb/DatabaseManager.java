package com.sativa.sshfilesweb;

import org.springframework.stereotype.Component;
import java.sql.Connection;
import java.sql.SQLException;

@Component
public class DatabaseManager {
    private Connection connection;

    public DatabaseManager() {
        // Initialize connection (pseudo-code)
        // this.connection = initializeConnection();
    }

    public void shutdown() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                e.printStackTrace();
                // Log or handle exception
            }
        }
    }
}
