import java.sql.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.io.FileWriter;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.regex.Pattern;
import jakarta.mail.*;
import jakarta.mail.internet.*;
import java.util.Properties;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
public class SmartBankApp {
    private static final String URL = "jdbc:mysql://localhost:3306/testdb";
    private static final String USER = "root";
    private static final String PASSWORD = "Rithikaa@2006";
    private static final Scanner sc = new Scanner(System.in);

    private static final String ADMIN_USER = "admin";
    private static final String ADMIN_PASS = "Admin@123";
    private static final String TWILIO_SID = "TWILIO_SID"; // your Account SID
    private static final String TWILIO_AUTH = "TWILIO_AUTH"; // your Auth Token
    private static final String TWILIO_PHONE = "+16182988289"; // your Twilio phone number
    private static final List<Branch> BRANCHES = List.of(
            new Branch("Chennai - Tambaram", "SBIN0001"),
            new Branch("Bangalore - Whitefield", "SBIN0002"),
            new Branch("Mumbai - Andheri", "SBIN0003"),
            new Branch("Delhi - Rohini", "SBIN0004")
    );

    public static void main(String[] args) {
        while (true) {
            System.out.println("\n====== 🏦 SMART BANK LOGIN ======");
            System.out.println("1. Admin Login");
            System.out.println("2. Customer Login");
            System.out.println("3. Open New Account");
            System.out.println("4. Exit");

            String choice = readValidated("Enter choice (1-4): ", "^[1-4]$", "Invalid. Enter 1-4.");
            if (choice == null) continue;

            switch (choice) {
                case "1" -> adminLogin();
                case "2" -> customerLogin();
                case "3" -> createAccount();
                case "4" -> {
                    System.out.println("🙏 Thank you for using Smart Bank!");
                    System.exit(0);
                }
            }
        }
    }

    private static String readValidated(String prompt, String regex, String err) {
        int attempts = 3;
        while (attempts-- > 0) {
            System.out.print(prompt);
            String in = sc.nextLine().trim();
            if (regex == null || Pattern.matches(regex, in)) return in;
            System.out.println(err + " (" + attempts + " left)");
        }
        System.out.println("Operation cancelled. Returning to menu...");
        return null;
    }

