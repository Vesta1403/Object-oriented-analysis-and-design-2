package com.myfinances.plugin;

import com.myfinances.IFinancePlugin;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.sql.*;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.List;

public class PaymentCalendarPlugin implements IFinancePlugin {

    @Override
    public String getName() { return "Платежный календарь"; }

    @Override
    public String getDescription() { return "Управление регулярными платежами и календарь"; }

    @Override
    public void execute(Connection dbConn, JFrame parent, LocalDate startDate, LocalDate endDate) {
        createTableIfNotExists(dbConn);
        showCalendarWindow(parent, dbConn);
    }

    private void createTableIfNotExists(Connection conn) {
        String sql = "CREATE TABLE IF NOT EXISTS scheduled_payments (\n"
                + "    id INTEGER PRIMARY KEY AUTOINCREMENT,\n"
                + "    name TEXT NOT NULL,\n"
                + "    category_id INTEGER NOT NULL,\n"
                + "    amount REAL NOT NULL,\n"
                + "    day_of_month INTEGER NOT NULL CHECK(day_of_month BETWEEN 1 AND 31),\n"
                + "    note TEXT,\n"
                + "    last_created_date TEXT,\n"
                + "    FOREIGN KEY(category_id) REFERENCES categories(id)\n"
                + ");";
        try (Statement stmt = conn.createStatement()) { stmt.execute(sql); }
        catch (SQLException e) { e.printStackTrace(); }
    }

    private void showCalendarWindow(JFrame parent, Connection conn) {
        JDialog dialog = new JDialog(parent, "Платежный календарь", true);
        dialog.setSize(900, 700);
        dialog.setLocationRelativeTo(parent);
        dialog.setLayout(new BorderLayout());

        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JSpinner monthSpinner = createMonthSpinner();
        JButton createAllBtn = new JButton("Создать непроведённые платежи за месяц");
        topPanel.add(new JLabel("Месяц:"));
        topPanel.add(monthSpinner);
        topPanel.add(createAllBtn);
        dialog.add(topPanel, BorderLayout.NORTH);

        JTable calendarTable = new JTable();
        calendarTable.setRowHeight(60);
        calendarTable.setDefaultRenderer(Object.class, new CalendarCellRenderer());
        JScrollPane calendarScroll = new JScrollPane(calendarTable);
        dialog.add(calendarScroll, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new BorderLayout());
        JPanel eventsPanel = new JPanel(new BorderLayout());
        eventsPanel.setBorder(BorderFactory.createTitledBorder("События выбранного дня"));
        JTable eventsTable = new JTable();
        eventsTable.setFillsViewportHeight(true);
        JScrollPane eventsScroll = new JScrollPane(eventsTable);
        eventsPanel.add(eventsScroll, BorderLayout.CENTER);

        JPanel eventsButtons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton addEventBtn = new JButton("Добавить событие");
        JButton editEventBtn = new JButton("Редактировать");
        JButton deleteEventBtn = new JButton("Удалить");
        JButton createTransactionBtn = new JButton("Создать транзакцию по событию");
        eventsButtons.add(addEventBtn);
        eventsButtons.add(editEventBtn);
        eventsButtons.add(deleteEventBtn);
        eventsButtons.add(createTransactionBtn);
        eventsPanel.add(eventsButtons, BorderLayout.SOUTH);
        bottomPanel.add(eventsPanel, BorderLayout.CENTER);

        JPanel allEventsPanel = new JPanel(new BorderLayout());
        allEventsPanel.setBorder(BorderFactory.createTitledBorder("Все регулярные платежи"));
        JTable allEventsTable = new JTable();
        allEventsTable.setFillsViewportHeight(true);
        JScrollPane allEventsScroll = new JScrollPane(allEventsTable);
        allEventsPanel.add(allEventsScroll, BorderLayout.CENTER);
        JButton refreshAllBtn = new JButton("Обновить список");
        allEventsPanel.add(refreshAllBtn, BorderLayout.SOUTH);
        bottomPanel.add(allEventsPanel, BorderLayout.EAST);
        bottomPanel.setPreferredSize(new Dimension(0, 250));

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, calendarScroll, bottomPanel);
        splitPane.setResizeWeight(0.6);
        dialog.add(splitPane, BorderLayout.CENTER);

        Runnable loadCalendar = () -> loadCalendar(calendarTable, monthSpinner, conn, eventsTable, allEventsTable, parent);
        Runnable loadAllEvents = () -> loadAllEvents(allEventsTable, conn);
        Runnable loadEventsForSelectedDay = () -> loadEventsForSelectedDay(calendarTable, conn, eventsTable, monthSpinner, parent);

