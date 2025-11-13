🏦 NEXA Bank – Smart Banking System

A Java-based Console Banking Application with features like account creation, login, deposits, withdrawals, transfers, PDF/CSV generation, daily withdrawal limits, admin dashboard, 2-Factor Authentication (OTP), and SMS/Email notifications.

📌 Features
👤 Customer Features

✔ Create Savings / Current / Salary Account
✔ Secure Login with:

PIN Verification

2-Factor Authentication (Email/SMS OTP)
✔ Deposit Money
✔ Withdraw Money
✔ Transfer Money with alerts
✔ Check Balance
✔ Mini Statement
✔ Download:

CSV Transaction History

PDF Account Statement
✔ Change PIN
✔ Update Email
✔ Update Phone Number
✔ Set Daily Withdrawal Limit

🛠️ Admin Features

✔ View All Accounts
✔ Search Accounts (by Number / Name)
✔ View All Transactions
✔ Freeze Account
✔ Unfreeze Account
✔ Close Account
✔ Export ALL transactions to CSV
✔ Secure Admin Login

📁 Project Structure
SmartBankSystem/
│
├── src/
│   ├── SmartBankApp.java        # Main banking application
│   ├── other helper classes     # (if any)
│
├── database/
│   ├── accounts_table.sql
│   ├── transactions_table.sql
│
├── generated_files/
│   ├── ACCNO_transactions.csv
│   ├── ACCNO_statement.pdf
│
└── README.md

🗄️ Database Schema
Accounts Table
Column	Type	Description
account_number	VARCHAR	PK
holder_name	VARCHAR	Account Holder Name
pin	VARCHAR	SHA-256 Encrypted PIN
balance	DECIMAL	Current Balance
account_type	VARCHAR	Savings / Current / Salary
email	VARCHAR	User Email
phone_number	VARCHAR	User Phone
daily_limit	DECIMAL	Daily Withdrawal Limit
account_status	VARCHAR	ACTIVE / FROZEN / CLOSED
created_at	TIMESTAMP	Creation Time
branch_name	VARCHAR	Branch Name
ifsc_code	VARCHAR	Bank IFSC Code
Transactions Table
Column	Type	Description
tx_id	INT (PK)	Transaction ID
tx_code	VARCHAR	Unique Transaction Code
tx_type	VARCHAR	DEPOSIT / WITHDRAW / TRANSFER
from_account	VARCHAR	Sender Account
to_account	VARCHAR	Receiver Account
amount	DECIMAL	Amount
tx_time	TIMESTAMP	Transaction Time
🔐 Security Features

✔ PIN stored using SHA-256 hashing
✔ Automatic lockout after 3 failed PIN attempts
✔ Optional Email or SMS OTP
✔ OTP Expiry Time: 60 seconds
✔ Masked Email & Phone Number display
✔ Admin-secured dashboard
✔ Secrets removed from code (recommended via environment variables)

🔧 Technologies Used

Java

MySQL

JavaMail (Jakarta Mail)

Twilio SMS API

iText PDF Library

JDBC
