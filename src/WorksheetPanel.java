import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class WorksheetPanel extends JPanel {

    private final JTextField dateField =
        new JTextField(LocalDate.now().toString(), 12);

    private final JLabel statusLabel =
        new JLabel("Load a worksheet to begin.");

    private final List<AccountRow> accounts = new ArrayList<>();

    private LocalDate loadedDate;
    private boolean updating = false;
    private boolean dirty = false;

    private static final String[] HEADERS = {
        "Account Name",
        "Worksheet Role",
        "Trial Balance Dr",
        "Trial Balance Cr",
        "Adjustments Dr",
        "Adjustments Cr",
        "Adjusted TB Dr",
        "Adjusted TB Cr",
        "Income Statement Dr",
        "Income Statement Cr",
        "Owner's Equity Dr",
        "Owner's Equity Cr",
        "Balance Sheet Dr",
        "Balance Sheet Cr"
    };

    private final DefaultTableModel model =
        new DefaultTableModel(HEADERS, 0) {

            @Override
            public boolean isCellEditable(int row, int column) {
                return row < accounts.size()
                    && (column == 1 || column == 4 || column == 5);
            }

            @Override
            public Class<?> getColumnClass(int column) {
                return column < 2 ? String.class : BigDecimal.class;
            }
        };

    private final JTable table = new JTable(model);

    public WorksheetPanel() {
        setLayout(new BorderLayout(0, 15));
        setBackground(Color.WHITE);
        setBorder(
            BorderFactory.createEmptyBorder(25, 25, 25, 25)
        );

        JLabel title = new JLabel("12-Column Accounting Worksheet");
        title.setFont(new Font("Arial", Font.BOLD, 22));
        title.setForeground(new Color(15, 70, 125));

        JButton loadButton = new JButton("Load");
        JButton calculateButton = new JButton("Calculate");
        JButton saveButton = new JButton("Save Draft");
        JButton exportButton = new JButton("Export CSV");

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        controls.setOpaque(false);
        controls.add(new JLabel("As of (YYYY-MM-DD):"));
        controls.add(dateField);
        controls.add(loadButton);
        controls.add(calculateButton);
        controls.add(saveButton);
        controls.add(exportButton);

        JLabel note = new JLabel(
            "Draft only: adjustments do not post to the Journal or Ledger."
        );

        JPanel top = new JPanel(new BorderLayout(0, 10));
        top.setOpaque(false);
        top.add(title, BorderLayout.NORTH);
        top.add(controls, BorderLayout.CENTER);
        top.add(note, BorderLayout.SOUTH);

        table.setRowHeight(28);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.getTableHeader().setReorderingAllowed(false);
        table.putClientProperty("terminateEditOnFocusLost", true);

        table.getColumnModel().getColumn(0).setPreferredWidth(240);
        table.getColumnModel().getColumn(1).setPreferredWidth(150);

        for (int c = 2; c < HEADERS.length; c++) {
            table.getColumnModel().getColumn(c).setPreferredWidth(155);
        }

        JComboBox<String> roles = new JComboBox<>(
            new String[] {
                "ASSET", "LIABILITY", "CAPITAL",
                "DRAWINGS", "REVENUE", "EXPENSE"
            }
        );

        table.getColumnModel().getColumn(1)
            .setCellEditor(new DefaultCellEditor(roles));

        DefaultTableCellRenderer moneyRenderer =
            new DefaultTableCellRenderer() {
                @Override
                public Component getTableCellRendererComponent(
                    JTable source,
                    Object value,
                    boolean selected,
                    boolean focused,
                    int row,
                    int column
                ) {
                    super.getTableCellRendererComponent(
                        source, value, selected, focused, row, column
                    );

                    setHorizontalAlignment(SwingConstants.RIGHT);

                    if (value instanceof BigDecimal) {
                        BigDecimal amount = (BigDecimal) value;
                        setText(
                            amount.signum() == 0
                                ? "-"
                                : amount.setScale(2).toPlainString()
                        );
                    } else {
                        setText("");
                    }

                    boolean totalRow = row >= accounts.size();

                    setFont(source.getFont().deriveFont(
                        totalRow ? Font.BOLD : Font.PLAIN
                    ));

                    if (!selected) {
                        setForeground(Color.DARK_GRAY);

                        setBackground(
                            totalRow
                                ? new Color(220, 231, 243)
                                : column == 4 || column == 5
                                    ? new Color(255, 248, 220)
                                    : Color.WHITE
                        );
                    }

                    return this;
                }
            };

        for (int c = 2; c < HEADERS.length; c++) {
            table.getColumnModel().getColumn(c)
                .setCellRenderer(moneyRenderer);
        }

        model.addTableModelListener(event -> {
            if (!updating && event.getFirstRow() >= 0
                    && event.getFirstRow() < accounts.size()) {
                dirty = true;
                statusLabel.setText(
                    "Unsaved changes. Click Calculate, then Save Draft."
                );
            }
        });

        loadButton.addActionListener(event -> loadWorksheet());

        calculateButton.addActionListener(event -> {
            if (finishEditing()) {
                calculate();
            }
        });

        saveButton.addActionListener(event -> saveDraft());

        exportButton.addActionListener(event -> {
            if (!finishEditing() || !calculate()) {
                return;
            }

            CsvExporter.export(
                this,
                table,
                "worksheet_" + loadedDate + ".csv"
            );
        });

        add(top, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(statusLabel, BorderLayout.SOUTH);
    }

    public void loadWorksheet() {
        if (!finishEditing()) {
            return;
        }

        if (dirty) {
            int answer = JOptionPane.showConfirmDialog(
                this,
                "Discard unsaved worksheet changes and reload?",
                "Reload Worksheet",
                JOptionPane.YES_NO_OPTION
            );

            if (answer != JOptionPane.YES_OPTION) {
                return;
            }
        }

        LocalDate reportDate;

        try {
            reportDate = LocalDate.parse(dateField.getText().trim());
        } catch (Exception exception) {
            warn("Enter a valid date using YYYY-MM-DD.");
            return;
        }

        List<AccountRow> loaded = new ArrayList<>();

        try (Connection connection = Database.connect()) {
            createDraftTable(connection);

            String accountSql = """
                SELECT id, code, name, type
                FROM accounts
                ORDER BY code, id
                """;

            try (
                PreparedStatement statement =
                    connection.prepareStatement(accountSql);
                ResultSet result = statement.executeQuery()
            ) {
                while (result.next()) {
                    String type = result.getString("type");

                    loaded.add(new AccountRow(
                        result.getInt("id"),
                        result.getString("code") + " - "
                            + result.getString("name"),
                        type,
                        type.equals("EQUITY") ? "CAPITAL" : type
                    ));
                }
            }

            String balanceSql = """
                SELECT j.account_id, j.debit_centavos, j.credit_centavos
                FROM journal_lines j
                JOIN transactions t ON t.id = j.transaction_id
                WHERE t.transaction_date <= ?
                """;

            try (
                PreparedStatement statement =
                    connection.prepareStatement(balanceSql)
            ) {
                statement.setString(1, reportDate.toString());

                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        AccountRow account = findAccount(
                            loaded, result.getInt("account_id")
                        );

                        if (account != null) {
                            account.balance = account.balance
                                .add(BigDecimal.valueOf(
                                    result.getLong("debit_centavos"), 2
                                ))
                                .subtract(BigDecimal.valueOf(
                                    result.getLong("credit_centavos"), 2
                                ));
                        }
                    }
                }
            }

            String draftSql = """
                SELECT account_id, role, debit_centavos, credit_centavos
                FROM worksheet_drafts
                WHERE report_date = ?
                """;

            try (
                PreparedStatement statement =
                    connection.prepareStatement(draftSql)
            ) {
                statement.setString(1, reportDate.toString());

                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        AccountRow account = findAccount(
                            loaded, result.getInt("account_id")
                        );

                        if (account != null) {
                            account.role = result.getString("role");
                            account.adjustmentDebit = BigDecimal.valueOf(
                                result.getLong("debit_centavos"), 2
                            );
                            account.adjustmentCredit = BigDecimal.valueOf(
                                result.getLong("credit_centavos"), 2
                            );
                        }
                    }
                }
            }

        } catch (SQLException exception) {
            error(exception);
            return;
        }

        accounts.clear();
        accounts.addAll(loaded);
        loadedDate = reportDate;

        updating = true;

        try {
            model.setRowCount(0);

            for (AccountRow account : accounts) {
                Object[] row = emptyRow(account.name);
                row[1] = account.role;

                putBalance(row, 2, account.balance);

                row[4] = account.adjustmentDebit;
                row[5] = account.adjustmentCredit;

                model.addRow(row);
            }
        } finally {
            updating = false;
        }

        dirty = false;
        calculate();
    }

    private boolean calculate() {
        if (loadedDate == null) {
            warn("Click Load first.");
            return false;
        }

        List<Object[]> calculated = new ArrayList<>();
        BigDecimal[] totals = new BigDecimal[HEADERS.length];

        for (int c = 2; c < totals.length; c++) {
            totals[c] = zero();
        }

        try {
            for (int r = 0; r < accounts.size(); r++) {
                AccountRow account = accounts.get(r);

                String role = String.valueOf(model.getValueAt(r, 1));
                validateRole(account.type, role);

                BigDecimal adjustmentDebit =
                    readAmount(model.getValueAt(r, 4));

                BigDecimal adjustmentCredit =
                    readAmount(model.getValueAt(r, 5));

                Object[] row = emptyRow(account.name);
                row[1] = role;

                putBalance(row, 2, account.balance);

                row[4] = adjustmentDebit;
                row[5] = adjustmentCredit;

                BigDecimal adjusted = account.balance
                    .add(adjustmentDebit)
                    .subtract(adjustmentCredit);

                putBalance(row, 6, adjusted);

                switch (role) {
                    case "REVENUE":
                    case "EXPENSE":
                        putBalance(row, 8, adjusted);
                        break;

                    case "CAPITAL":
                    case "DRAWINGS":
                        putBalance(row, 10, adjusted);
                        break;

                    default:
                        putBalance(row, 12, adjusted);
                }

                for (int c = 2; c < row.length; c++) {
                    totals[c] = totals[c].add((BigDecimal) row[c]);
                }

                calculated.add(row);
            }

        } catch (IllegalArgumentException exception) {
            clearCalculatedResults();
            warn(exception.getMessage());
            return false;
        }

        // Revenue credit less expense debit.
        BigDecimal netIncome = totals[9].subtract(totals[8]);

        // Capital credits less drawings/debits, plus profit or loss.
        BigDecimal endingCapital = totals[11]
            .subtract(totals[10])
            .add(netIncome);

        Object[] totalRow = emptyRow("TOTAL");

        for (int c = 2; c < totals.length; c++) {
            totalRow[c] = totals[c];
        }

        Object[] incomeRow = emptyRow(
            netIncome.signum() >= 0 ? "NET INCOME" : "NET LOSS"
        );

        // Balance the Income Statement pair.
        if (netIncome.signum() >= 0) {
            incomeRow[8] = netIncome;
            incomeRow[11] = netIncome;
        } else {
            incomeRow[9] = netIncome.abs();
            incomeRow[10] = netIncome.abs();
        }

        Object[] capitalRow = emptyRow("ENDING OWNER'S CAPITAL");

        if (endingCapital.signum() >= 0) {
            capitalRow[10] = endingCapital;
            capitalRow[13] = endingCapital;
        } else {
            capitalRow[11] = endingCapital.abs();
            capitalRow[12] = endingCapital.abs();
        }

        Object[] grandRow = emptyRow("GRAND TOTAL");

        for (int c = 2; c < totals.length; c++) {
            grandRow[c] = totals[c]
                .add((BigDecimal) incomeRow[c])
                .add((BigDecimal) capitalRow[c]);
        }

        updating = true;

        try {
            model.setRowCount(0);

            for (Object[] row : calculated) {
                model.addRow(row);
            }

            model.addRow(totalRow);
            model.addRow(incomeRow);
            model.addRow(capitalRow);
            model.addRow(grandRow);

        } finally {
            updating = false;
        }

        boolean trialBalanced = totals[2].compareTo(totals[3]) == 0;
        boolean adjustmentsBalanced =
            totals[4].compareTo(totals[5]) == 0;

        boolean adjustedBalanced = totals[6].compareTo(totals[7]) == 0;

        statusLabel.setForeground(
            trialBalanced && adjustmentsBalanced && adjustedBalanced
                ? Color.DARK_GRAY : Color.RED
        );

        statusLabel.setText(
            "<html>As of " + loadedDate
                + " | Trial Balance: "
                + (trialBalanced ? "Balanced" : "NOT BALANCED")
                + " | Adjustments: "
                + (adjustmentsBalanced ? "Balanced" : "NOT BALANCED")
                + "<br>Adjusted Trial Balance: "
                + (adjustedBalanced ? "Balanced" : "NOT BALANCED")
                + " | " + (dirty ? "Unsaved draft" : "Draft loaded")
                + "</html>"
        );

        return true;
    }

    private void saveDraft() {
        if (!finishEditing() || !calculate()) {
            return;
        }

        try (Connection connection = Database.connect()) {
            createDraftTable(connection);
            connection.setAutoCommit(false);

            try {
                String sql = """
                    INSERT INTO worksheet_drafts (
                        report_date, account_id, role,
                        debit_centavos, credit_centavos
                    ) VALUES (?, ?, ?, ?, ?)
                    ON CONFLICT(report_date, account_id)
                    DO UPDATE SET
                        role = excluded.role,
                        debit_centavos = excluded.debit_centavos,
                        credit_centavos = excluded.credit_centavos
                    """;

                try (
                    PreparedStatement statement =
                        connection.prepareStatement(sql)
                ) {
                    for (int r = 0; r < accounts.size(); r++) {
                        statement.setString(1, loadedDate.toString());
                        statement.setInt(2, accounts.get(r).id);
                        statement.setString(
                            3, model.getValueAt(r, 1).toString()
                        );

                        statement.setLong(
                            4,
                            readAmount(model.getValueAt(r, 4))
                                .movePointRight(2).longValueExact()
                        );

                        statement.setLong(
                            5,
                            readAmount(model.getValueAt(r, 5))
                                .movePointRight(2).longValueExact()
                        );

                        statement.executeUpdate();
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
            error(exception);
            return;
        }

        dirty = false;
        calculate();

        JOptionPane.showMessageDialog(
            this,
            "Draft saved for " + loadedDate
                + ".\nAdjustments have not been posted."
        );
    }

    private void createDraftTable(Connection connection)
            throws SQLException {

        String sql = """
            CREATE TABLE IF NOT EXISTS worksheet_drafts (
                report_date TEXT NOT NULL,
                account_id INTEGER NOT NULL,
                role TEXT NOT NULL,
                debit_centavos INTEGER NOT NULL DEFAULT 0
                    CHECK (debit_centavos >= 0),
                credit_centavos INTEGER NOT NULL DEFAULT 0
                    CHECK (credit_centavos >= 0),
                PRIMARY KEY (report_date, account_id),
                FOREIGN KEY (account_id) REFERENCES accounts(id)
            )
            """;

        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    private BigDecimal readAmount(Object value) {
        try {
            String text = value == null ? "" : value.toString().trim();

            BigDecimal amount = text.isEmpty()
                ? zero()
                : new BigDecimal(text)
                    .setScale(2, RoundingMode.UNNECESSARY);

            if (amount.signum() < 0) {
                throw new IllegalArgumentException();
            }

            amount.movePointRight(2).longValueExact();
            return amount;

        } catch (Exception exception) {
            throw new IllegalArgumentException(
                "Adjustments must be nonnegative amounts"
                    + " with at most two decimal places."
            );
        }
    }

    private void validateRole(String type, String role) {
        boolean valid = type.equals("EQUITY")
            ? role.equals("CAPITAL") || role.equals("DRAWINGS")
            : type.equals(role);

        if (!valid) {
            throw new IllegalArgumentException(
                "Worksheet role must match the account type."
                    + "\nEQUITY accounts can be CAPITAL or DRAWINGS."
            );
        }
    }

    private boolean finishEditing() {
        if (table.isEditing()
                && !table.getCellEditor().stopCellEditing()) {
            warn("Finish entering a valid cell value first.");
            return false;
        }

        return true;
    }

    private void clearCalculatedResults() {
        updating = true;

        try {
            while (model.getRowCount() > accounts.size()) {
                model.removeRow(model.getRowCount() - 1);
            }

            for (int r = 0; r < accounts.size(); r++) {
                for (int c = 6; c < HEADERS.length; c++) {
                    model.setValueAt(null, r, c);
                }
            }
        } finally {
            updating = false;
        }

        statusLabel.setText("Fix the input and calculate again.");
        statusLabel.setForeground(Color.RED);
    }

    private static Object[] emptyRow(String label) {
        Object[] row = new Object[HEADERS.length];
        row[0] = label;
        row[1] = "";

        for (int c = 2; c < row.length; c++) {
            row[c] = zero();
        }

        return row;
    }

    private static void putBalance(
        Object[] row, int debitColumn, BigDecimal balance
    ) {
        row[debitColumn] = balance.signum() > 0
            ? balance : zero();

        row[debitColumn + 1] = balance.signum() < 0
            ? balance.abs() : zero();
    }

    private static AccountRow findAccount(
        List<AccountRow> rows, int id
    ) {
        for (AccountRow row : rows) {
            if (row.id == id) {
                return row;
            }
        }

        return null;
    }

    private static BigDecimal zero() {
        return new BigDecimal("0.00");
    }

    private void warn(String message) {
        JOptionPane.showMessageDialog(
            this, message, "Check Worksheet",
            JOptionPane.WARNING_MESSAGE
        );
    }

    private void error(SQLException exception) {
        exception.printStackTrace();

        JOptionPane.showMessageDialog(
            this,
            "Worksheet operation failed:\n" + exception.getMessage(),
            "Database Error",
            JOptionPane.ERROR_MESSAGE
        );
    }

    private static class AccountRow {
        final int id;
        final String name;
        final String type;

        String role;
        BigDecimal balance = zero();
        BigDecimal adjustmentDebit = zero();
        BigDecimal adjustmentCredit = zero();

        AccountRow(int id, String name, String type, String role) {
            this.id = id;
            this.name = name;
            this.type = type;
            this.role = role;
        }
    }
}