        loadCalendar.run();
        loadAllEvents.run();

        calendarTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) loadEventsForSelectedDay.run();
        });
        monthSpinner.addChangeListener(e -> loadCalendar.run());
        createAllBtn.addActionListener(e -> createAllMissedPayments(conn, getSelectedYearMonth(monthSpinner), parent, loadCalendar, loadAllEvents, loadEventsForSelectedDay));
        addEventBtn.addActionListener(e -> showEventDialog(dialog, conn, null, parent, loadCalendar, loadAllEvents, loadEventsForSelectedDay));
        editEventBtn.addActionListener(e -> {
            int row = allEventsTable.getSelectedRow();
            if (row == -1) { JOptionPane.showMessageDialog(dialog, "Выберите событие в правой таблице."); return; }
            int id = (int) allEventsTable.getValueAt(row, 0);
            showEventDialog(dialog, conn, id, parent, loadCalendar, loadAllEvents, loadEventsForSelectedDay);
        });
        deleteEventBtn.addActionListener(e -> {
            int row = allEventsTable.getSelectedRow();
            if (row == -1) return;
            int id = (int) allEventsTable.getValueAt(row, 0);
            int confirm = JOptionPane.showConfirmDialog(dialog, "Удалить событие?", "Подтверждение", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                deleteEvent(conn, id);
                loadAllEvents.run();
                loadCalendar.run();
                loadEventsForSelectedDay.run();
            }
        });
        createTransactionBtn.addActionListener(e -> {
            int row = eventsTable.getSelectedRow();
            if (row == -1) row = allEventsTable.getSelectedRow();
            if (row == -1) { JOptionPane.showMessageDialog(dialog, "Выберите событие."); return; }
            int eventId = (row < eventsTable.getRowCount()) ? (int) eventsTable.getValueAt(row, 0) : (int) allEventsTable.getValueAt(row, 0);
            LocalDate selectedDate = getSelectedDateFromCalendar(calendarTable, getSelectedYearMonth(monthSpinner));
            if (selectedDate == null) { JOptionPane.showMessageDialog(dialog, "Выберите день в календаре."); return; }
            createTransactionFromEvent(conn, eventId, selectedDate, parent);
            loadEventsForSelectedDay.run();
        });
        refreshAllBtn.addActionListener(e -> loadAllEvents.run());
        dialog.setVisible(true);
    }

    private JSpinner createMonthSpinner() {
        SpinnerDateModel model = new SpinnerDateModel();
        JSpinner spinner = new JSpinner(model);
        JSpinner.DateEditor editor = new JSpinner.DateEditor(spinner, "MMMM yyyy");
        spinner.setEditor(editor);
        editor.getTextField().setLocale(new Locale("ru"));
        spinner.setValue(java.util.Calendar.getInstance().getTime());
        return spinner;
    }

    private YearMonth getSelectedYearMonth(JSpinner spinner) {
        java.util.Date date = (java.util.Date) spinner.getValue();
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTime(date);
        return YearMonth.of(cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH) + 1);
    }

    private void loadCalendar(JTable calendarTable, JSpinner monthSpinner, Connection conn, JTable eventsTable, JTable allEventsTable, JFrame parent) {
        YearMonth yearMonth = getSelectedYearMonth(monthSpinner);
        LocalDate firstDay = yearMonth.atDay(1);
        int daysInMonth = yearMonth.lengthOfMonth();
        int startWeekday = firstDay.getDayOfWeek().getValue();
        Map<Integer, List<ScheduledPayment>> paymentsByDay = new HashMap<>();
        String sql = "SELECT sp.id, sp.name, sp.amount, c.name as category, sp.day_of_month, sp.note " +
                "FROM scheduled_payments sp JOIN categories c ON sp.category_id = c.id " +
                "WHERE sp.day_of_month <= ? ORDER BY sp.day_of_month";
        try (PreparedStatement pst = conn.prepareStatement(sql)) {
            pst.setInt(1, daysInMonth);
            ResultSet rs = pst.executeQuery();
            while (rs.next()) {
                ScheduledPayment p = new ScheduledPayment(rs.getInt("id"), rs.getString("name"), rs.getDouble("amount"),
                        rs.getString("category"), rs.getInt("day_of_month"), rs.getString("note"));
                paymentsByDay.computeIfAbsent(p.dayOfMonth, k -> new ArrayList<>()).add(p);
            }
        } catch (SQLException e) { e.printStackTrace(); }
        String[] colNames = {"Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"};
        DefaultTableModel model = new DefaultTableModel(colNames, 0) { public boolean isCellEditable(int r, int c) { return false; } };
        int dayCounter = 1;
        for (int row = 0; row < 6; row++) {
            Vector<Object> rowData = new Vector<>();
            for (int col = 0; col < 7; col++) {
                if (row == 0 && col + 1 < startWeekday) rowData.add(null);
                else if (dayCounter > daysInMonth) rowData.add(null);
                else { rowData.add(new CalendarDayCell(dayCounter, paymentsByDay.getOrDefault(dayCounter, Collections.emptyList()))); dayCounter++; }
            }
            model.addRow(rowData);
            if (dayCounter > daysInMonth) break;
        }
        calendarTable.setModel(model);
        for (int i = 0; i < calendarTable.getColumnCount(); i++) calendarTable.getColumnModel().getColumn(i).setPreferredWidth(100);
    }

    private LocalDate getSelectedDateFromCalendar(JTable calendarTable, YearMonth yearMonth) {
        int row = calendarTable.getSelectedRow(), col = calendarTable.getSelectedColumn();
        if (row < 0 || col < 0) return null;
        Object value = calendarTable.getValueAt(row, col);
        if (value instanceof CalendarDayCell) return yearMonth.atDay(((CalendarDayCell) value).day);
        return null;
    }

    private void loadEventsForSelectedDay(JTable calendarTable, Connection conn, JTable eventsTable, JSpinner monthSpinner, JFrame parent) {
        YearMonth yearMonth = getSelectedYearMonth(monthSpinner);
        LocalDate selectedDate = getSelectedDateFromCalendar(calendarTable, yearMonth);
        if (selectedDate == null) { eventsTable.setModel(new DefaultTableModel()); return; }
        int day = selectedDate.getDayOfMonth();
        String sql = "SELECT sp.id, sp.name, sp.amount, c.name as category, sp.note FROM scheduled_payments sp JOIN categories c ON sp.category_id = c.id WHERE sp.day_of_month = ?";
        try (PreparedStatement pst = conn.prepareStatement(sql)) {
            pst.setInt(1, day);
            ResultSet rs = pst.executeQuery();
            DefaultTableModel model = new DefaultTableModel(new String[]{"ID","Название","Сумма","Категория","Примечание"},0) {
                public boolean isCellEditable(int r,int c) { return false; }
            };
            while (rs.next()) model.addRow(new Object[]{rs.getInt("id"), rs.getString("name"), rs.getDouble("amount"), rs.getString("category"), rs.getString("note")});
            eventsTable.setModel(model);
        } catch (SQLException e) { e.printStackTrace(); }
    }

    private void loadAllEvents(JTable allEventsTable, Connection conn) {
        String sql = "SELECT sp.id, sp.name, sp.amount, c.name as category, sp.day_of_month, sp.note FROM scheduled_payments sp JOIN categories c ON sp.category_id = c.id ORDER BY sp.day_of_month";
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            DefaultTableModel model = new DefaultTableModel(new String[]{"ID","Название","Сумма","Категория","День месяца","Примечание"},0) {
                public boolean isCellEditable(int r,int c) { return false; }
            };
            while (rs.next()) model.addRow(new Object[]{rs.getInt("id"), rs.getString("name"), rs.getDouble("amount"), rs.getString("category"), rs.getInt("day_of_month"), rs.getString("note")});
            allEventsTable.setModel(model);
        } catch (SQLException e) { e.printStackTrace(); }
    }

    private void showEventDialog(JDialog parent, Connection conn, Integer eventId, JFrame frame, Runnable... refreshCallbacks) {
        JDialog dialog = new JDialog(parent, eventId == null ? "Новое событие" : "Редактирование события", true);
        dialog.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5,5,5,5);
        gbc.gridx = 0; gbc.gridy = 0; dialog.add(new JLabel("Название:"), gbc);
        gbc.gridx = 1; JTextField nameField = new JTextField(15); dialog.add(nameField, gbc);
        gbc.gridx = 0; gbc.gridy = 1; dialog.add(new JLabel("Категория:"), gbc);
        gbc.gridx = 1; JComboBox<String> categoryCombo = new JComboBox<>(getCategories(conn)); dialog.add(categoryCombo, gbc);
        gbc.gridx = 0; gbc.gridy = 2; dialog.add(new JLabel("Сумма:"), gbc);
        gbc.gridx = 1; JTextField amountField = new JTextField(15); dialog.add(amountField, gbc);
        gbc.gridx = 0; gbc.gridy = 3; dialog.add(new JLabel("День месяца (1-31):"), gbc);
        gbc.gridx = 1; JSpinner daySpinner = new JSpinner(new SpinnerNumberModel(1,1,31,1)); dialog.add(daySpinner, gbc);
        gbc.gridx = 0; gbc.gridy = 4; dialog.add(new JLabel("Примечание:"), gbc);
        gbc.gridx = 1; JTextArea noteArea = new JTextArea(3,15); dialog.add(new JScrollPane(noteArea), gbc);

        if (eventId != null) {
            try (PreparedStatement pst = conn.prepareStatement("SELECT name, category_id, amount, day_of_month, note FROM scheduled_payments WHERE id=?")) {
                pst.setInt(1, eventId);
                ResultSet rs = pst.executeQuery();
                if (rs.next()) {
                    nameField.setText(rs.getString("name"));
                    int catId = rs.getInt("category_id");
                    String catName = getCategoryNameById(conn, catId);
                    categoryCombo.setSelectedItem(catName);
                    amountField.setText(String.valueOf(rs.getDouble("amount")));
                    daySpinner.setValue(rs.getInt("day_of_month"));
                    noteArea.setText(rs.getString("note"));
                }
            } catch (SQLException e) { e.printStackTrace(); }
        }

        JButton saveBtn = new JButton("Сохранить"), cancelBtn = new JButton("Отмена");
        JPanel btnPanel = new JPanel(); btnPanel.add(saveBtn); btnPanel.add(cancelBtn);
        gbc.gridx = 0; gbc.gridy = 5; gbc.gridwidth = 2; dialog.add(btnPanel, gbc);

        saveBtn.addActionListener(e -> {
            try {
                String name = nameField.getText().trim();
                if (name.isEmpty()) throw new Exception("Введите название");
                String selectedCat = (String) categoryCombo.getSelectedItem();
                int categoryId = getCategoryIdByName(conn, selectedCat);
                double amount = Double.parseDouble(amountField.getText().trim());
                int day = (int) daySpinner.getValue();
                String note = noteArea.getText();
                if (eventId == null) {
                    String sql = "INSERT INTO scheduled_payments (name, category_id, amount, day_of_month, note) VALUES (?,?,?,?,?)";
                    try (PreparedStatement pst = conn.prepareStatement(sql)) {
                        pst.setString(1, name); pst.setInt(2, categoryId); pst.setDouble(3, amount); pst.setInt(4, day); pst.setString(5, note);
                        pst.executeUpdate();
                    }
                } else {
                    String sql = "UPDATE scheduled_payments SET name=?, category_id=?, amount=?, day_of_month=?, note=? WHERE id=?";
                    try (PreparedStatement pst = conn.prepareStatement(sql)) {
                        pst.setString(1, name); pst.setInt(2, categoryId); pst.setDouble(3, amount); pst.setInt(4, day); pst.setString(5, note); pst.setInt(6, eventId);
                        pst.executeUpdate();
                    }
                }
                dialog.dispose();
                for (Runnable r : refreshCallbacks) r.run();
            } catch (Exception ex) { JOptionPane.showMessageDialog(dialog, "Ошибка: " + ex.getMessage()); }
        });
        cancelBtn.addActionListener(ev -> dialog.dispose());
        dialog.pack();
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
    }

    private void deleteEvent(Connection conn, int id) {
        try (PreparedStatement pst = conn.prepareStatement("DELETE FROM scheduled_payments WHERE id=?")) { pst.setInt(1, id); pst.executeUpdate(); }
        catch (SQLException e) { e.printStackTrace(); }
    }

    private void createTransactionFromEvent(Connection conn, int eventId, LocalDate date, JFrame parent) {
        try (PreparedStatement pst = conn.prepareStatement("SELECT name, category_id, amount, note FROM scheduled_payments WHERE id=?")) {
            pst.setInt(1, eventId);
            ResultSet rs = pst.executeQuery();
            if (rs.next()) {
                String name = rs.getString("name");
                int catId = rs.getInt("category_id");
                double amount = rs.getDouble("amount");
                String note = rs.getString("note");
                String insert = "INSERT INTO transactions (date, amount, category_id, note) VALUES (?,?,?,?)";
                try (PreparedStatement ins = conn.prepareStatement(insert)) {
                    ins.setString(1, date.toString());
                    ins.setDouble(2, amount);
                    ins.setInt(3, catId);
                    ins.setString(4, (note != null ? note : "") + " (авто: " + name + ")");
                    ins.executeUpdate();
                    JOptionPane.showMessageDialog(parent, "Транзакция создана на " + date);
                }
            }
        } catch (SQLException e) { e.printStackTrace(); JOptionPane.showMessageDialog(parent, "Ошибка: " + e.getMessage()); }
    }

    private void createAllMissedPayments(Connection conn, YearMonth yearMonth, JFrame parent, Runnable... refreshCallbacks) {
        int daysInMonth = yearMonth.lengthOfMonth();
        int createdCount = 0;
        String sql = "SELECT id, day_of_month FROM scheduled_payments WHERE day_of_month <= ?";
        try (PreparedStatement pst = conn.prepareStatement(sql)) {
            pst.setInt(1, daysInMonth);
            ResultSet rs = pst.executeQuery();
            while (rs.next()) {
                int id = rs.getInt("id");
                int day = rs.getInt("day_of_month");
                LocalDate targetDate = yearMonth.atDay(day);
                if (!isTransactionAlreadyCreated(conn, targetDate, id)) {
                    createTransactionFromEvent(conn, id, targetDate, parent);
                    createdCount++;
                }
            }
            if (createdCount > 0) { JOptionPane.showMessageDialog(parent, "Создано транзакций: " + createdCount); for (Runnable r : refreshCallbacks) r.run(); }
            else { JOptionPane.showMessageDialog(parent, "Все платежи за месяц уже созданы."); }
        } catch (SQLException e) { e.printStackTrace(); }
    }

    private boolean isTransactionAlreadyCreated(Connection conn, LocalDate date, int eventId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM transactions t JOIN scheduled_payments sp ON t.category_id = sp.category_id WHERE t.date = ? AND sp.id = ?";
        try (PreparedStatement pst = conn.prepareStatement(sql)) {
            pst.setString(1, date.toString()); pst.setInt(2, eventId);
            ResultSet rs = pst.executeQuery();
            return rs.getInt(1) > 0;
        }
    }

    private String[] getCategories(Connection conn) {
        List<String> cats = new ArrayList<>();
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT name FROM categories ORDER BY name")) {
            while (rs.next()) cats.add(rs.getString("name"));
        } catch (SQLException e) { e.printStackTrace(); }
        return cats.toArray(new String[0]);
    }

    private int getCategoryIdByName(Connection conn, String name) {
        try (PreparedStatement pst = conn.prepareStatement("SELECT id FROM categories WHERE name=?")) {
            pst.setString(1, name);
            ResultSet rs = pst.executeQuery();
            if (rs.next()) return rs.getInt("id");
        } catch (SQLException e) { e.printStackTrace(); }
        return -1;
    }

    private String getCategoryNameById(Connection conn, int id) {
        try (PreparedStatement pst = conn.prepareStatement("SELECT name FROM categories WHERE id=?")) {
            pst.setInt(1, id);
            ResultSet rs = pst.executeQuery();
            if (rs.next()) return rs.getString("name");
        } catch (SQLException e) { e.printStackTrace(); }
        return "";
    }

    static class ScheduledPayment {
        int id; String name; double amount; String category; int dayOfMonth; String note;
        ScheduledPayment(int id, String name, double amount, String category, int dayOfMonth, String note) {
            this.id = id; this.name = name; this.amount = amount; this.category = category; this.dayOfMonth = dayOfMonth; this.note = note;
        }
    }

    static class CalendarDayCell {
        int day; List<ScheduledPayment> payments;
        CalendarDayCell(int day, List<ScheduledPayment> payments) { this.day = day; this.payments = payments; }
        @Override public String toString() {
            StringBuilder sb = new StringBuilder("<html><b>" + day + "</b>");
            if (!payments.isEmpty()) { sb.append("<br><font size='2' color='gray'>"); for (ScheduledPayment p : payments) sb.append(p.name).append(" ").append(p.amount).append("<br>"); sb.append("</font>"); }
            sb.append("</html>");
            return sb.toString();
        }
    }

    static class CalendarCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            if (value instanceof CalendarDayCell) {
                label.setText(((CalendarDayCell) value).toString());
                label.setHorizontalAlignment(SwingConstants.LEFT);
                label.setVerticalAlignment(SwingConstants.TOP);
                if (!((CalendarDayCell) value).payments.isEmpty()) label.setBackground(new Color(230, 255, 230));
                else label.setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
            } else if (value == null) { label.setText(""); label.setBackground(Color.LIGHT_GRAY); }
            return label;
        }
    }
}