import java.sql.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.InputMismatchException;
import java.util.Scanner;

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
            System.out.println("7. Exit");
            System.out.print("Enter your choice: ");

            int choice = readInt();
            switch (choice) {
                case 1 -> createAccount();
                case 2 -> deposit();
                case 3 -> withdraw();
                case 4 -> checkBalance();
                case 5 -> transfer();
                case 6 -> viewMiniStatement();
                case 7 -> {
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
            System.out.print("Enter Full Name: ");
            String name = sc.nextLine().trim();
            if (name.isEmpty()) {
                System.out.println("❌ Name cannot be empty!");
                return;
            }

            System.out.print("Set a 4-digit PIN: ");
            String pin = sc.nextLine().trim();
            if (!pin.matches("\\d{4}")) {
                System.out.println("❌ PIN must be exactly 4 digits!");
                return;
            }

            String lastAccNo = getLastAccountNumber(conn);
            String newAccNo = generateNextAccountNumber(lastAccNo);

            String sql = "INSERT INTO accounts (account_number, holder_name, pin, balance, created_at) VALUES (?, ?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, newAccNo);
                ps.setString(2, name);
                ps.setString(3, pin);
                ps.setBigDecimal(4, BigDecimal.ZERO);
                ps.setTimestamp(5, Timestamp.valueOf(LocalDateTime.now()));
                ps.executeUpdate();
            }

            System.out.println("\n✅ Account created successfully!");
            System.out.println("Your Account Number is: " + newAccNo);

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

        System.out.print("Enter PIN: ");
        String pin = sc.nextLine().trim();

        if (!authenticate(acc, pin)) {
            System.out.println("❌ Incorrect PIN!");
            return;
        }

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

        System.out.print("Enter PIN: ");
        String pin = sc.nextLine().trim();

        if (!authenticate(acc, pin)) {
            System.out.println("❌ Incorrect PIN!");
            return;
        }

        System.out.print("Enter amount to withdraw: ");
        BigDecimal amount = readAmount();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            System.out.println("❌ Invalid amount!");
            return;
        }

        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD)) {
            BigDecimal balance = getBalance(conn, acc);
            if (balance == null) {
                System.out.println("❌ Account not found!");
                return;
            }
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

        System.out.print("Enter PIN: ");
        String pin = sc.nextLine().trim();

        if (!authenticate(fromAcc, pin)) {
            System.out.println("❌ Incorrect PIN!");
            return;
        }

        System.out.print("Enter Recipient Account Number: ");
        String toAcc = sc.nextLine().trim();

        if (!accountExists(toAcc)) {
            System.out.println("❌ Recipient account not found!");
            return;
        }

        if (fromAcc.equals(toAcc)) {
            System.out.println("❌ Cannot transfer to the same account!");
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
            if (balance == null || balance.compareTo(amount) < 0) {
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

        System.out.print("Enter PIN: ");
        String pin = sc.nextLine().trim();

        if (!authenticate(acc, pin)) {
            System.out.println("❌ Incorrect PIN!");
            return;
        }

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

        System.out.print("Enter PIN: ");
        String pin = sc.nextLine().trim();

        if (!authenticate(acc, pin)) {
            System.out.println("❌ Incorrect PIN!");
            return;
        }

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
        return null;
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
