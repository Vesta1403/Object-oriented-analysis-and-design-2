package com.finances;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class FinanceApp extends JFrame {
    private JTable transactionTable;
    private DefaultTableModel tableModel;
    private Connection dbConnection;
    private JComboBox<String> monthFilter;
    private JComboBox<String> categoryFilter;
    private JLabel balanceLabel, incomeLabel, expenseLabel;

    public FinanceApp() {
        DatabaseManager.createTables();
        DatabaseManager.insertDefaultCategories();
        dbConnection = DatabaseManager.connect();
        initUI();
        loadTransactions();
    }

    private void initUI() {
        setTitle("Личный финансовый учёт");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1100, 700);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        JPanel topPanel = new JPanel(new GridLayout(2, 1));

        // Панель фильтров
        JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        filterPanel.add(new JLabel("Месяц:"));
        monthFilter = new JComboBox<>(getAvailableMonths());
        filterPanel.add(monthFilter);

        filterPanel.add(new JLabel("Категория:"));
        categoryFilter = new JComboBox<>(getCategoriesForFilter());
        filterPanel.add(categoryFilter);

        JButton filterButton = new JButton("Применить фильтр");
        filterButton.addActionListener(e -> loadTransactions());
        filterPanel.add(filterButton);

        // Кнопки дополнительных модулей
        JButton goalButton = new JButton("Финансовые цели");
        goalButton.addActionListener(e -> SavingsGoalManager.showDialog(this, dbConnection));
        JButton calendarButton = new JButton("Платежный календарь");
        calendarButton.addActionListener(e -> PaymentCalendarManager.showDialog(this, dbConnection));
        filterPanel.add(goalButton);
        filterPanel.add(calendarButton);

        // Панель итогов
        JPanel summaryPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        balanceLabel = new JLabel("Баланс: 0.00");
        incomeLabel = new JLabel("Доходы: 0.00");
        expenseLabel = new JLabel("Расходы: 0.00");
        summaryPanel.add(balanceLabel);
        summaryPanel.add(incomeLabel);
        summaryPanel.add(expenseLabel);

        topPanel.add(filterPanel);
        topPanel.add(summaryPanel);
        add(topPanel, BorderLayout.NORTH);

        // Таблица транзакций
        String[] columns = {"ID", "Дата", "Сумма", "Категория", "Примечание"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        transactionTable = new JTable(tableModel);
        JScrollPane scrollPane = new JScrollPane(transactionTable);
        add(scrollPane, BorderLayout.CENTER);

        // Кнопки управления транзакциями
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        JButton addButton = new JButton("Добавить");
        JButton editButton = new JButton("Редактировать");
        JButton deleteButton = new JButton("Удалить");

        addButton.addActionListener(e -> openTransactionDialog(null));
        editButton.addActionListener(e -> editSelectedTransaction());
        deleteButton.addActionListener(e -> deleteSelectedTransaction());

        buttonPanel.add(addButton);
        buttonPanel.add(editButton);
        buttonPanel.add(deleteButton);
        add(buttonPanel, BorderLayout.SOUTH);
    }

    private String[] getAvailableMonths() {
        String[] months = new String[12];
        LocalDate now = LocalDate.now();
        for (int i = 0; i < 12; i++) {
            LocalDate date = now.minusMonths(i);
            months[i] = date.format(DateTimeFormatter.ofPattern("yyyy-MM"));
        }
        return months;
    }

    private String[] getCategoriesForFilter() {
        List<String> cats = new ArrayList<>();
        cats.add("Все");
        String sql = "SELECT name FROM categories ORDER BY name";
        try (Statement st = dbConnection.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                cats.add(rs.getString("name"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return cats.toArray(new String[0]);
    }

    private void loadTransactions() {
        String selectedMonth = (String) monthFilter.getSelectedItem();
        String selectedCategory = (String) categoryFilter.getSelectedItem();

        String sql = "SELECT t.id, t.date, t.amount, c.name as cat, t.note " +
                "FROM transactions t " +
                "LEFT JOIN categories c ON t.category_id = c.id " +
                "WHERE strftime('%Y-%m', t.date) = ? ";
        if (!selectedCategory.equals("Все")) {
            sql += "AND c.name = ? ";
        }
        sql += "ORDER BY t.date DESC";

        try (PreparedStatement pst = dbConnection.prepareStatement(sql)) {
            pst.setString(1, selectedMonth);
            if (!selectedCategory.equals("Все")) {
                pst.setString(2, selectedCategory);
            }
            ResultSet rs = pst.executeQuery();

            tableModel.setRowCount(0);
            while (rs.next()) {
                tableModel.addRow(new Object[]{
                        rs.getInt("id"),
                        rs.getString("date"),
                        rs.getDouble("amount"),
                        rs.getString("cat"),
                        rs.getString("note")
                });
            }
            updateSummary(selectedMonth);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void updateSummary(String month) {
        String sql = "SELECT " +
                "SUM(CASE WHEN c.type='income' THEN t.amount ELSE 0 END) as income, " +
                "SUM(CASE WHEN c.type='expense' THEN t.amount ELSE 0 END) as expense " +
                "FROM transactions t " +
                "JOIN categories c ON t.category_id = c.id " +
                "WHERE strftime('%Y-%m', t.date) = ?";
        try (PreparedStatement pst = dbConnection.prepareStatement(sql)) {
            pst.setString(1, month);
            ResultSet rs = pst.executeQuery();
            double income = rs.getDouble("income");
            double expense = rs.getDouble("expense");
            double balance = income - expense;
            balanceLabel.setText(String.format("Баланс: %.2f", balance));
            incomeLabel.setText(String.format("Доходы: %.2f", income));
            expenseLabel.setText(String.format("Расходы: %.2f", expense));
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void openTransactionDialog(Integer editId) {
        TransactionDialog dialog = new TransactionDialog(this, dbConnection, editId);
        dialog.setVisible(true);
        loadTransactions();
    }

    private void editSelectedTransaction() {
        int selectedRow = transactionTable.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "Выберите транзакцию для редактирования.");
            return;
        }
        int id = (int) tableModel.getValueAt(selectedRow, 0);
        openTransactionDialog(id);
    }

    private void deleteSelectedTransaction() {
        int selectedRow = transactionTable.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "Выберите транзакцию для удаления.");
            return;
        }
        int id = (int) tableModel.getValueAt(selectedRow, 0);
        int confirm = JOptionPane.showConfirmDialog(this, "Удалить запись?", "Подтверждение", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            String sql = "DELETE FROM transactions WHERE id = ?";
            try (PreparedStatement pst = dbConnection.prepareStatement(sql)) {
                pst.setInt(1, id);
                pst.executeUpdate();
                loadTransactions();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new FinanceApp().setVisible(true));
    }
}