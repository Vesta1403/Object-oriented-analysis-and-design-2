package com.myfinances;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.ResultSet;

public class DatabaseManager {
    static {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            System.err.println("SQLite JDBC не найден!");
            e.printStackTrace();
        }
    }

    private static final String DB_URL = "jdbc:sqlite:finances.db";

    public static Connection connect() {
        Connection conn = null;
        try {
            conn = DriverManager.getConnection(DB_URL);
        } catch (SQLException e) {
            System.out.println("Ошибка подключения: " + e.getMessage());
        }
        return conn;
    }

    public static void createTables() {
        String sqlCategories = "CREATE TABLE IF NOT EXISTS categories (\n"
                + "    id INTEGER PRIMARY KEY AUTOINCREMENT,\n"
                + "    name TEXT NOT NULL,\n"
                + "    type TEXT CHECK(type IN ('income','expense')) NOT NULL\n"
                + ");";

        String sqlTransactions = "CREATE TABLE IF NOT EXISTS transactions (\n"
                + "    id INTEGER PRIMARY KEY AUTOINCREMENT,\n"
                + "    date TEXT NOT NULL,\n"
                + "    amount REAL NOT NULL,\n"
                + "    category_id INTEGER,\n"
                + "    note TEXT,\n"
                + "    FOREIGN KEY(category_id) REFERENCES categories(id)\n"
                + ");";

        try (Connection conn = connect();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sqlCategories);
            stmt.execute(sqlTransactions);
            System.out.println("Таблицы созданы или уже существуют.");
        } catch (SQLException e) {
            System.out.println("Ошибка создания таблиц: " + e.getMessage());
        }
    }

    public static void insertDefaultCategories() {
        String checkSql = "SELECT COUNT(*) FROM categories";
        try (Connection conn = connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(checkSql)) {
            
            if (rs.getInt(1) == 0) {
                String insertSql = "INSERT INTO categories (name, type) VALUES\n"
                        + "('Зарплата', 'income'),\n"
                        + "('Фриланс', 'income'),\n"
                        + "('Продукты', 'expense'),\n"
                        + "('Транспорт', 'expense'),\n"
                        + "('ЖКХ', 'expense'),\n"
                        + "('Развлечения', 'expense'),\n"
                        + "('Здоровье', 'expense')";
                stmt.execute(insertSql);
                System.out.println("Добавлены начальные категории.");
            }
        } catch (SQLException e) {
            System.out.println("Ошибка вставки категорий: " + e.getMessage());
        }
    }
}