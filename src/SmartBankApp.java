import java.sql.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.io.FileWriter;
import java.io.IOException;
import java.util.InputMismatchException;
import java.util.Scanner;
import java.util.regex.Pattern;

public class SmartBankApp {
    private static final String URL = "jdbc:mysql://localhost:3306/testdb";
    private static final String USER = "root";
    private static final String PASSWORD = "Rithikaa@2006";
    private static final Scanner sc = new Scanner(System.in);

    public static void main(String[] args) {
        while (true) {
            System.out.println("\n====== 💳 SMART BANK SYSTEM ======");
            System.out.println("1. Create New Account");
            System.out.println("2. Deposit Money");
            System.out.println("3. Withdraw Money");
            System.out.println("4. Check Balance");
            System.out.println("5. Transfer Funds");
            System.out.println("6. View Mini Statement");
            System.out.println("7. Download Transaction History (CSV)");
            System.out.println("8. Exit");
            System.out.print("Enter your choice: ");

            int choice = readInt();
            switch (choice) {
                case 1 -> createAccount();
                case 2 -> deposit();
                case 3 -> withdraw();
                case 4 -> checkBalance();
                case 5 -> transfer();
                case 6 -> viewMiniStatement();
                case 7 -> downloadTransactions();
                case 8 -> {
                    System.out.println("🙏 Thank you for using Smart Bank!");
                    System.exit(0);
                }
                default -> System.out.println("❌ Invalid choice. Try again!");
            }
        }
    }

    // ✅ Safe integer input
    private static int readInt() {
        try {
            int val = sc.nextInt();
            sc.nextLine();
            return val;
        } catch (InputMismatchException e) {
            sc.nextLine();
            return -1;
        }
    }

