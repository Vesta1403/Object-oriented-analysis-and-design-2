package com.myfinances;

import javax.swing.*;
import java.awt.*;
import java.sql.*;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

public class TransactionDialog extends JDialog {
    private Connection db;
    private Integer editId; // null – добавление, число – редактирование
    private JTextField dateField;
    private JTextField amountField;
    private JComboBox<String> categoryCombo;
    private JTextArea noteArea;
    private Map<String, Integer> categoryMap = new HashMap<>(); // название -> id

    public TransactionDialog(JFrame parent, Connection db, Integer editId) {
        super(parent, editId == null ? "Добавить транзакцию" : "Редактировать транзакцию", true);
        this.db = db;
        this.editId = editId;
        initUI();
        if (editId != null) {
            loadTransactionData();
        }
        pack();
        setLocationRelativeTo(parent);
    }

    private void initUI() {
        setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);

        // Дата
        gbc.gridx = 0; gbc.gridy = 0;
        add(new JLabel("Дата (ГГГГ-ММ-ДД):"), gbc);
        gbc.gridx = 1;
        dateField = new JTextField(LocalDate.now().toString(), 15);
        add(dateField, gbc);

        // Сумма
        gbc.gridx = 0; gbc.gridy = 1;
        add(new JLabel("Сумма:"), gbc);
        gbc.gridx = 1;
        amountField = new JTextField(15);
        add(amountField, gbc);

        // Категория
        gbc.gridx = 0; gbc.gridy = 2;
        add(new JLabel("Категория:"), gbc);
        gbc.gridx = 1;
        loadCategories(); // загружаем категории из БД в categoryMap
        categoryCombo = new JComboBox<>(categoryMap.keySet().toArray(new String[0]));
        add(categoryCombo, gbc);

        // Примечание
        gbc.gridx = 0; gbc.gridy = 3;
        add(new JLabel("Примечание:"), gbc);
        gbc.gridx = 1;
        noteArea = new JTextArea(3, 15);
        add(new JScrollPane(noteArea), gbc);

        // Кнопки
        JPanel buttonPanel = new JPanel();
        JButton saveButton = new JButton("Сохранить");
        JButton cancelButton = new JButton("Отмена");
        saveButton.addActionListener(e -> saveTransaction());
        cancelButton.addActionListener(e -> dispose());
        buttonPanel.add(saveButton);
        buttonPanel.add(cancelButton);
        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 2;
        add(buttonPanel, gbc);
    }

    // Загружает список категорий из БД в словарь categoryMap
    private void loadCategories() {
        String sql = "SELECT id, name FROM categories ORDER BY name";
        try (Statement st = db.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            categoryMap.clear();
            while (rs.next()) {
                categoryMap.put(rs.getString("name"), rs.getInt("id"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Если редактируем – заполняем поля существующими данными
    private void loadTransactionData() {
        String sql = "SELECT date, amount, category_id, note FROM transactions WHERE id = ?";
        try (PreparedStatement pst = db.prepareStatement(sql)) {
            pst.setInt(1, editId);
            ResultSet rs = pst.executeQuery();
            if (rs.next()) {
                dateField.setText(rs.getString("date"));
                amountField.setText(String.valueOf(rs.getDouble("amount")));
                int catId = rs.getInt("category_id");
                // Находим название категории по id
                String catName = categoryMap.entrySet().stream()
                        .filter(e -> e.getValue() == catId)
                        .map(Map.Entry::getKey)
                        .findFirst().orElse("");
                categoryCombo.setSelectedItem(catName);
                noteArea.setText(rs.getString("note"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Сохраняет транзакцию (INSERT или UPDATE)
    private void saveTransaction() {
        String date = dateField.getText().trim();
        String amountStr = amountField.getText().trim();
        String selectedCategory = (String) categoryCombo.getSelectedItem();
        String note = noteArea.getText();

        if (date.isEmpty() || amountStr.isEmpty() || selectedCategory == null) {
            JOptionPane.showMessageDialog(this, "Заполните дату и сумму.");
            return;
        }
        double amount;
        try {
            amount = Double.parseDouble(amountStr);
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Сумма должна быть числом.");
            return;
        }
        int categoryId = categoryMap.get(selectedCategory);

        String sql;
        if (editId == null) {
            sql = "INSERT INTO transactions (date, amount, category_id, note) VALUES (?,?,?,?)";
        } else {
            sql = "UPDATE transactions SET date=?, amount=?, category_id=?, note=? WHERE id=?";
        }
        try (PreparedStatement pst = db.prepareStatement(sql)) {
            pst.setString(1, date);
            pst.setDouble(2, amount);
            pst.setInt(3, categoryId);
            pst.setString(4, note);
            if (editId != null) {
                pst.setInt(5, editId);
            }
            pst.executeUpdate();
            dispose(); // закрываем окно
        } catch (SQLException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Ошибка сохранения: " + e.getMessage());
        }
    }
}