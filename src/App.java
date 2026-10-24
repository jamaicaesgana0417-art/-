import javax.swing.*;
import java.awt.*;
import java.sql.SQLException;

public class App {

    private static final Color BLUE = new Color(15, 70, 125);

    public static void main(String[] args) {
        try {
            Database.initialize();
            AuthService.initialize();

            SwingUtilities.invokeLater(() ->
                new LoginFrame().setVisible(true)
            );

        } catch (SQLException exception) {
            exception.printStackTrace();

            SwingUtilities.invokeLater(() ->
                JOptionPane.showMessageDialog(
                    null,
                    "Cannot initialize the database:\n"
                        + exception.getMessage(),
                    "Database Error",
                    JOptionPane.ERROR_MESSAGE
                )
            );
        }
    }

    // Called by LoginFrame after successful authentication.
    public static void openAccountingWindow(String username) {
        JFrame window = new JFrame(
            "Accounting System - " + username
        );

        window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        window.setSize(1300, 750);
        window.setMinimumSize(new Dimension(950, 600));
        window.setLocationRelativeTo(null);
        window.setLayout(new BorderLayout());

        // Sidebar
        JPanel sidebar = new JPanel(new BorderLayout(0, 25));
        sidebar.setBackground(BLUE);
        sidebar.setPreferredSize(new Dimension(210, 0));
        sidebar.setBorder(
            BorderFactory.createEmptyBorder(25, 12, 20, 12)
        );

        JLabel title = new JLabel(
            "<html><center>Accounting<br>System</center></html>",
            SwingConstants.CENTER
        );

        title.setForeground(Color.WHITE);
        title.setFont(new Font("Arial", Font.BOLD, 22));

        sidebar.add(title, BorderLayout.NORTH);

        // Screen container
        CardLayout cardLayout = new CardLayout();
        JPanel content = new JPanel(cardLayout);

        // Create each screen once.
        AccountsPanel accountsPanel = new AccountsPanel();

        NewTransactionPanel transactionPanel =
            new NewTransactionPanel();

        TransactionsPanel transactionsPanel =
            new TransactionsPanel();

        JournalPanel journalPanel =
            new JournalPanel();

        LedgerPanel ledgerPanel =
            new LedgerPanel();

        BalanceSheetPanel balanceSheetPanel =
            new BalanceSheetPanel();

        WorksheetPanel worksheetPanel =
            new WorksheetPanel();

        // Register screens.
        content.add(transactionPanel, "New Transaction");
        content.add(transactionsPanel, "Transactions");
        content.add(accountsPanel, "Accounts");
        content.add(journalPanel, "General Journal");
        content.add(ledgerPanel, "General Ledger");
        content.add(balanceSheetPanel, "Balance Sheet");
        content.add(worksheetPanel, "12-Column Worksheet");

        // Navigation
        JPanel navigation = new JPanel(
            new GridLayout(0, 1, 0, 10)
        );
        navigation.setOpaque(false);

        String[] screens = {
            "New Transaction",
            "Transactions",
            "Accounts",
            "General Journal",
            "General Ledger",
            "Balance Sheet",
            "12-Column Worksheet"
        };

        for (String screenName : screens) {
            JButton button = new JButton(screenName);

            button.setFont(new Font("Arial", Font.PLAIN, 13));
            button.setFocusPainted(false);

            button.addActionListener(event -> {
                switch (screenName) {
                    case "New Transaction":
                        transactionPanel.refreshAccounts();
                        break;

                    case "Transactions":
                        transactionsPanel.refreshTransactions();
                        break;

                    case "Accounts":
                        accountsPanel.refreshAccounts();
                        break;

                    case "General Journal":
                        journalPanel.refreshJournal();
                        break;

                    case "General Ledger":
                        ledgerPanel.refreshAccounts();
                        break;

                    case "Balance Sheet":
                        balanceSheetPanel.refreshBalanceSheet();
                        break;

                    case "12-Column Worksheet":
                        /*
                         * Keep the worksheet's current edits.
                         * Use its Load button to load or reload data.
                         */
                        break;
                }

                cardLayout.show(content, screenName);
            });

            navigation.add(button);
        }

        JPanel navigationContainer = new JPanel(
            new BorderLayout()
        );
        navigationContainer.setOpaque(false);
        navigationContainer.add(
            navigation,
            BorderLayout.NORTH
        );

        sidebar.add(
            navigationContainer,
            BorderLayout.CENTER
        );

        // User information and logout
        JPanel userArea = new JPanel(
            new GridLayout(0, 1, 0, 10)
        );
        userArea.setOpaque(false);

        JLabel userLabel = new JLabel(
            "User: " + username,
            SwingConstants.CENTER
        );

        userLabel.setForeground(Color.WHITE);
        userLabel.setFont(new Font("Arial", Font.PLAIN, 12));

        JButton logoutButton = new JButton("Logout");
        logoutButton.setFocusPainted(false);

        logoutButton.addActionListener(event -> {
            int answer = JOptionPane.showConfirmDialog(
                window,
                "Log out?"
                    + "\nSave any worksheet draft changes first.",
                "Confirm Logout",
                JOptionPane.YES_NO_OPTION
            );

            if (answer == JOptionPane.YES_OPTION) {
                new LoginFrame().setVisible(true);
                window.dispose();
            }
        });

        userArea.add(userLabel);
        userArea.add(logoutButton);

        sidebar.add(userArea, BorderLayout.SOUTH);

        window.add(sidebar, BorderLayout.WEST);
        window.add(content, BorderLayout.CENTER);

        cardLayout.show(content, "Accounts");
        window.setVisible(true);
    }
}