    private static String hashPin(String pin) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] h = md.digest(pin.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : h) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean accountExists(String acc) {
        String sql = "SELECT 1 FROM accounts WHERE account_number = ?";
        try (Connection c = DriverManager.getConnection(URL, USER, PASSWORD);
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, acc);
            return ps.executeQuery().next();
        } catch (SQLException e) { return false; }
    }

    private static boolean authenticatePin(String acc) {
        int attempts = 3;
        while (attempts-- > 0) {
            String pin = readValidated("Enter 4-digit PIN: ", "^\\d{4}$", "Invalid PIN format.");
            if (pin == null) return false;
            String sql = "SELECT 1 FROM accounts WHERE account_number = ? AND pin = ?";
            try (Connection c = DriverManager.getConnection(URL, USER, PASSWORD);
                 PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, acc);
                ps.setString(2, hashPin(pin));
                if (ps.executeQuery().next()) return true;
            } catch (SQLException ignored) {}
            System.out.println("Incorrect PIN. (" + attempts + " left)");
        }
        System.out.println("Too many attempts. Returning to menu...");
        return false;
    }

    private static String getAccountStatus(String acc) {
        String sql = "SELECT account_status FROM accounts WHERE account_number=?";
        try (Connection c = DriverManager.getConnection(URL, USER, PASSWORD);
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, acc);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getString(1) : null;
        } catch (SQLException e) { return null; }
    }

    private static boolean ensureActive(String acc) {
        String st = getAccountStatus(acc);
        if (st == null) { System.out.println("❌ Account not found."); return false; }
        if ("FROZEN".equals(st)) { System.out.println("⚠️ Account is FROZEN. Operation blocked."); return false; }
        if ("CLOSED".equals(st)) { System.out.println("⚠️ Account is CLOSED. Operation blocked."); return false; }
        return true;
    }

    private static void createAccount() {
        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD)) {

            System.out.println("\nSelect Branch:");
            for (int i = 0; i < BRANCHES.size(); i++) {
                System.out.printf("%d. %s%n", i + 1, BRANCHES.get(i).name());
            }
            String bch = readValidated("Enter (1-4): ", "^[1-4]$", "Invalid branch.");
            if (bch == null) return;
            Branch branch = BRANCHES.get(Integer.parseInt(bch) - 1);

            String t = readValidated("1.Savings  2.Current  3.Salary\nSelect Type: ", "^[1-3]$", "Invalid type.");
            if (t == null) return;
            String type = switch (t) { case "1" -> "Savings"; case "2" -> "Current"; default -> "Salary"; };

            String name = readValidated("Full Name: ", "^[a-zA-Z ]+$", "Name must be letters/spaces only.");
            if (name == null) return;
            String email = readValidated("Email (lowercase): ", "^[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,6}$", "Invalid email.");
            if (email == null) return;
            String phone = readValidated("Phone (10 digits): ", "^[0-9]{10}$", "Invalid phone.");
            if (phone == null) return;
            String pin = readValidated("Set 4-digit PIN: ", "^\\d{4}$", "Invalid PIN.");
            if (pin == null) return;

            String encPin = hashPin(pin);

            String lastAcc = getLastAccountNumber(conn);
            String accNo = generateAccNo(lastAcc, type);
            String ifsc = branch.ifscPrefix() + accNo.substring(accNo.length() - 3);

            try (PreparedStatement ps = conn.prepareStatement("""
                    INSERT INTO accounts
                    (account_number, holder_name, pin, balance, account_status, created_at,
                     account_type, email, phone_number, branch_name, ifsc_code)
                    VALUES (?, ?, ?, 0, 'ACTIVE', NOW(), ?, ?, ?, ?, ?)
                    """)) {
                ps.setString(1, accNo);
                ps.setString(2, name);
                ps.setString(3, encPin);
                ps.setString(4, type);
                ps.setString(5, email);
                ps.setString(6, phone);
                ps.setString(7, branch.name());
                ps.setString(8, ifsc);
                ps.executeUpdate();
            }

            System.out.println("\n✅ Account Created!");
            System.out.println("Account: " + accNo);
            System.out.println("Branch : " + branch.name());
            System.out.println("IFSC   : " + ifsc);

        } catch (SQLException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private static String getLastAccountNumber(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT account_number FROM accounts ORDER BY created_at DESC LIMIT 1")) {
            return rs.next() ? rs.getString(1) : null;
        }
    }

    private static String generateAccNo(String last, String type) {
        String prefix = switch (type) { case "Savings" -> "SBIN"; case "Current" -> "CURR"; default -> "SAL"; };
        int next = (last == null) ? 1001 : Integer.parseInt(last.replaceAll("\\D", "")) + 1;
        return prefix + String.format("%07d", next);
    }

    private static void customerLogin() {
        String acc = readValidated("Account Number: ", "^[A-Z]{3,4}\\d{7}$", "Invalid account no.");
        if (acc == null || !accountExists(acc)) { System.out.println("Account not found."); return; }
        if (!ensureActive(acc)) return;
        if (!authenticatePin(acc)) return;
        if (!require2FAWithChoice(acc)) return;
        customerMenu(acc);
    }

    private static void customerMenu(String acc) {
        while (true) {
            System.out.println("\n====== 👤 CUSTOMER MENU ======");
            System.out.println("1. Deposit");
            System.out.println("2. Withdraw");
            System.out.println("3. Transfer");
            System.out.println("4. Check Balance");
            System.out.println("5. Mini Statement");
            System.out.println("6. Download CSV");
            System.out.println("7. Logout");
            System.out.println("8. Account Details"); // ✅ Added option

            String ch = readValidated("Enter (1-8): ", "^[1-8]$", "Invalid.");
            if (ch == null) return;

            switch (ch) {
                case "1" -> deposit(acc);
                case "2" -> withdraw(acc);
                case "3" -> transfer(acc);
                case "4" -> showSummary(acc);
                case "5" -> miniStatement(acc);
                case "6" -> downloadCSV(acc);
                case "7" -> { System.out.println("Logged out."); return; }
                case "8" -> showFullAccountDetails(acc); // ✅ New feature
            }
        }
    }

    // ✅ New function to show full account details
    private static void showFullAccountDetails(String acc) {
        if (!require2FAWithChoice(acc)) return;
        String sql = "SELECT * FROM accounts WHERE account_number=?";
        try (Connection c = DriverManager.getConnection(URL, USER, PASSWORD);
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, acc);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                System.out.println("\n====== 🧾 FULL ACCOUNT DETAILS ======");
                ResultSetMetaData md = rs.getMetaData();
                int cols = md.getColumnCount();
                for (int i = 1; i <= cols; i++) {
                    System.out.printf("%-20s : %s%n", md.getColumnName(i), rs.getString(i));
                }
                System.out.println("=====================================");
            } else {
                System.out.println("❌ Account not found.");
            }
        } catch (SQLException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }
    // ====== DEPOSIT / WITHDRAW / TRANSFER ======
    private static void deposit(String acc) {
        if (!ensureActive(acc)) return;
        if (!require2FAWithChoice(acc)) return; // 2FA before operation
        BigDecimal amt = askAmount("Amount to deposit: ");
        if (amt == null) return;
        try (Connection c = DriverManager.getConnection(URL, USER, PASSWORD)) {
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement("UPDATE accounts SET balance = balance + ? WHERE account_number=?")) {
                ps.setBigDecimal(1, amt);
                ps.setString(2, acc);
                ps.executeUpdate();
            }
            recordTxn(c, "DEPOSIT", null, acc, acc, amt);
            c.commit();
            System.out.println("✅ Deposit successful.");
        } catch (SQLException e) { System.out.println("Error: " + e.getMessage()); }
    }

    private static void withdraw(String acc) {
        if (!ensureActive(acc)) return;
        if (!require2FAWithChoice(acc)) return; // 2FA before operation
        BigDecimal amt = askAmount("Amount to withdraw: ");
        if (amt == null) return;
        try (Connection c = DriverManager.getConnection(URL, USER, PASSWORD)) {
            c.setAutoCommit(false);
            if (getBalance(c, acc).compareTo(amt) < 0) { System.out.println("❌ Insufficient balance."); c.rollback(); return; }
            try (PreparedStatement ps = c.prepareStatement("UPDATE accounts SET balance = balance - ? WHERE account_number=?")) {
                ps.setBigDecimal(1, amt);
                ps.setString(2, acc);
                ps.executeUpdate();
            }
            recordTxn(c, "WITHDRAW", acc, null, acc, amt);
            c.commit();
            System.out.println("✅ Withdraw successful.");
        } catch (SQLException e) { System.out.println("Error: " + e.getMessage()); }
    }

    private static void transfer(String fromAcc) {
        if (!ensureActive(fromAcc)) return;
        if (!require2FAWithChoice(fromAcc)) return; // 2FA before operation

        String toAcc = readValidated("Recipient Account Number: ", "^[A-Z]{3,4}\\d{7}$", "Invalid account no.");
        if (toAcc == null || !accountExists(toAcc)) { System.out.println("Recipient not found."); return; }
        if (!ensureActive(toAcc)) return;
        if (fromAcc.equals(toAcc)) { System.out.println("❌ Cannot transfer to same account."); return; }

        BigDecimal amt = askAmount("Amount to transfer: ");
        if (amt == null) return;

        try (Connection c = DriverManager.getConnection(URL, USER, PASSWORD)) {
            c.setAutoCommit(false);
            if (getBalance(c, fromAcc).compareTo(amt) < 0) { System.out.println("❌ Insufficient balance."); c.rollback(); return; }

            try (PreparedStatement d = c.prepareStatement("UPDATE accounts SET balance = balance - ? WHERE account_number=?");
                 PreparedStatement a = c.prepareStatement("UPDATE accounts SET balance = balance + ? WHERE account_number=?")) {
                d.setBigDecimal(1, amt); d.setString(2, fromAcc);
                a.setBigDecimal(1, amt); a.setString(2, toAcc);
                d.executeUpdate(); a.executeUpdate();
            }

            recordTxn(c, "TRANSFER", fromAcc, toAcc, fromAcc, amt);
            c.commit();
            System.out.println("✅ Transfer successful.");
        } catch (SQLException e) { System.out.println("Error: " + e.getMessage()); }
    }

    // ====== CUSTOMER UTIL ======
    private static void showSummary(String acc) {
        if (!require2FAWithChoice(acc)) return; // 2FA before viewing sensitive info
        String sql = """
            SELECT holder_name, account_number, account_type, balance, email, phone_number, branch_name, ifsc_code, account_status
            FROM accounts WHERE account_number=?
            """;
        try (Connection c = DriverManager.getConnection(URL, USER, PASSWORD);
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, acc);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                System.out.println("\n=== ACCOUNT SUMMARY ===");
                System.out.println("Name   : " + rs.getString(1));
                System.out.println("Account: " + rs.getString(2));
                System.out.println("Type   : " + rs.getString(3));
                System.out.println("Balance: ₹" + rs.getBigDecimal(4));
                System.out.println("Email  : " + rs.getString(5));
                System.out.println("Phone  : " + rs.getString(6));
                System.out.println("Branch : " + rs.getString(7));
                System.out.println("IFSC   : " + rs.getString(8));
                System.out.println("Status : " + rs.getString(9));
                System.out.println("=======================");
            }
        } catch (SQLException ignored) {}
    }

    private static void miniStatement(String acc) {
        if (!require2FAWithChoice(acc)) return; // 2FA before viewing transactions
        System.out.println("\n📜 Recent Transactions:");
        System.out.println("TX#        TX_CODE               TYPE         FROM         TO           AMOUNT     DATE");
        System.out.println("-------------------------------------------------------------------------------------------");
        String sql = """
            SELECT tx_id, tx_code, tx_type, from_account, to_account, amount, tx_time
            FROM transactions
            WHERE account_number=? OR from_account=? OR to_account=?
            ORDER BY tx_time DESC LIMIT 5
            """;
        try (Connection c = DriverManager.getConnection(URL, USER, PASSWORD);
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, acc); ps.setString(2, acc); ps.setString(3, acc);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                System.out.printf("%-10s %-20s %-12s %-12s %-12s ₹%-9s %s%n",
                        rs.getInt(1), rs.getString(2), rs.getString(3),
                        opt(rs.getString(4)), opt(rs.getString(5)),
                        rs.getBigDecimal(6), rs.getTimestamp(7));
            }
        } catch (SQLException ignored) {}
    }

    private static void downloadCSV(String acc) {
        if (!require2FAWithChoice(acc)) return; // 2FA before export
        String file = acc + "_transactions.csv";
        String sql = """
            SELECT tx_id, tx_code, tx_type, from_account, to_account, amount, tx_time
            FROM transactions
            WHERE account_number=? OR from_account=? OR to_account=?
            ORDER BY tx_time DESC
            """;
        try (Connection c = DriverManager.getConnection(URL, USER, PASSWORD);
             PreparedStatement ps = c.prepareStatement(sql);
             FileWriter fw = new FileWriter(file)) {
            ps.setString(1, acc); ps.setString(2, acc); ps.setString(3, acc);
            ResultSet rs = ps.executeQuery();
            fw.write("TX_ID,TX_CODE,TYPE,FROM,TO,AMOUNT,DATE\n");
            while (rs.next()) {
                fw.write(rs.getInt(1) + "," + rs.getString(2) + "," + rs.getString(3) + "," +
                        opt(rs.getString(4)) + "," + opt(rs.getString(5)) + "," +
                        rs.getBigDecimal(6) + "," + rs.getTimestamp(7) + "\n");
            }
            System.out.println("✅ Exported to: " + file);
        } catch (Exception e) { System.out.println("Error: " + e.getMessage()); }
    }

    private static String opt(String s) { return s == null ? "" : s; }

    private static BigDecimal askAmount(String msg) {
        String in = readValidated(msg, "^\\d+(\\.\\d{1,2})?$", "Invalid amount.");
        return in == null ? null : new BigDecimal(in);
    }

    private static BigDecimal getBalance(Connection c, String acc) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT balance FROM accounts WHERE account_number=?")) {
            ps.setString(1, acc);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getBigDecimal(1) : BigDecimal.ZERO;
        }
    }

    private static void recordTxn(Connection c, String type, String from, String to, String accRef, BigDecimal amt) throws SQLException {
        String txCode = "TXN" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) +
                "-" + (100000 + new Random().nextInt(900000));
        try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO transactions
                (tx_code, from_account, to_account, account_number, tx_type, amount, tx_time)
                VALUES (?, ?, ?, ?, ?, ?, NOW())
                """)) {
            ps.setString(1, txCode);
            ps.setString(2, from);
            ps.setString(3, to);
            ps.setString(4, accRef); // for deposits/withdrawals, keep owner account reference
            ps.setString(5, type);
            ps.setBigDecimal(6, amt);
            ps.executeUpdate();
        }
    }

    // ====== ADMIN LOGIN & MENU ======
    private static void adminLogin() {
        String u = readValidated("Admin Username: ", ".+", "Required.");
        if (u == null) return;
        String p = readValidated("Admin Password: ", ".+", "Required.");
        if (p == null) return;

        if (ADMIN_USER.equals(u) && ADMIN_PASS.equals(p)) {
            System.out.println("✅ Admin authenticated.");
            adminMenu();
        } else {
            System.out.println("❌ Invalid admin credentials.");
        }
    }

    private static void adminMenu() {
        while (true) {
            System.out.println("\n====== 🛠️ ADMIN DASHBOARD ======");
            System.out.println("1. View All Accounts");
            System.out.println("2. Search Account (by number or name)");
            System.out.println("3. View All Transactions");
            System.out.println("4. Freeze Account");
            System.out.println("5. Unfreeze Account");
            System.out.println("6. Close Account (Soft Delete → CLOSED)");
            System.out.println("7. Export ALL Transactions CSV");
            System.out.println("8. Logout");

            String ch = readValidated("Enter (1-8): ", "^[1-8]$", "Invalid.");
            if (ch == null) return;

            switch (ch) {
                case "1" -> adminViewAllAccounts();
                case "2" -> adminSearchAccount();
                case "3" -> adminViewAllTransactions();
                case "4" -> adminSetStatus("FROZEN");
                case "5" -> adminSetStatus("ACTIVE");
                case "6" -> adminSetStatus("CLOSED");
                case "7" -> adminExportAllTxns();
                case "8" -> { System.out.println("Admin logged out."); return; }
            }
        }
    }

    private static void adminViewAllAccounts() {
        String sql = "SELECT account_number, holder_name, account_type, balance, account_status, branch_name, ifsc_code FROM accounts ORDER BY created_at DESC";
        try (Connection c = DriverManager.getConnection(URL, USER, PASSWORD);
             Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            System.out.println("\nACCT_NO     HOLDER           TYPE     BALANCE     STATUS    BRANCH                 IFSC");
            System.out.println("----------------------------------------------------------------------------------------------");
            while (rs.next()) {
                System.out.printf("%-11s %-15s %-8s ₹%-10s %-8s %-22s %s%n",
                        rs.getString(1), rs.getString(2), rs.getString(3),
                        rs.getBigDecimal(4), rs.getString(5), rs.getString(6), rs.getString(7));
            }
        } catch (SQLException e) { System.out.println("Error: " + e.getMessage()); }
    }

    private static void adminSearchAccount() {
        String q = readValidated("Enter account number or name: ", ".+", "Required.");
        if (q == null) return;
        String sql = """
            SELECT account_number, holder_name, account_type, balance, account_status, branch_name, ifsc_code
            FROM accounts
            WHERE account_number = ? OR holder_name LIKE CONCAT('%',?,'%')
            """;
        try (Connection c = DriverManager.getConnection(URL, USER, PASSWORD);
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, q);
            ps.setString(2, q);
            ResultSet rs = ps.executeQuery();
            boolean any = false;
            while (rs.next()) {
                any = true;
                System.out.printf("%s | %s | %s | ₹%s | %s | %s | %s%n",
                        rs.getString(1), rs.getString(2), rs.getString(3),
                        rs.getBigDecimal(4), rs.getString(5), rs.getString(6), rs.getString(7));
            }
            if (!any) System.out.println("No results.");
        } catch (SQLException e) { System.out.println("Error: " + e.getMessage()); }
    }

    private static void adminViewAllTransactions() {
        String sql = "SELECT tx_id, tx_code, tx_type, from_account, to_account, amount, tx_time FROM transactions ORDER BY tx_time DESC LIMIT 200";
        try (Connection c = DriverManager.getConnection(URL, USER, PASSWORD);
             Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            System.out.println("\nTX_ID  TX_CODE               TYPE         FROM         TO           AMOUNT     DATE");
            System.out.println("------------------------------------------------------------------------------------");
            while (rs.next()) {
                System.out.printf("%-6d %-20s %-12s %-12s %-12s ₹%-9s %s%n",
                        rs.getInt(1), rs.getString(2), rs.getString(3),
                        opt(rs.getString(4)), opt(rs.getString(5)),
                        rs.getBigDecimal(6), rs.getTimestamp(7));
            }
        } catch (SQLException e) { System.out.println("Error: " + e.getMessage()); }
    }

    private static void adminSetStatus(String status) {
        String acc = readValidated("Account Number: ", "^[A-Z]{3,4}\\d{7}$", "Invalid.");
        if (acc == null || !accountExists(acc)) { System.out.println("Account not found."); return; }
        String sql = "UPDATE accounts SET account_status=? WHERE account_number=?";
        try (Connection c = DriverManager.getConnection(URL, USER, PASSWORD);
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setString(2, acc);
            ps.executeUpdate();
            System.out.println("✅ Status updated to: " + status);
        } catch (SQLException e) { System.out.println("Error: " + e.getMessage()); }
    }

    private static void adminExportAllTxns() {
        String file = "ALL_transactions.csv";
        String sql = "SELECT tx_id, tx_code, tx_type, from_account, to_account, amount, tx_time FROM transactions ORDER BY tx_time DESC";
        try (Connection c = DriverManager.getConnection(URL, USER, PASSWORD);
             Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql);
             FileWriter fw = new FileWriter(file)) {
            fw.write("TX_ID,TX_CODE,TYPE,FROM,TO,AMOUNT,DATE\n");
            while (rs.next()) {
                fw.write(rs.getInt(1) + "," + rs.getString(2) + "," + rs.getString(3) + "," +
                        opt(rs.getString(4)) + "," + opt(rs.getString(5)) + "," +
                        rs.getBigDecimal(6) + "," + rs.getTimestamp(7) + "\n");
            }
            System.out.println("✅ Exported to: " + file);
        } catch (Exception e) { System.out.println("Error: " + e.getMessage()); }
    }

    // -------------------- 2FA (Simulated) --------------------
    private static class ContactInfo {
        String email; String phone;
        ContactInfo(String e, String p) { email = e; phone = p; }
    }

    private static ContactInfo getContactInfo(String acc) {
        String sql = "SELECT email, phone_number FROM accounts WHERE account_number=?";
        try (Connection c = DriverManager.getConnection(URL, USER, PASSWORD);
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, acc);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return new ContactInfo(rs.getString(1), rs.getString(2));
        } catch (SQLException ignored) {}
        return null;
    }

    private static String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "unknown";
        String[] parts = email.split("@", 2);
        String user = parts[0];
        String dom  = parts[1];
        String uMask = user.length() <= 2 ? user.charAt(0) + "*" : user.charAt(0) + "***" + user.charAt(user.length()-1);
        return uMask + "@" + dom;
    }

    private static String maskPhone(String phone) {
        if (phone == null || phone.length() < 4) return "unknown";
        String last4 = phone.substring(phone.length()-4);
        return "******" + last4;
    }

    private enum OtpChannel { EMAIL, SMS }

    private static OtpChannel chooseOtpChannel(ContactInfo ci) {
        boolean hasEmail = ci.email != null && !ci.email.isBlank();
        boolean hasPhone = ci.phone != null && !ci.phone.isBlank();

        if (!hasEmail && !hasPhone) {
            System.out.println("❌ No email/phone available for 2FA.");
            return null;
        }

        if (hasEmail && hasPhone) {
            System.out.println("\nChoose OTP delivery method:");
            System.out.println("1. Email (" + maskEmail(ci.email) + ")");
            System.out.println("2. Phone (" + maskPhone(ci.phone) + ")");
            String ch = readValidated("Enter (1-2): ", "^[1-2]$", "Invalid choice.");
            if (ch == null) return null;
            return "1".equals(ch) ? OtpChannel.EMAIL : OtpChannel.SMS;
        } else if (hasEmail) {
            System.out.println("\nSending OTP to your Email (" + maskEmail(ci.email) + ")");
            return OtpChannel.EMAIL;
        } else {
            System.out.println("\nSending OTP to your Phone (" + maskPhone(ci.phone) + ")");
            return OtpChannel.SMS;
        }
    }

    // Simulated senders — replace later with JavaMail / Twilio, etc.
    private static void sendOtpEmail(String receiverEmail, int otp) {
        final String senderEmail = "smartbankinternotp@gmail.com"; // your Gmail
        final String senderPass = "iwfuwfxonhdkehha"; // your App Password (16 chars)

        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", "smtp.gmail.com");
        props.put("mail.smtp.port", "587");

        Session session = Session.getInstance(props, new Authenticator() {
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(senderEmail, senderPass);
            }
        });

        try {
            jakarta.mail.Message message = new MimeMessage(session); // ✅ Correct class
            message.setFrom(new InternetAddress(senderEmail));
            message.setRecipients(jakarta.mail.Message.RecipientType.TO, InternetAddress.parse(receiverEmail));
            message.setSubject("SmartBank OTP Verification");
            message.setText("Dear User,\n\nYour SmartBank OTP is: " + otp +
                    "\n\nIt is valid for 60 seconds.\n\n- SmartBank Security Team");

            Transport.send(message);
            System.out.println("✅ OTP sent to: " + receiverEmail);
        } catch (MessagingException e) {
            System.out.println("❌ Failed to send OTP Email: " + e.getMessage());
        }
    }



    private static void sendOtpSms(String phone, int otp) {
        try {
            // Initialize Twilio
            Twilio.init(TWILIO_SID, TWILIO_AUTH);

            String messageBody = "Your SmartBank OTP is: " + otp + "\nValid for 60 seconds.";

            // Send the SMS
            Message message = Message.creator(
                    new PhoneNumber("+919488153044"), // 👈 user’s phone number with country code
                    new PhoneNumber(TWILIO_PHONE),  // 👈 your Twilio number
                    messageBody
            ).create();

            System.out.println("✅ SMS OTP sent to: " + phone);
        } catch (Exception e) {
            System.out.println("❌ Failed to send SMS: " + e.getMessage());
        }
    }

    /**
     * Ask user to choose Email or Phone, send a single OTP to the chosen channel.
     * 60s validity; if expired, auto-regenerate (no attempt penalty for expiry);
     * Max 3 incorrect entries total.
     */
    private static boolean require2FAWithChoice(String acc) {
        ContactInfo ci = getContactInfo(acc);
        if (ci == null) { System.out.println("❌ Cannot fetch contact info for 2FA."); return false; }

        OtpChannel channel = chooseOtpChannel(ci);
        if (channel == null) return false;

        int attemptsLeft = 3;
        Random r = new Random();

        while (true) {
            int otp = 100000 + r.nextInt(900000);
            long issuedAt = System.currentTimeMillis();

            // Send to chosen channel
            if (channel == OtpChannel.EMAIL) sendOtpEmail(ci.email, otp);
            else sendOtpSms(ci.phone, otp);

            System.out.println("🔐 Enter the 6-digit OTP (valid for 60s).");

            while (true) {
                String entered = readValidated("OTP: ", "^\\d{6}$", "Invalid OTP format.");
                if (entered == null) return false;

                long seconds = (System.currentTimeMillis() - issuedAt) / 1000;
                if (seconds > 60) {
                    System.out.println("⏳ OTP expired. Generating and resending a new OTP...");
                    // regenerate and resend (no attempt penalty for expiry)
                    break; // break inner loop to regenerate
                }

                if (Integer.parseInt(entered) == otp) {
                    System.out.println("✅ 2FA verified.");
                    return true;
                }

                attemptsLeft--;
                if (attemptsLeft <= 0) {
                    System.out.println("❌ Too many incorrect OTP attempts. Action cancelled.");
                    return false;
                }
                System.out.println("❌ Incorrect OTP. (" + attemptsLeft + " attempts left)");
                // allow retry with same (still-valid) OTP
            }
            // outer loop continues to regenerate on expiry
        }
    }

    // ----- Branch record -----
    private record Branch(String name, String ifscPrefix) {}
}
