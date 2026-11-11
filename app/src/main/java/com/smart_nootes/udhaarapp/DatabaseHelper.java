
package com.smart_nootes.udhaarapp;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "SmartKhata.db";
    private static final int DATABASE_VERSION = 7;

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {

        db.execSQL("CREATE TABLE users (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "owner_name TEXT NOT NULL, " +
                "shop_name TEXT NOT NULL, " +
                "mobile TEXT UNIQUE NOT NULL, " +
                "password TEXT NOT NULL DEFAULT '', " +
                "email TEXT, " +
                "firebase_uid TEXT)");

        db.execSQL("CREATE TABLE customers (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "user_id INTEGER NOT NULL, " +
                "name TEXT NOT NULL, " +
                "mobile TEXT, " +
                "address TEXT, " +
                "created_at DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                "cloud_id TEXT, " +
                "sync_status TEXT NOT NULL DEFAULT 'PENDING')");

        db.execSQL("CREATE TABLE transactions (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "customer_id INTEGER NOT NULL, " +
                "type TEXT NOT NULL, " +
                "amount REAL NOT NULL, " +
                "description TEXT, " +
                "date DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                "cloud_id TEXT, " +
                "sync_status TEXT NOT NULL DEFAULT 'PENDING')");

        createSyncDeletionsTable(db);
    }

    private void createSyncDeletionsTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS sync_deletions (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "entity_type TEXT NOT NULL, " +
                "cloud_id TEXT NOT NULL, " +
                "parent_customer_cloud_id TEXT)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

        if (oldVersion < 3) {
            db.execSQL("CREATE TABLE IF NOT EXISTS transactions (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "customer_id INTEGER NOT NULL, " +
                    "type TEXT NOT NULL, " +
                    "amount REAL NOT NULL, " +
                    "description TEXT, " +
                    "date DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                    "cloud_id TEXT, " +
                    "sync_status TEXT NOT NULL DEFAULT 'PENDING')");
        }

        if (oldVersion < 5 && !columnExists(db, "users", "email")) {
            db.execSQL("ALTER TABLE users ADD COLUMN email TEXT");
        }

        if (oldVersion < 6) {
            if (!columnExists(db, "users", "firebase_uid")) {
                db.execSQL("ALTER TABLE users ADD COLUMN firebase_uid TEXT");
            }

            if (!columnExists(db, "customers", "cloud_id")) {
                db.execSQL("ALTER TABLE customers ADD COLUMN cloud_id TEXT");
            }

            if (!columnExists(db, "transactions", "cloud_id")) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN cloud_id TEXT");
            }
        }

        if (oldVersion < 7) {
            if (!columnExists(db, "customers", "sync_status")) {
                db.execSQL("ALTER TABLE customers ADD COLUMN " +
                        "sync_status TEXT NOT NULL DEFAULT 'PENDING'");
            }

            if (!columnExists(db, "transactions", "sync_status")) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN " +
                        "sync_status TEXT NOT NULL DEFAULT 'PENDING'");
            }
        }

        createSyncDeletionsTable(db);
    }

    private boolean columnExists(SQLiteDatabase db, String table, String column) {
        Cursor cursor = db.rawQuery("PRAGMA table_info(" + table + ")", null);

        try {
            int index = cursor.getColumnIndex("name");

            while (cursor.moveToNext()) {
                if (index >= 0 && column.equalsIgnoreCase(cursor.getString(index))) {
                    return true;
                }
            }
        } finally {
            cursor.close();
        }

        return false;
    }

    // --------------------------------------------------
    // USER / SHOP METHODS
    // --------------------------------------------------

    public boolean registerUser(String ownerName, String shopName,
                                String mobile, String email) {

        SQLiteDatabase db = getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put("owner_name", ownerName);
        values.put("shop_name", shopName);
        values.put("mobile", mobile);
        values.put("password", "");
        values.put("email", email);

        return db.insert("users", null, values) != -1;
    }

    public boolean setFirebaseUid(String mobile, String firebaseUid) {
        ContentValues values = new ContentValues();
        values.put("firebase_uid", firebaseUid);

        return getWritableDatabase().update(
                "users", values, "mobile = ?", new String[]{mobile}) > 0;
    }

    public String getMobileByFirebaseUid(String firebaseUid) {
        return getUserString("mobile", "firebase_uid", firebaseUid);
    }

    public String getMobileByEmail(String email) {
        return getUserString("mobile", "email", email);
    }

    public String getFirebaseUidByMobile(String mobile) {
        return getUserString("firebase_uid", "mobile", mobile);
    }

    private String getUserString(String returnColumn, String whereColumn, String value) {
        SQLiteDatabase db = getReadableDatabase();

        Cursor cursor = db.rawQuery(
                "SELECT " + returnColumn + " FROM users WHERE " +
                        whereColumn + " = ? LIMIT 1",
                new String[]{value});

        try {
            return cursor.moveToFirst() ? cursor.getString(0) : null;
        } finally {
            cursor.close();
        }
    }

    public String getOwnerName(String mobile) {
        return getUserField(mobile, "owner_name");
    }

    public String getShopName(String mobile) {
        return getUserField(mobile, "shop_name");
    }

    private String getUserField(String mobile, String field) {
        if (!"owner_name".equals(field) && !"shop_name".equals(field)) {
            return null;
        }

        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT " + field + " FROM users WHERE mobile = ? LIMIT 1",
                new String[]{mobile});

        try {
            return cursor.moveToFirst() ? cursor.getString(0) : null;
        } finally {
            cursor.close();
        }
    }

    public int getUserId(String mobile) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT id FROM users WHERE mobile = ? LIMIT 1",
                new String[]{mobile});

        try {
            return cursor.moveToFirst() ? cursor.getInt(0) : -1;
        } finally {
            cursor.close();
        }
    }

    public int getUserIdByOwnerAndShop(String ownerName, String shopName) {
        Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT id FROM users WHERE owner_name = ? AND shop_name = ? LIMIT 1",
                new String[]{ownerName, shopName});

        try {
            return cursor.moveToFirst() ? cursor.getInt(0) : -1;
        } finally {
            cursor.close();
        }
    }

    public boolean checkMobileExists(String mobile) {
        Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT id FROM users WHERE mobile = ? LIMIT 1",
                new String[]{mobile});

        try {
            return cursor.moveToFirst();
        } finally {
            cursor.close();
        }
    }

    public boolean checkUser(String mobile, String password) {
        Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT id FROM users WHERE mobile = ? AND password = ?",
                new String[]{mobile, password});

        try {
            return cursor.moveToFirst();
        } finally {
            cursor.close();
        }
    }

    // --------------------------------------------------
    // CUSTOMER METHODS
    // --------------------------------------------------

    public boolean addCustomer(int userId, String name, String mobile, String address) {
        ContentValues values = new ContentValues();
        values.put("user_id", userId);
        values.put("name", name);
        values.put("mobile", mobile);
        values.put("address", address);
        values.put("sync_status", "PENDING");

        return getWritableDatabase().insert("customers", null, values) != -1;
    }

    public Cursor getCustomers(int userId) {
        return getReadableDatabase().rawQuery(
                "SELECT * FROM customers WHERE user_id = ? ORDER BY id DESC",
                new String[]{String.valueOf(userId)});
    }

    public Cursor getCustomer(int customerId) {
        return getReadableDatabase().rawQuery(
                "SELECT * FROM customers WHERE id = ? LIMIT 1",
                new String[]{String.valueOf(customerId)});
    }

    public boolean updateCustomer(int customerId, String name,
                                  String mobile, String address) {

        ContentValues values = new ContentValues();
        values.put("name", name);
        values.put("mobile", mobile);
        values.put("address", address);
        values.put("sync_status", "PENDING");

        return getWritableDatabase().update(
                "customers", values, "id = ?",
                new String[]{String.valueOf(customerId)}) > 0;
    }

    public boolean setCustomerCloudId(int customerId, String cloudId) {
        ContentValues values = new ContentValues();
        values.put("cloud_id", cloudId);

        return getWritableDatabase().update(
                "customers", values, "id = ?",
                new String[]{String.valueOf(customerId)}) > 0;
    }

    // Call this only after Firestore upload succeeds.
    public boolean markCustomerSynced(int customerId, String cloudId) {
        ContentValues values = new ContentValues();
        values.put("cloud_id", cloudId);
        values.put("sync_status", "SYNCED");

        return getWritableDatabase().update(
                "customers", values, "id = ?",
                new String[]{String.valueOf(customerId)}) > 0;
    }

    public String getCustomerCloudId(int customerId) {
        return getCloudId("customers", customerId);
    }

    public int getCustomerCount(int userId) {
        Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM customers WHERE user_id = ?",
                new String[]{String.valueOf(userId)});

        try {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        } finally {
            cursor.close();
        }
    }

    // --------------------------------------------------
    // TRANSACTION METHODS
    // --------------------------------------------------

    public boolean addTransaction(int customerId, String type,
                                  double amount, String description) {

        ContentValues values = new ContentValues();
        values.put("customer_id", customerId);
        values.put("type", type);
        values.put("amount", amount);
        values.put("description", description);
        values.put("sync_status", "PENDING");

        return getWritableDatabase().insert("transactions", null, values) != -1;
    }

    public Cursor getTransactions(int customerId) {
        return getReadableDatabase().rawQuery(
                "SELECT * FROM transactions WHERE customer_id = ? ORDER BY id DESC",
                new String[]{String.valueOf(customerId)});
    }

    public boolean updateTransaction(int transactionId, String type,
                                     double amount, String description) {

        ContentValues values = new ContentValues();
        values.put("type", type);
        values.put("amount", amount);
        values.put("description", description);
        values.put("sync_status", "PENDING");

        return getWritableDatabase().update(
                "transactions", values, "id = ?",
                new String[]{String.valueOf(transactionId)}) > 0;
    }

    public boolean setTransactionCloudId(int transactionId, String cloudId) {
        ContentValues values = new ContentValues();
        values.put("cloud_id", cloudId);

        return getWritableDatabase().update(
                "transactions", values, "id = ?",
                new String[]{String.valueOf(transactionId)}) > 0;
    }

    // Call this only after Firestore upload succeeds.
    public boolean markTransactionSynced(int transactionId, String cloudId) {
        ContentValues values = new ContentValues();
        values.put("cloud_id", cloudId);
        values.put("sync_status", "SYNCED");

        return getWritableDatabase().update(
                "transactions", values, "id = ?",
                new String[]{String.valueOf(transactionId)}) > 0;
    }

    public String getTransactionCloudId(int transactionId) {
        return getCloudId("transactions", transactionId);
    }

    private String getCloudId(String table, int localId) {
        if (!"customers".equals(table) && !"transactions".equals(table)) {
            return null;
        }

        Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT cloud_id FROM " + table + " WHERE id = ? LIMIT 1",
                new String[]{String.valueOf(localId)});

        try {
            return cursor.moveToFirst() ? cursor.getString(0) : null;
        } finally {
            cursor.close();
        }
    }

    // --------------------------------------------------
    // DELETE METHODS AND CLOUD DELETION TRACKING
    // --------------------------------------------------

    public boolean deleteTransaction(int transactionId) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();

        try {
            Cursor check = db.rawQuery(
                    "SELECT id FROM transactions WHERE id = ? LIMIT 1",
                    new String[]{String.valueOf(transactionId)});

            boolean exists;
            try {
                exists = check.moveToFirst();
            } finally {
                check.close();
            }

            if (!exists) {
                return false;
            }

            String cloudId = getCloudIdFromDatabase(
                    db, "transactions", transactionId);

            String customerCloudId = getParentCustomerCloudId(db, transactionId);

            if (!isEmpty(cloudId)) {
                addDeletionRecord(db, "transaction", cloudId, customerCloudId);
            }

            int deleted = db.delete(
                    "transactions", "id = ?",
                    new String[]{String.valueOf(transactionId)});

            if (deleted <= 0) {
                return false;
            }

            db.setTransactionSuccessful();
            return true;

        } catch (Exception e) {
            return false;
        } finally {
            db.endTransaction();
        }
    }

    public boolean deleteCustomer(int customerId) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();

        try {
            String customerCloudId = getCloudIdFromDatabase(
                    db, "customers", customerId);

            Cursor check = db.rawQuery(
                    "SELECT id FROM customers WHERE id = ? LIMIT 1",
                    new String[]{String.valueOf(customerId)});

            boolean exists;
            try {
                exists = check.moveToFirst();
            } finally {
                check.close();
            }

            if (!exists) {
                return false;
            }

            Cursor transactions = db.rawQuery(
                    "SELECT id, cloud_id FROM transactions WHERE customer_id = ?",
                    new String[]{String.valueOf(customerId)});

            try {
                while (transactions.moveToNext()) {
                    String transactionCloudId = transactions.getString(1);

                    if (!isEmpty(transactionCloudId)) {
                        addDeletionRecord(db, "transaction",
                                transactionCloudId, customerCloudId);
                    }
                }
            } finally {
                transactions.close();
            }

            if (!isEmpty(customerCloudId)) {
                addDeletionRecord(db, "customer", customerCloudId, null);
            }

            db.delete("transactions", "customer_id = ?",
                    new String[]{String.valueOf(customerId)});

            int deleted = db.delete("customers", "id = ?",
                    new String[]{String.valueOf(customerId)});

            if (deleted <= 0) {
                return false;
            }

            db.setTransactionSuccessful();
            return true;

        } catch (Exception e) {
            return false;
        } finally {
            db.endTransaction();
        }
    }

    private String getCloudIdFromDatabase(SQLiteDatabase db, String table, int localId) {
        if (!"customers".equals(table) && !"transactions".equals(table)) {
            return null;
        }

        Cursor cursor = db.rawQuery(
                "SELECT cloud_id FROM " + table + " WHERE id = ? LIMIT 1",
                new String[]{String.valueOf(localId)});

        try {
            return cursor.moveToFirst() ? cursor.getString(0) : null;
        } finally {
            cursor.close();
        }
    }

    private String getParentCustomerCloudId(SQLiteDatabase db, int transactionId) {
        Cursor cursor = db.rawQuery(
                "SELECT c.cloud_id FROM customers c " +
                        "INNER JOIN transactions t ON t.customer_id = c.id " +
                        "WHERE t.id = ? LIMIT 1",
                new String[]{String.valueOf(transactionId)});

        try {
            return cursor.moveToFirst() ? cursor.getString(0) : null;
        } finally {
            cursor.close();
        }
    }

    private void addDeletionRecord(SQLiteDatabase db, String entityType,
                                   String cloudId, String parentCustomerCloudId) {

        Cursor cursor = db.rawQuery(
                "SELECT id FROM sync_deletions " +
                        "WHERE entity_type = ? AND cloud_id = ? LIMIT 1",
                new String[]{entityType, cloudId});

        boolean exists;
        try {
            exists = cursor.moveToFirst();
        } finally {
            cursor.close();
        }

        if (!exists) {
            ContentValues values = new ContentValues();
            values.put("entity_type", entityType);
            values.put("cloud_id", cloudId);
            values.put("parent_customer_cloud_id", parentCustomerCloudId);

            db.insert("sync_deletions", null, values);
        }
    }

    public Cursor getPendingDeletions() {
        return getReadableDatabase().rawQuery(
                "SELECT id, entity_type, cloud_id, parent_customer_cloud_id " +
                        "FROM sync_deletions ORDER BY id ASC",
                null);
    }

    public boolean removePendingDeletion(int deletionId) {
        return getWritableDatabase().delete(
                "sync_deletions", "id = ?",
                new String[]{String.valueOf(deletionId)}) > 0;
    }

    public boolean deleteSyncDeletionRecord(int deletionId) {
        return removePendingDeletion(deletionId);
    }

    private boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }

    // --------------------------------------------------
    // CLOUD RESTORE METHODS
    // --------------------------------------------------

    public int getCustomerIdByCloudId(String cloudId) {
        Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT id FROM customers WHERE cloud_id = ? LIMIT 1",
                new String[]{cloudId});

        try {
            return cursor.moveToFirst() ? cursor.getInt(0) : -1;
        } finally {
            cursor.close();
        }
    }

    public int restoreCloudCustomer(int userId, String cloudId, String name,
                                    String mobile, String address, String createdAt) {

        SQLiteDatabase db = getWritableDatabase();

        Cursor cursor = db.rawQuery(
                "SELECT id, sync_status FROM customers WHERE cloud_id = ? LIMIT 1",
                new String[]{cloudId});

        int localId = -1;
        String syncStatus = null;

        try {
            if (cursor.moveToFirst()) {
                localId = cursor.getInt(0);
                syncStatus = cursor.getString(1);
            }
        } finally {
            cursor.close();
        }

        // Do not overwrite unsynced local changes.
        if (localId >= 0 && "PENDING".equalsIgnoreCase(syncStatus)) {
            return localId;
        }

        ContentValues values = new ContentValues();
        values.put("user_id", userId);
        values.put("cloud_id", cloudId);
        values.put("name", name);
        values.put("mobile", mobile);
        values.put("address", address);
        values.put("created_at", createdAt);
        values.put("sync_status", "SYNCED");

        if (localId >= 0) {
            db.update("customers", values, "id = ?",
                    new String[]{String.valueOf(localId)});
            return localId;
        }

        long result = db.insert("customers", null, values);
        return result == -1 ? -1 : (int) result;
    }

    public boolean restoreCloudTransaction(int customerId, String cloudId,
                                           String type, double amount,
                                           String description, String date) {

        SQLiteDatabase db = getWritableDatabase();

        Cursor cursor = db.rawQuery(
                "SELECT id, sync_status FROM transactions WHERE cloud_id = ? LIMIT 1",
                new String[]{cloudId});

        int localId = -1;
        String syncStatus = null;

        try {
            if (cursor.moveToFirst()) {
                localId = cursor.getInt(0);
                syncStatus = cursor.getString(1);
            }
        } finally {
            cursor.close();
        }

        // Do not overwrite unsynced local changes.
        if (localId >= 0 && "PENDING".equalsIgnoreCase(syncStatus)) {
            return true;
        }

        ContentValues values = new ContentValues();
        values.put("customer_id", customerId);
        values.put("cloud_id", cloudId);
        values.put("type", type);
        values.put("amount", amount);
        values.put("description", description);
        values.put("date", date);
        values.put("sync_status", "SYNCED");

        if (localId >= 0) {
            return db.update("transactions", values, "id = ?",
                    new String[]{String.valueOf(localId)}) > 0;
        }

        return db.insert("transactions", null, values) != -1;
    }

    // --------------------------------------------------
    // DASHBOARD / BALANCE METHODS
    // --------------------------------------------------

    public double getTotalUdhar(int customerId) {
        return getTransactionTotal(
                "customer_id = ? AND type = ?",
                new String[]{String.valueOf(customerId), "UDHAR"});
    }

    public double getTotalPayment(int customerId) {
        return getTransactionTotal(
                "customer_id = ? AND type = ?",
                new String[]{String.valueOf(customerId), "PAYMENT"});
    }

    public double getCustomerBalance(int customerId) {
        return getTotalUdhar(customerId) - getTotalPayment(customerId);
    }

    public double getUserTotalBalance(int userId) {
        Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT COALESCE(SUM(CASE " +
                        "WHEN t.type = 'UDHAR' THEN t.amount " +
                        "WHEN t.type = 'PAYMENT' THEN -t.amount ELSE 0 END), 0) " +
                        "FROM transactions t " +
                        "INNER JOIN customers c ON c.id = t.customer_id " +
                        "WHERE c.user_id = ?",
                new String[]{String.valueOf(userId)});

        try {
            return cursor.moveToFirst() ? cursor.getDouble(0) : 0;
        } finally {
            cursor.close();
        }
    }

    private double getTransactionTotal(String selection, String[] selectionArgs) {
        Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE " + selection,
                selectionArgs);

        try {
            return cursor.moveToFirst() ? cursor.getDouble(0) : 0;
        } finally {
            cursor.close();
        }
    }

    public double getTodayUdhar(int userId) {
        return getDateTotal(userId, "UDHAR", "date('now','localtime')");
    }

    public double getTodayPayment(int userId) {
        return getDateTotal(userId, "PAYMENT", "date('now','localtime')");
    }

    private double getDateTotal(int userId, String type, String dateExpression) {
        Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT COALESCE(SUM(t.amount), 0) " +
                        "FROM transactions t " +
                        "INNER JOIN customers c ON c.id = t.customer_id " +
                        "WHERE c.user_id = ? AND t.type = ? " +
                        "AND date(t.date) = " + dateExpression,
                new String[]{String.valueOf(userId), type});

        try {
            return cursor.moveToFirst() ? cursor.getDouble(0) : 0;
        } finally {
            cursor.close();
        }
    }

    public Cursor getTodayTransactions(int userId) {
        return getReadableDatabase().rawQuery(
                "SELECT t.*, c.name AS customer_name " +
                        "FROM transactions t " +
                        "INNER JOIN customers c ON c.id = t.customer_id " +
                        "WHERE c.user_id = ? " +
                        "AND date(t.date) = date('now','localtime') " +
                        "ORDER BY t.date DESC",
                new String[]{String.valueOf(userId)});
    }

    public Cursor get15DaysTransactions(int userId) {
        return getReadableDatabase().rawQuery(
                "SELECT t.*, c.name AS customer_name " +
                        "FROM transactions t " +
                        "INNER JOIN customers c ON c.id = t.customer_id " +
                        "WHERE c.user_id = ? " +
                        "AND date(t.date) >= date('now','localtime','-14 days') " +
                        "ORDER BY t.date DESC",
                new String[]{String.valueOf(userId)});
    }

    public Cursor getMonthlyTransactions(int userId) {
        return getReadableDatabase().rawQuery(
                "SELECT t.*, c.name AS customer_name " +
                        "FROM transactions t " +
                        "INNER JOIN customers c ON c.id = t.customer_id " +
                        "WHERE c.user_id = ? " +
                        "AND strftime('%Y-%m', t.date) = " +
                        "strftime('%Y-%m', 'now', 'localtime') " +
                        "ORDER BY t.date DESC",
                new String[]{String.valueOf(userId)});
    }

    // Remove a customer locally when Firestore says that customer is deleted
    public void removeCloudCustomerLocally(String cloudId) {

        if (cloudId == null || cloudId.trim().isEmpty()) {
            return;
        }

        SQLiteDatabase db = this.getWritableDatabase();

        Cursor cursor = null;

        db.beginTransaction();

        try {

            int customerId = -1;

            cursor = db.query(
                    "customers",
                    new String[]{"id"},
                    "cloud_id = ?",
                    new String[]{cloudId},
                    null,
                    null,
                    null
            );

            if (cursor != null && cursor.moveToFirst()) {
                customerId = cursor.getInt(
                        cursor.getColumnIndexOrThrow("id")
                );
            }

            if (cursor != null) {
                cursor.close();
                cursor = null;
            }

            if (customerId != -1) {

                // Delete all transactions of this customer
                db.delete(
                        "transactions",
                        "customer_id = ?",
                        new String[]{String.valueOf(customerId)}
                );

                // Delete customer
                db.delete(
                        "customers",
                        "id = ?",
                        new String[]{String.valueOf(customerId)}
                );
            }

            db.setTransactionSuccessful();

        } catch (Exception e) {

            e.printStackTrace();

        } finally {

            if (cursor != null) {
                cursor.close();
            }

            db.endTransaction();
        }
    }
}