    // 🏦 1. Create Account
    private static void createAccount() {
        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD)) {

            // Account type
            System.out.println("\nSelect Account Type:");
            System.out.println("1. Savings");
            System.out.println("2. Current");
            System.out.println("3. Salary");
            System.out.print("Enter your choice: ");
            int typeChoice = readInt();

            String accType = switch (typeChoice) {
                case 1 -> "Savings";
                case 2 -> "Current";
                case 3 -> "Salary";
                default -> null;
            };

            if (accType == null) {
                System.out.println("❌ Invalid account type selected!");
                return;
            }

            // Name validation
            System.out.print("Enter Full Name: ");
            String name = sc.nextLine().trim();
            if (!Pattern.matches("^[a-zA-Z ]+$", name)) {
                System.out.println("❌ Name should contain only alphabets and spaces!");
                return;
            }

            // Email validation
            System.out.print("Enter Email: ");
            String email = sc.nextLine().trim();
            if (!email.contains("@") || !email.equals(email.toLowerCase()) ||
                    !Pattern.matches("^[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,6}$", email)) {
                System.out.println("❌ Invalid email format! Use lowercase letters only and include '@'.");
                return;
            }

            // Phone number validation
            System.out.print("Enter Phone Number (10 digits): ");
            String phone = sc.nextLine().trim();
            if (!Pattern.matches("^[0-9]{10}$", phone)) {
                System.out.println("❌ Phone number must be exactly 10 digits!");
                return;
            }

            // PIN validation
            System.out.print("Set a 4-digit PIN: ");
            String pin = sc.nextLine().trim();
            if (!pin.matches("\\d{4}")) {
                System.out.println("❌ PIN must be exactly 4 digits!");
                return;
            }

            // Account number generation
            String lastAccNo = getLastAccountNumber(conn);
            String newAccNo = generateNextAccountNumber(lastAccNo);

            // SQL insert
            String sql = "INSERT INTO accounts (account_number, holder_name, pin, balance, created_at, account_type, email, phone_number) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, newAccNo);
                ps.setString(2, name);
                ps.setString(3, pin);
                ps.setBigDecimal(4, BigDecimal.ZERO);
                ps.setTimestamp(5, Timestamp.valueOf(LocalDateTime.now()));
                ps.setString(6, accType);
                ps.setString(7, email);
                ps.setString(8, phone);
                ps.executeUpdate();
            }

            System.out.println("\n✅ Account created successfully!");
            System.out.println("Account Type: " + accType);
            System.out.println("Account Number: " + newAccNo);

        } catch (SQLException e) {
            System.out.println("❌ Database Error: " + e.getMessage());
        }
    }

    private static String getLastAccountNumber(Connection conn) throws SQLException {
        String sql = "SELECT account_number FROM accounts ORDER BY created_at DESC LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return rs.getString(1);
        }
        return null;
    }

    private static String generateNextAccountNumber(String lastAccNo) {
        if (lastAccNo == null) return "ACC1001";
        int num = Integer.parseInt(lastAccNo.substring(3));
        return "ACC" + (num + 1);
    }

    // 💰 2. Deposit
    private static void deposit() {
        System.out.print("Enter Account Number: ");
        String acc = sc.nextLine().trim();

        if (!accountExists(acc)) {
            System.out.println("❌ Account not found!");
            return;
        }

        if (!validatePin(acc)) return;

        System.out.print("Enter amount to deposit: ");
        BigDecimal amount = readAmount();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            System.out.println("❌ Invalid amount!");
            return;
        }

        String sql = "UPDATE accounts SET balance = balance + ? WHERE account_number = ?";
        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setBigDecimal(1, amount);
            ps.setString(2, acc);
            ps.executeUpdate();
            recordTransaction(conn, acc, "DEPOSIT", amount);
            System.out.println("✅ ₹" + amount + " deposited successfully!");

        } catch (SQLException e) {
            System.out.println("❌ Error: " + e.getMessage());
        }
    }

    // 💸 3. Withdraw
    private static void withdraw() {
        System.out.print("Enter Account Number: ");
        String acc = sc.nextLine().trim();

        if (!accountExists(acc)) {
            System.out.println("❌ Account not found!");
            return;
        }

        if (!validatePin(acc)) return;

        System.out.print("Enter amount to withdraw: ");
        BigDecimal amount = readAmount();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            System.out.println("❌ Invalid amount!");
            return;
        }

        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD)) {
            BigDecimal balance = getBalance(conn, acc);
            if (balance.compareTo(amount) < 0) {
                System.out.println("❌ Insufficient balance!");
                return;
            }

            try (PreparedStatement ps = conn.prepareStatement("UPDATE accounts SET balance = balance - ? WHERE account_number = ?")) {
                ps.setBigDecimal(1, amount);
                ps.setString(2, acc);
                ps.executeUpdate();
                recordTransaction(conn, acc, "WITHDRAW", amount);
                System.out.println("✅ ₹" + amount + " withdrawn successfully!");
            }

        } catch (SQLException e) {
            System.out.println("❌ Error: " + e.getMessage());
        }
    }

    // 🔁 4. Transfer
    private static void transfer() {
        System.out.print("Enter Sender Account Number: ");
        String fromAcc = sc.nextLine().trim();

        if (!accountExists(fromAcc)) {
            System.out.println("❌ Sender account not found!");
            return;
        }

        if (!validatePin(fromAcc)) return;

        System.out.print("Enter Recipient Account Number: ");
        String toAcc = sc.nextLine().trim();

        if (!accountExists(toAcc)) {
            System.out.println("❌ Recipient account not found!");
            return;
        }

        if (fromAcc.equals(toAcc)) {
            System.out.println("❌ Cannot transfer to same account!");
            return;
        }

        System.out.print("Enter amount to transfer: ");
        BigDecimal amount = readAmount();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            System.out.println("❌ Invalid amount!");
            return;
        }

        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD)) {
            conn.setAutoCommit(false);

            BigDecimal balance = getBalance(conn, fromAcc);
            if (balance.compareTo(amount) < 0) {
                System.out.println("❌ Insufficient balance!");
                conn.rollback();
                return;
            }

            try (PreparedStatement deduct = conn.prepareStatement("UPDATE accounts SET balance = balance - ? WHERE account_number = ?");
                 PreparedStatement add = conn.prepareStatement("UPDATE accounts SET balance = balance + ? WHERE account_number = ?")) {
                deduct.setBigDecimal(1, amount);
                deduct.setString(2, fromAcc);
                add.setBigDecimal(1, amount);
                add.setString(2, toAcc);
                deduct.executeUpdate();
                add.executeUpdate();

                recordTransaction(conn, fromAcc, "TRANSFER-OUT", amount);
                recordTransaction(conn, toAcc, "TRANSFER-IN", amount);
                conn.commit();
                System.out.println("✅ ₹" + amount + " transferred successfully to " + toAcc);
            }

        } catch (SQLException e) {
            System.out.println("❌ Transfer Error: " + e.getMessage());
        }
    }

    // 👁️ 5. Check Balance
    private static void checkBalance() {
        System.out.print("Enter Account Number: ");
        String acc = sc.nextLine().trim();

        if (!accountExists(acc)) {
            System.out.println("❌ Account not found!");
            return;
        }

        if (!validatePin(acc)) return;

        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
             PreparedStatement ps = conn.prepareStatement("SELECT balance FROM accounts WHERE account_number = ?")) {
            ps.setString(1, acc);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                System.out.println("💰 Current Balance: ₹" + rs.getBigDecimal(1));
            }
        } catch (SQLException e) {
            System.out.println("❌ Error: " + e.getMessage());
        }
    }

    // 📜 6. Mini Statement
    private static void viewMiniStatement() {
        System.out.print("Enter Account Number: ");
        String acc = sc.nextLine().trim();

        if (!accountExists(acc)) {
            System.out.println("❌ Account not found!");
            return;
        }

        if (!validatePin(acc)) return;

        String sql = "SELECT tx_type, amount, tx_time FROM transactions WHERE account_number = ? ORDER BY tx_time DESC LIMIT 5";
        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, acc);
            ResultSet rs = ps.executeQuery();
            System.out.println("\n📜 Last 5 Transactions:");
            System.out.println("-------------------------------");
            while (rs.next()) {
                System.out.printf("%-15s ₹%-10s %s%n",
                        rs.getString("tx_type"),
                        rs.getBigDecimal("amount"),
                        rs.getTimestamp("tx_time"));
            }
        } catch (SQLException e) {
            System.out.println("❌ Error fetching statement: " + e.getMessage());
        }
    }

    // 🧾 7. Download Transactions as CSV
    private static void downloadTransactions() {
        System.out.print("Enter Account Number: ");
        String acc = sc.nextLine().trim();

        if (!accountExists(acc)) {
            System.out.println("❌ Account not found!");
            return;
        }

        if (!validatePin(acc)) return;

        String sql = "SELECT tx_type, amount, tx_time FROM transactions WHERE account_number = ? ORDER BY tx_time DESC";
        String fileName = acc + "_transactions.csv";

        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
             PreparedStatement ps = conn.prepareStatement(sql);
             FileWriter writer = new FileWriter(fileName)) {

            ps.setString(1, acc);
            ResultSet rs = ps.executeQuery();

            writer.write("Transaction Type,Amount,Date & Time\n");
            while (rs.next()) {
                writer.write(rs.getString("tx_type") + "," +
                        rs.getBigDecimal("amount") + "," +
                        rs.getTimestamp("tx_time") + "\n");
            }

            System.out.println("✅ Transactions exported successfully to file: " + fileName);

        } catch (SQLException | IOException e) {
            System.out.println("❌ Error exporting transactions: " + e.getMessage());
        }
    }

    // 🔐 PIN validation (3 attempts)
    private static boolean validatePin(String acc) {
        int attempts = 0;
        while (attempts < 3) {
            System.out.print("Enter PIN: ");
            String pin = sc.nextLine().trim();
            if (authenticate(acc, pin)) return true;
            attempts++;
            if (attempts < 3) {
                System.out.println("❌ Incorrect PIN! Try again (" + (3 - attempts) + " attempts left)");
            }
        }
        System.out.println("⚠️ Too many failed attempts. Try again later.");
        return false;
    }

    // 🔐 Utility helpers
    private static boolean accountExists(String acc) {
        String sql = "SELECT 1 FROM accounts WHERE account_number = ?";
        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, acc);
            ResultSet rs = ps.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            return false;
        }
    }

    private static boolean authenticate(String acc, String pin) {
        String sql = "SELECT 1 FROM accounts WHERE account_number = ? AND pin = ?";
        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, acc);
            ps.setString(2, pin);
            ResultSet rs = ps.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            return false;
        }
    }

    private static BigDecimal getBalance(Connection conn, String acc) throws SQLException {
        String sql = "SELECT balance FROM accounts WHERE account_number = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, acc);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getBigDecimal(1);
        }
        return BigDecimal.ZERO;
    }

    private static void recordTransaction(Connection conn, String acc, String type, BigDecimal amount) throws SQLException {
        String sql = "INSERT INTO transactions (account_number, tx_type, amount, tx_time) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, acc);
            ps.setString(2, type);
            ps.setBigDecimal(3, amount);
            ps.setTimestamp(4, Timestamp.valueOf(LocalDateTime.now()));
            ps.executeUpdate();
        }
    }

    private static BigDecimal readAmount() {
        try {
            BigDecimal val = sc.nextBigDecimal();
            sc.nextLine();
            return val;
        } catch (InputMismatchException e) {
            sc.nextLine();
            return null;
        }
    }
}
