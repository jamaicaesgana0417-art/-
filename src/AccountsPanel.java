import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class AccountsPanel extends JPanel {

    private final JTextField codeField = new JTextField();
    private final JTextField nameField = new JTextField();
    private final JTextField searchField = new JTextField(20);

    private final JComboBox<String> typeBox = new JComboBox<>(
        new String[] {
            "ASSET",
            "LIABILITY",
            "EQUITY",
            "REVENUE",
            "EXPENSE"
        }
    );

    private final JButton addButton =
        new JButton("Add Account");

    private final JButton updateButton =
        new JButton("Save Changes");

    private final JButton toggleButton =
        new JButton("Activate / Deactivate");

    private final JLabel statusLabel =
        new JLabel("Enter details to add an account.");

    private Integer selectedAccountId = null;
    private boolean refreshing = false;

    private final DefaultTableModel model =
        new DefaultTableModel(
            new String[] {
                "ID", "Code", "Account Name", "Type", "Status"
            },
            0
        ) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }

            @Override
            public Class<?> getColumnClass(int column) {
                return column == 0 ? Integer.class : String.class;
            }
        };

    private final JTable table = new JTable(model);

    private final TableRowSorter<DefaultTableModel> sorter =
        new TableRowSorter<>(model);

    public AccountsPanel() {
        setLayout(new BorderLayout(0, 20));
        setBackground(Color.WHITE);
        setBorder(
            BorderFactory.createEmptyBorder(30, 30, 30, 30)
        );

        JLabel heading = new JLabel("Chart of Accounts");
        heading.setFont(new Font("Arial", Font.BOLD, 24));
        heading.setForeground(new Color(15, 70, 125));

        // Account input form
        JPanel form = new JPanel(new GridLayout(0, 2, 12, 12));
        form.setOpaque(false);

        form.add(new JLabel("Account Code:"));
        form.add(codeField);

        form.add(new JLabel("Account Name:"));
        form.add(nameField);

        form.add(new JLabel("Account Type:"));
        form.add(typeBox);

        JButton clearButton = new JButton("Clear Selection");

        JPanel buttons = new JPanel(
            new FlowLayout(FlowLayout.LEFT)
        );

        buttons.setOpaque(false);
        buttons.add(addButton);
        buttons.add(updateButton);
        buttons.add(toggleButton);
        buttons.add(clearButton);

        JPanel formArea = new JPanel(new BorderLayout(0, 10));
        formArea.setOpaque(false);
        formArea.add(form, BorderLayout.CENTER);
        formArea.add(buttons, BorderLayout.SOUTH);

        JPanel top = new JPanel(new BorderLayout(0, 20));
        top.setOpaque(false);
        top.add(heading, BorderLayout.NORTH);
        top.add(formArea, BorderLayout.CENTER);

        // Accounts table
        table.setRowHeight(28);
        table.setFillsViewportHeight(true);
        table.setSelectionMode(
            ListSelectionModel.SINGLE_SELECTION
        );
        table.setRowSorter(sorter);
        table.getTableHeader().setReorderingAllowed(false);

        table.getColumnModel().getColumn(0).setPreferredWidth(50);
        table.getColumnModel().getColumn(1).setPreferredWidth(100);
        table.getColumnModel().getColumn(2).setPreferredWidth(280);
        table.getColumnModel().getColumn(3).setPreferredWidth(130);
        table.getColumnModel().getColumn(4).setPreferredWidth(100);

        // Search and export
        JButton clearSearchButton = new JButton("Clear Search");
        JButton exportButton = new JButton("Export CSV");

        JPanel searchBar = new JPanel(
            new FlowLayout(FlowLayout.LEFT)
        );

        searchBar.setOpaque(false);
        searchBar.add(new JLabel("Search Accounts:"));
        searchBar.add(searchField);
        searchBar.add(clearSearchButton);
        searchBar.add(exportButton);

        JPanel tableArea = new JPanel(new BorderLayout(0, 10));
        tableArea.setOpaque(false);
        tableArea.add(searchBar, BorderLayout.NORTH);
        tableArea.add(
            new JScrollPane(table),
            BorderLayout.CENTER
        );

        JButton refreshButton = new JButton("Refresh Accounts");

        JPanel footer = new JPanel(new BorderLayout(10, 0));
        footer.setOpaque(false);
        footer.add(statusLabel, BorderLayout.CENTER);
        footer.add(refreshButton, BorderLayout.EAST);

        add(top, BorderLayout.NORTH);
        add(tableArea, BorderLayout.CENTER);
        add(footer, BorderLayout.SOUTH);

        // Button actions
        addButton.addActionListener(event -> addAccount());

        updateButton.addActionListener(event -> updateAccount());

        toggleButton.addActionListener(event -> toggleAccount());

        clearButton.addActionListener(event -> clearSelection());

        refreshButton.addActionListener(event -> refreshAccounts());

        clearSearchButton.addActionListener(event ->
            searchField.setText("")
        );

        exportButton.addActionListener(event ->
            CsvExporter.export(
                this,
                table,
                "chart_of_accounts.csv"
            )
        );

        // Load the selected record into the form.
        table.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting() && !refreshing) {
                loadSelectedAccount();
            }
        });

        // Search while typing.
        searchField.getDocument().addDocumentListener(
            new DocumentListener() {
                @Override
                public void insertUpdate(DocumentEvent event) {
                    applySearch();
                }

                @Override
                public void removeUpdate(DocumentEvent event) {
                    applySearch();
                }

                @Override
                public void changedUpdate(DocumentEvent event) {
                    applySearch();
                }
            }
        );

        updateButton.setEnabled(false);
        toggleButton.setEnabled(false);

        refreshAccounts();
    }

    private void loadSelectedAccount() {
        int viewRow = table.getSelectedRow();

        if (viewRow < 0) {
            resetForm();
            return;
        }

        // Convert the visible row because the table supports sorting.
        int row = table.convertRowIndexToModel(viewRow);

        selectedAccountId = (Integer) model.getValueAt(row, 0);

        codeField.setText(model.getValueAt(row, 1).toString());
        nameField.setText(model.getValueAt(row, 2).toString());
        typeBox.setSelectedItem(model.getValueAt(row, 3));

        boolean active =
            "Active".equals(model.getValueAt(row, 4));

        addButton.setEnabled(false);
        updateButton.setEnabled(true);
        toggleButton.setEnabled(true);
        toggleButton.setText(active ? "Deactivate" : "Activate");

        statusLabel.setText(
            "Selected account: " + selectedAccountId
                + " | " + (active ? "Active" : "Inactive")
        );
    }

    private boolean validateFields() {
        if (codeField.getText().trim().isEmpty()
                || nameField.getText().trim().isEmpty()) {
            showWarning(
                "Enter an account code and account name."
            );
            return false;
        }

        return true;
    }

    private void addAccount() {
        if (!validateFields()) {
            return;
        }

        String code = codeField.getText().trim();
        String name = nameField.getText().trim();
        String type = (String) typeBox.getSelectedItem();

        String sql = """
            INSERT INTO accounts (code, name, type)
            VALUES (?, ?, ?)
            """;

        try (Connection connection = Database.connect()) {
            if (codeExists(connection, code, null)) {
                showWarning("That account code already exists.");
                return;
            }

            try (
                PreparedStatement statement =
                    connection.prepareStatement(sql)
            ) {
                statement.setString(1, code);
                statement.setString(2, name);
                statement.setString(3, type);
                statement.executeUpdate();
            }

        } catch (SQLException exception) {
            showError("Could not add the account.", exception);
            return;
        }

        refreshAccounts();
        showMessage("Account added successfully.");
    }

    private void updateAccount() {
        if (selectedAccountId == null || !validateFields()) {
            return;
        }

        int accountId = selectedAccountId;
        String code = codeField.getText().trim();
        String name = nameField.getText().trim();
        String type = (String) typeBox.getSelectedItem();

        try (Connection connection = Database.connect()) {
            connection.setAutoCommit(false);

            try {
                if (codeExists(connection, code, accountId)) {
                    connection.rollback();
                    showWarning("That account code already exists.");
                    return;
                }

                String oldType;

                try (
                    PreparedStatement statement =
                        connection.prepareStatement(
                            "SELECT type FROM accounts WHERE id = ?"
                        )
                ) {
                    statement.setInt(1, accountId);

                    try (ResultSet result = statement.executeQuery()) {
                        if (!result.next()) {
                            throw new SQLException(
                                "Account no longer exists. Refresh the list."
                            );
                        }

                        oldType = result.getString("type");
                    }
                }

                // Prevent reclassification of historical entries.
                if (!oldType.equals(type)
                        && hasPostedEntries(connection, accountId)) {
                    connection.rollback();

                    showWarning(
                        "This account has posted entries."
                            + "\nIts account type cannot be changed."
                            + "\nCreate a new account if another type is needed."
                    );
                    return;
                }

                String sql = """
                    UPDATE accounts
                    SET code = ?, name = ?, type = ?
                    WHERE id = ?
                    """;

                try (
                    PreparedStatement statement =
                        connection.prepareStatement(sql)
                ) {
                    statement.setString(1, code);
                    statement.setString(2, name);
                    statement.setString(3, type);
                    statement.setInt(4, accountId);

                    if (statement.executeUpdate() != 1) {
                        throw new SQLException(
                            "Account was not updated."
                        );
                    }
                }

                connection.commit();

            } catch (SQLException exception) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackException) {
                    exception.addSuppressed(rollbackException);
                }

                throw exception;
            }

        } catch (SQLException exception) {
            showError("Could not update the account.", exception);
            return;
        }

        refreshAccounts();
        showMessage("Account updated successfully.");
    }

    private void toggleAccount() {
        if (selectedAccountId == null) {
            return;
        }

        int viewRow = table.getSelectedRow();

        if (viewRow < 0) {
            return;
        }

        int modelRow = table.convertRowIndexToModel(viewRow);
        int accountId = selectedAccountId;

        boolean currentlyActive =
            "Active".equals(model.getValueAt(modelRow, 4));

        int newActive = currentlyActive ? 0 : 1;
        String action = currentlyActive ? "Deactivate" : "Activate";

        int answer = JOptionPane.showConfirmDialog(
            this,
            action + " this account?"
                + "\nExisting journal entries will remain saved.",
            "Confirm Account Status",
            JOptionPane.YES_NO_OPTION
        );

        if (answer != JOptionPane.YES_OPTION) {
            return;
        }

        String sql = """
            UPDATE accounts
            SET active = ?
            WHERE id = ?
            """;

        try (
            Connection connection = Database.connect();
            PreparedStatement statement =
                connection.prepareStatement(sql)
        ) {
            statement.setInt(1, newActive);
            statement.setInt(2, accountId);

            if (statement.executeUpdate() != 1) {
                throw new SQLException(
                    "Account no longer exists. Refresh the list."
                );
            }

        } catch (SQLException exception) {
            showError(
                "Could not change account status.",
                exception
            );
            return;
        }

        refreshAccounts();
        showMessage("Account status updated.");
    }

    private boolean codeExists(
        Connection connection,
        String code,
        Integer excludedId
    ) throws SQLException {

        String sql = excludedId == null
            ? "SELECT 1 FROM accounts WHERE code = ?"
            : "SELECT 1 FROM accounts WHERE code = ? AND id <> ?";

        try (
            PreparedStatement statement =
                connection.prepareStatement(sql)
        ) {
            statement.setString(1, code);

            if (excludedId != null) {
                statement.setInt(2, excludedId);
            }

            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private boolean hasPostedEntries(
        Connection connection,
        int accountId
    ) throws SQLException {

        String sql = """
            SELECT 1
            FROM journal_lines
            WHERE account_id = ?
            LIMIT 1
            """;

        try (
            PreparedStatement statement =
                connection.prepareStatement(sql)
        ) {
            statement.setInt(1, accountId);

            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    public void refreshAccounts() {
        List<Object[]> rows = new ArrayList<>();

        String sql = """
            SELECT id, code, name, type, active
            FROM accounts
            ORDER BY code
            """;

        try (
            Connection connection = Database.connect();
            PreparedStatement statement =
                connection.prepareStatement(sql);
            ResultSet result = statement.executeQuery()
        ) {
            while (result.next()) {
                rows.add(new Object[] {
                    result.getInt("id"),
                    result.getString("code"),
                    result.getString("name"),
                    result.getString("type"),
                    result.getInt("active") == 1
                        ? "Active"
                        : "Inactive"
                });
            }

        } catch (SQLException exception) {
            showError("Could not load accounts.", exception);
            return;
        }

        refreshing = true;

        try {
            clearSelection();
            model.setRowCount(0);

            for (Object[] row : rows) {
                model.addRow(row);
            }

            sorter.sort();

        } finally {
            refreshing = false;
        }

        applySearch();
    }

    private void applySearch() {
        clearSelection();

        String keyword = searchField.getText().trim();

        if (keyword.isEmpty()) {
            sorter.setRowFilter(null);
        } else {
            sorter.setRowFilter(
                RowFilter.regexFilter(
                    "(?iu)" + Pattern.quote(keyword),
                    1, 2, 3, 4
                )
            );
        }

        updateCount();
    }

    private void clearSelection() {
        table.clearSelection();
        resetForm();
        updateCount();
    }

    private void resetForm() {
        selectedAccountId = null;

        codeField.setText("");
        nameField.setText("");
        typeBox.setSelectedIndex(0);

        addButton.setEnabled(true);
        updateButton.setEnabled(false);
        toggleButton.setEnabled(false);
        toggleButton.setText("Activate / Deactivate");
    }

    private void updateCount() {
        statusLabel.setText(
            "Displayed accounts: " + table.getRowCount()
                + " of " + model.getRowCount()
                + " | Select a row to edit."
        );
    }

    private void showMessage(String message) {
        JOptionPane.showMessageDialog(this, message);
    }

    private void showWarning(String message) {
        JOptionPane.showMessageDialog(
            this,
            message,
            "Check Account",
            JOptionPane.WARNING_MESSAGE
        );
    }

    private void showError(
        String message,
        SQLException exception
    ) {
        exception.printStackTrace();

        JOptionPane.showMessageDialog(
            this,
            message + "\n" + exception.getMessage(),
            "Database Error",
            JOptionPane.ERROR_MESSAGE
        );
    }
}