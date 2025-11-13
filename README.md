🏦 NEXA Bank – Smart Banking System

A Java-based console banking application with secure authentication, account management, transactions, PDF/CSV export, and an admin dashboard.

🚀 Features
👤 Customer

Create Savings / Current / Salary Account

Secure Login (PIN + 2FA OTP)

Deposit / Withdraw (with daily limits)

Money Transfer

Check Balance

Mini Statement

Download CSV & PDF

Update Email / Phone

Change PIN

View Account Details

🛠️ Admin

View all accounts

Search accounts

View all transactions

Freeze / Unfreeze / Close accounts

Export all transactions (CSV)

🔐 Security

SHA-256 PIN hashing

OTP verification (Email/SMS)

OTP expiry (60 sec)

3-attempt lockout

SQL-safe operations

🛠 Tech Stack

Java 17

MySQL (JDBC)

JavaMail API

Twilio SMS API

iTextPDF

SHA-256 security

⚙️ How to Run

Import project into IntelliJ/Eclipse

Add libraries: MySQL Connector, JavaMail, Twilio, iTextPDF

Create MySQL DB bankdb

Update DB, Email, Twilio credentials

Run SmartBankApp.java

📁 Tables

accounts – customer info, PIN hash, balance, daily_limit, branch
transactions – all deposits, withdrawals, transfers
