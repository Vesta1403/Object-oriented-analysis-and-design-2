package com.myfinances.plugin;

import com.myfinances.IFinancePlugin;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class SavingsGoalPlugin implements IFinancePlugin {

    @Override
    public String getName() {
        return "Финансовая цель";
    }

    @Override
    public String getDescription() {
        return "Установите цель накоплений и отслеживайте прогресс";
    }

    @Override
    public void execute(Connection dbConn, JFrame parent, LocalDate startDate, LocalDate endDate) {
        createGoalTableIfNotExists(dbConn);
        showGoalManager(parent, dbConn);
    }

    private void createGoalTableIfNotExists(Connection conn) {
        String sql = "CREATE TABLE IF NOT EXISTS savings_goals (\n"
                + "    id INTEGER PRIMARY KEY AUTOINCREMENT,\n"
                + "    name TEXT NOT NULL,\n"
                + "    target_amount REAL NOT NULL,\n"
                + "    deadline TEXT NOT NULL,\n"
                + "    initial_balance REAL DEFAULT 0,\n"
                + "    created_at TEXT DEFAULT CURRENT_TIMESTAMP\n"
                + ");";
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void showGoalManager(JFrame parent, Connection conn) {
        JDialog dialog = new JDialog(parent, "Мои цели", true);
        dialog.setSize(650, 450);
        dialog.setLocationRelativeTo(parent);
        dialog.setLayout(new BorderLayout());

        String[] columns = {"ID", "Название", "Цель (руб.)", "Дедлайн", "Накоплено", "Прогресс"};
        DefaultTableModel tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int col) {
                return false;
            }
        };
        JTable goalTable = new JTable(tableModel);
        JScrollPane scrollPane = new JScrollPane(goalTable);
        dialog.add(scrollPane, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout());
        JButton addButton = new JButton("Новая цель");
        JButton editButton = new JButton("Редактировать");
        JButton deleteButton = new JButton("Удалить");
        JButton refreshButton = new JButton("Обновить");
        buttonPanel.add(addButton);
        buttonPanel.add(editButton);
        buttonPanel.add(deleteButton);
        buttonPanel.add(refreshButton);
        dialog.add(buttonPanel, BorderLayout.SOUTH);

        Runnable loadData = () -> loadGoalsToTable(conn, tableModel);
        loadData.run();

        addButton.addActionListener(e -> {
            showGoalDialog(dialog, conn, null);
            loadData.run();
        });
        editButton.addActionListener(e -> {
            int row = goalTable.getSelectedRow();
            if (row == -1) {
                JOptionPane.showMessageDialog(dialog, "Выберите цель.");
                return;
            }
            int id = (int) tableModel.getValueAt(row, 0);
            showGoalDialog(dialog, conn, id);
            loadData.run();
        });
        deleteButton.addActionListener(e -> {
            int row = goalTable.getSelectedRow();
            if (row == -1) return;
            int confirm = JOptionPane.showConfirmDialog(dialog, "Удалить цель?", "Подтверждение", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                int id = (int) tableModel.getValueAt(row, 0);
                deleteGoal(conn, id);
                loadData.run();
            }
        });
        refreshButton.addActionListener(e -> loadData.run());

        dialog.setVisible(true);
    }

    private void loadGoalsToTable(Connection conn, DefaultTableModel model) {
        model.setRowCount(0);
        String sql = "SELECT id, name, target_amount, deadline, initial_balance FROM savings_goals ORDER BY deadline";
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                int id = rs.getInt("id");
                String name = rs.getString("name");
                double target = rs.getDouble("target_amount");
                String deadline = rs.getString("deadline");
                double initial = rs.getDouble("initial_balance");

                double currentBalance = getCurrentBalance(conn);
                double saved = currentBalance - initial;
                if (saved < 0) saved = 0;
                int percent = (int) ((saved / target) * 100);
                if (percent > 100) percent = 100;

                model.addRow(new Object[]{
                        id,
                        name,
                        target,
                        deadline,
                        String.format("%.2f", saved),
                        String.format("%d%%", percent)
                });
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private double getCurrentBalance(Connection conn) {
        String sql = "SELECT SUM(CASE WHEN c.type='income' THEN t.amount ELSE -t.amount END) as balance\n"
                + "FROM transactions t\n"
                + "JOIN categories c ON t.category_id = c.id";
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            if (rs.next()) return rs.getDouble("balance");
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0.0;
    }

    private void showGoalDialog(JDialog parent, Connection conn, Integer goalId) {
        JDialog dialog = new JDialog(parent, goalId == null ? "Новая цель" : "Редактирование цели", true);
        dialog.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.gridx = 0; gbc.gridy = 0;
        dialog.add(new JLabel("Название:"), gbc);
        gbc.gridx = 1;
        JTextField nameField = new JTextField(15);
        dialog.add(nameField, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        dialog.add(new JLabel("Цель (руб.):"), gbc);
        gbc.gridx = 1;
        JTextField amountField = new JTextField(15);
        dialog.add(amountField, gbc);

        gbc.gridx = 0; gbc.gridy = 2;
        dialog.add(new JLabel("Дедлайн (ГГГГ-ММ-ДД):"), gbc);
        gbc.gridx = 1;
        JTextField deadlineField = new JTextField(LocalDate.now().plusMonths(1).toString(), 15);
        dialog.add(deadlineField, gbc);

        gbc.gridx = 0; gbc.gridy = 3;
        dialog.add(new JLabel("Начальный баланс (руб.):"), gbc);
        gbc.gridx = 1;
        JTextField initialField = new JTextField("0", 15);
        dialog.add(initialField, gbc);

        if (goalId != null) {
            try (PreparedStatement pst = conn.prepareStatement("SELECT name, target_amount, deadline, initial_balance FROM savings_goals WHERE id=?")) {
                pst.setInt(1, goalId);
                ResultSet rs = pst.executeQuery();
                if (rs.next()) {
                    nameField.setText(rs.getString("name"));
                    amountField.setText(String.valueOf(rs.getDouble("target_amount")));
                    deadlineField.setText(rs.getString("deadline"));
                    initialField.setText(String.valueOf(rs.getDouble("initial_balance")));
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        JButton saveBtn = new JButton("Сохранить");
        JButton cancelBtn = new JButton("Отмена");
        JPanel btnPanel = new JPanel();
        btnPanel.add(saveBtn);
        btnPanel.add(cancelBtn);
        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 2;
        dialog.add(btnPanel, gbc);

        saveBtn.addActionListener(e -> {
            try {
                String name = nameField.getText().trim();
                double target = Double.parseDouble(amountField.getText().trim());
                String deadline = deadlineField.getText().trim();
                double initial = Double.parseDouble(initialField.getText().trim());
                if (name.isEmpty() || target <= 0) throw new Exception("Некорректные данные");
                LocalDate.parse(deadline, DateTimeFormatter.ISO_LOCAL_DATE); // валидация

                if (goalId == null) {
                    try (PreparedStatement pst = conn.prepareStatement("INSERT INTO savings_goals (name, target_amount, deadline, initial_balance) VALUES (?,?,?,?)")) {
                        pst.setString(1, name);
                        pst.setDouble(2, target);
                        pst.setString(3, deadline);
                        pst.setDouble(4, initial);
                        pst.executeUpdate();
                    }
                } else {
                    try (PreparedStatement pst = conn.prepareStatement("UPDATE savings_goals SET name=?, target_amount=?, deadline=?, initial_balance=? WHERE id=?")) {
                        pst.setString(1, name);
                        pst.setDouble(2, target);
                        pst.setString(3, deadline);
                        pst.setDouble(4, initial);
                        pst.setInt(5, goalId);
                        pst.executeUpdate();
                    }
                }
                dialog.dispose();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dialog, "Ошибка: " + ex.getMessage());
            }
        });
        cancelBtn.addActionListener(e -> dialog.dispose());

        dialog.pack();
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
    }

    private void deleteGoal(Connection conn, int goalId) {
        try (PreparedStatement pst = conn.prepareStatement("DELETE FROM savings_goals WHERE id=?")) {
            pst.setInt(1, goalId);
            pst.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}