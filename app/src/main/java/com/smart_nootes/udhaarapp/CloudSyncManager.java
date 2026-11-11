
package com.smart_nootes.udhaarapp;

import android.database.Cursor;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.SetOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public class CloudSyncManager {

    private static final String TAG = "SmartKhataSync";

    private final DatabaseHelper databaseHelper;
    private final FirebaseFirestore firestore;

    private ListenerRegistration customerListener;
    private ListenerRegistration transactionListener;
    private ListenerRegistration deletionListener;

    private boolean syncInProgress = false;
    private boolean syncQueued = false;

    private Runnable pendingRefresh;

    public CloudSyncManager(DatabaseHelper databaseHelper) {
        this.databaseHelper = databaseHelper;
        this.firestore = FirebaseFirestore.getInstance();
    }

    public interface SyncCallback {
        void onSuccess();

        void onError(@NonNull Exception exception);
    }

    // --------------------------------------------------
    // CREATE CUSTOMER DELETION MARKER
    // --------------------------------------------------

    private void createCustomerDeletionMarker(
            String uid,
            String cloudId,
            Runnable onSuccess,
            Consumer<Exception> onFailure
    ) {

        Map<String, Object> deletionData = new HashMap<>();

        deletionData.put("cloud_id", cloudId);
        deletionData.put("deleted_at", FieldValue.serverTimestamp());
        deletionData.put("deleted_by_uid", uid);

        firestore.collection("users")
                .document(uid)
                .collection("deleted_customers")
                .document(cloudId)
                .set(deletionData, SetOptions.merge())
                .addOnSuccessListener(unused -> onSuccess.run())
                .addOnFailureListener(onFailure::accept);
    }

    // --------------------------------------------------
    // APPLY CLOUD DELETIONS TO LOCAL SQLITE
    // --------------------------------------------------

    private void applyCloudCustomerDeletions(
            String uid,
            Runnable onSuccess,
            Consumer<Exception> onFailure
    ) {

        firestore.collection("users")
                .document(uid)
                .collection("deleted_customers")
                .get()
                .addOnSuccessListener(snapshot -> {

                    try {

                        for (DocumentSnapshot document :
                                snapshot.getDocuments()) {

                            String cloudId =
                                    document.getString("cloud_id");

                            if (isEmpty(cloudId)) {
                                cloudId = document.getId();
                            }

                            // Remove customer and its local transactions.
                            // This method must NOT create another deletion queue.
                            databaseHelper.removeCloudCustomerLocally(cloudId);
                        }

                        onSuccess.run();

                    } catch (Exception exception) {
                        onFailure.accept(exception);
                    }
                })
                .addOnFailureListener(onFailure::accept);
    }

    // --------------------------------------------------
    // RESTORE CLOUD DATA AFTER LOGIN
    // --------------------------------------------------

    public void restoreCloudData(SyncCallback callback) {

        FirebaseUser firebaseUser =
                FirebaseAuth.getInstance().getCurrentUser();

        if (firebaseUser == null || firebaseUser.getEmail() == null) {
            callback.onError(new Exception(
                    "Please login to your Firebase account first."
            ));
            return;
        }

        String uid = firebaseUser.getUid();
        String email = firebaseUser.getEmail();

        firestore.collection("users")
                .document(uid)
                .get()
                .addOnSuccessListener(document -> {

                    if (!document.exists()) {

                        String localMobile =
                                databaseHelper.getMobileByEmail(email);

                        if (!isEmpty(localMobile)) {
                            backupLocalData(callback);
                        } else {
                            callback.onError(new Exception(
                                    "Cloud shop profile nahi mila. " +
                                            "Pehle original device par backup karo."
                            ));
                        }

                        return;
                    }

                    String cloudUid =
                            document.getString("firebase_uid");

                    String cloudEmail =
                            document.getString("email");

                    String ownerName =
                            document.getString("owner_name");

                    String shopName =
                            document.getString("shop_name");

                    String cloudMobile =
                            document.getString("mobile");

                    if (cloudUid != null && !cloudUid.equals(uid)) {
                        callback.onError(new Exception(
                                "Cloud profile Firebase UID match nahi karta."
                        ));
                        return;
                    }

                    if (cloudEmail != null &&
                            !cloudEmail.equalsIgnoreCase(email)) {

                        callback.onError(new Exception(
                                "Logged-in email aur cloud profile email match nahi hain."
                        ));
                        return;
                    }

                    if (isEmpty(ownerName) ||
                            isEmpty(shopName) ||
                            isEmpty(cloudMobile)) {

                        callback.onError(new Exception(
                                "Cloud profile mein required details missing hain."
                        ));
                        return;
                    }

                    prepareLocalProfile(
                            uid,
                            email,
                            ownerName,
                            shopName,
                            cloudMobile,
                            callback
                    );
                })
                .addOnFailureListener(callback::onError);
    }

    // --------------------------------------------------
    // VERIFY OR CREATE LOCAL PROFILE
    // --------------------------------------------------

    private void prepareLocalProfile(
            String uid,
            String email,
            String ownerName,
            String shopName,
            String cloudMobile,
            SyncCallback callback
    ) {

        String localMobile =
                databaseHelper.getMobileByEmail(email);

        if (isEmpty(localMobile)) {

            int existingUserId =
                    databaseHelper.getUserId(cloudMobile);

            if (existingUserId >= 0) {
                callback.onError(new Exception(
                        "Is mobile number ka local profile pehle se hai, " +
                                "lekin email match nahi hui. Data safety ke liye " +
                                "restore rok diya gaya."
                ));
                return;
            }

            boolean registered = databaseHelper.registerUser(
                    ownerName,
                    shopName,
                    cloudMobile,
                    email
            );

            if (!registered) {
                callback.onError(new Exception(
                        "Cloud profile local database mein create nahi hua."
                ));
                return;
            }

            localMobile = cloudMobile;

        } else if (!localMobile.equals(cloudMobile)) {

            callback.onError(new Exception(
                    "Local aur cloud mobile number match nahi karte. " +
                            "Profile link nahi ki gayi."
            ));
            return;
        }

        String existingUid =
                databaseHelper.getFirebaseUidByMobile(localMobile);

        if (!isEmpty(existingUid) && !existingUid.equals(uid)) {
            callback.onError(new Exception(
                    "Local profile pehle se kisi doosre Firebase account " +
                            "se linked hai. Restore cancel kar diya gaya."
            ));
            return;
        }

        if (!databaseHelper.setFirebaseUid(localMobile, uid)) {
            callback.onError(new Exception(
                    "Firebase UID local profile mein save nahi hua."
            ));
            return;
        }

        int userId = databaseHelper.getUserId(localMobile);

        if (userId < 0) {
            callback.onError(new Exception("Local user ID nahi mila."));
            return;
        }

        // First process this device's pending deletions.
        processPendingDeletions(
                uid,
                () -> {

                    // Then apply deletions made by other devices.
                    applyCloudCustomerDeletions(
                            uid,
                            () -> restoreCustomers(uid, userId, callback),
                            callback::onError
                    );
                },
                callback
        );
    }

    // --------------------------------------------------
    // RESTORE CUSTOMERS
    // --------------------------------------------------

    private void restoreCustomers(
            String uid,
            int userId,
            SyncCallback callback
    ) {

        firestore.collection("users")
                .document(uid)
                .collection("customers")
                .get()
                .addOnSuccessListener(snapshot -> {

                    try {

                        for (DocumentSnapshot doc :
                                snapshot.getDocuments()) {

                            String cloudId =
                                    doc.getString("cloud_id");

                            if (isEmpty(cloudId)) {
                                cloudId = doc.getId();
                            }

                            String name = doc.getString("name");
                            String mobile = doc.getString("mobile");
                            String address = doc.getString("address");
                            String createdAt = doc.getString("created_at");

                            if (isEmpty(name)) {
                                throw new Exception(
                                        "Cloud customer ka name missing hai: " +
                                                doc.getId()
                                );
                            }

                            int restoredId =
                                    databaseHelper.restoreCloudCustomer(
                                            userId,
                                            cloudId,
                                            name,
                                            mobile,
                                            address,
                                            createdAt
                                    );

                            if (restoredId < 0) {
                                throw new Exception(
                                        "Customer restore nahi hua: " + name
                                );
                            }
                        }

                        restoreTransactions(uid, callback);

                    } catch (Exception exception) {
                        callback.onError(exception);
                    }
                })
                .addOnFailureListener(callback::onError);
    }

    // --------------------------------------------------
    // RESTORE TRANSACTIONS
    // --------------------------------------------------

    private void restoreTransactions(
            String uid,
            SyncCallback callback
    ) {

        firestore.collection("users")
                .document(uid)
                .collection("transactions")
                .get()
                .addOnSuccessListener(snapshot -> {

                    try {

                        for (DocumentSnapshot doc :
                                snapshot.getDocuments()) {

                            String cloudId =
                                    doc.getString("cloud_id");

                            if (isEmpty(cloudId)) {
                                cloudId = doc.getId();
                            }

                            String customerCloudId =
                                    doc.getString("customer_cloud_id");

                            String type = doc.getString("type");
                            String description =
                                    doc.getString("description");

                            String date = doc.getString("date");

                            Number amountNumber =
                                    doc.getDouble("amount");

                            if (amountNumber == null) {

                                Object amountValue =
                                        doc.get("amount");

                                if (amountValue instanceof Number) {
                                    amountNumber = (Number) amountValue;
                                }
                            }

                            if (isEmpty(customerCloudId) ||
                                    isEmpty(type) ||
                                    amountNumber == null) {

                                throw new Exception(
                                        "Transaction data incomplete hai: " +
                                                doc.getId()
                                );
                            }

                            int customerId =
                                    databaseHelper.getCustomerIdByCloudId(
                                            customerCloudId
                                    );

                            if (customerId < 0) {
                                throw new Exception(
                                        "Transaction ka customer nahi mila. " +
                                                "Customer cloud ID: " +
                                                customerCloudId
                                );
                            }

                            boolean restored =
                                    databaseHelper.restoreCloudTransaction(
                                            customerId,
                                            cloudId,
                                            type,
                                            amountNumber.doubleValue(),
                                            description,
                                            date
                                    );

                            if (!restored) {
                                throw new Exception(
                                        "Transaction restore nahi hua: " +
                                                doc.getId()
                                );
                            }
                        }

                        callback.onSuccess();

                    } catch (Exception exception) {
                        callback.onError(exception);
                    }
                })
                .addOnFailureListener(callback::onError);
    }

    // --------------------------------------------------
    // BACKUP LOCAL DATA TO FIRESTORE
    // --------------------------------------------------

    public void backupLocalData(SyncCallback callback) {

        FirebaseUser firebaseUser =
                FirebaseAuth.getInstance().getCurrentUser();

        if (firebaseUser == null || firebaseUser.getEmail() == null) {
            callback.onError(new Exception(
                    "Please login to your Firebase account first."
            ));
            return;
        }

        String uid = firebaseUser.getUid();
        String email = firebaseUser.getEmail();

        String mobile =
                databaseHelper.getMobileByEmail(email);

        if (isEmpty(mobile)) {
            callback.onError(new Exception(
                    "Local shop profile nahi mila."
            ));
            return;
        }

        int userId = databaseHelper.getUserId(mobile);

        if (userId < 0) {
            callback.onError(new Exception(
                    "Local shop profile ka user ID nahi mila."
            ));
            return;
        }

        String existingUid =
                databaseHelper.getFirebaseUidByMobile(mobile);

        if (!isEmpty(existingUid) && !existingUid.equals(uid)) {
            callback.onError(new Exception(
                    "Yeh local profile kisi doosre Firebase account se linked hai."
            ));
            return;
        }

        if (!databaseHelper.setFirebaseUid(mobile, uid)) {
            callback.onError(new Exception(
                    "Firebase UID local profile mein save nahi hua."
            ));
            return;
        }

        Map<String, Object> profile = new HashMap<>();

        profile.put("firebase_uid", uid);
        profile.put("owner_name", databaseHelper.getOwnerName(mobile));
        profile.put("shop_name", databaseHelper.getShopName(mobile));
        profile.put("mobile", mobile);
        profile.put("email", email);

        firestore.collection("users")
                .document(uid)
                .set(profile, SetOptions.merge())
                .addOnSuccessListener(unused -> {

                    // Process pending local deletions first.
                    processPendingDeletions(
                            uid,
                            () -> {

                                // Remove remotely deleted customers
                                // from local SQLite before uploading.
                                applyCloudCustomerDeletions(
                                        uid,
                                        () -> uploadCustomersAndTransactions(
                                                uid,
                                                userId,
                                                callback
                                        ),
                                        callback::onError
                                );
                            },
                            callback
                    );
                })
                .addOnFailureListener(callback::onError);
    }

    // --------------------------------------------------
    // PROCESS PENDING FIREBASE DELETIONS
    // --------------------------------------------------

    private void processPendingDeletions(
            String uid,
            Runnable onComplete,
            SyncCallback callback
    ) {

        List<Map<String, Object>> deletions =
                new ArrayList<>();

        Cursor cursor = null;

        try {

            cursor = databaseHelper.getPendingDeletions();

            while (cursor != null && cursor.moveToNext()) {

                Map<String, Object> item = new HashMap<>();

                item.put("id", cursor.getInt(0));
                item.put("entity_type", cursor.getString(1));
                item.put("cloud_id", cursor.getString(2));
                item.put(
                        "parent_customer_cloud_id",
                        cursor.getString(3)
                );

                deletions.add(item);
            }

        } catch (Exception exception) {

            callback.onError(exception);
            return;

        } finally {

            if (cursor != null) {
                cursor.close();
            }
        }

        processDeletionAtIndex(
                uid,
                deletions,
                0,
                onComplete,
                callback
        );
    }

    // --------------------------------------------------
    // DELETE FIRESTORE DOCUMENTS ONE BY ONE
    // --------------------------------------------------

    private void processDeletionAtIndex(
            String uid,
            List<Map<String, Object>> deletions,
            int index,
            Runnable onComplete,
            SyncCallback callback
    ) {

        if (index >= deletions.size()) {
            onComplete.run();
            return;
        }

        Map<String, Object> item = deletions.get(index);

        int deletionId = (Integer) item.get("id");

        String entityType =
                (String) item.get("entity_type");

        String cloudId =
                (String) item.get("cloud_id");

        if (isEmpty(cloudId)) {
            callback.onError(new Exception(
                    "Pending deletion ka cloud ID missing hai."
            ));
            return;
        }

        if ("transaction".equals(entityType)) {

            // Transaction deletion.
            // Customer deletion already queues its child transactions first.
            firestore.collection("users")
                    .document(uid)
                    .collection("transactions")
                    .document(cloudId)
                    .delete()
                    .addOnSuccessListener(unused -> {

                        boolean removed =
                                databaseHelper.deleteSyncDeletionRecord(
                                        deletionId
                                );

                        if (!removed) {
                            callback.onError(new Exception(
                                    "Transaction deletion ke baad local " +
                                            "pending record remove nahi hua."
                            ));
                            return;
                        }

                        processDeletionAtIndex(
                                uid,
                                deletions,
                                index + 1,
                                onComplete,
                                callback
                        );
                    })
                    .addOnFailureListener(callback::onError);

        } else if ("customer".equals(entityType)) {

            // IMPORTANT:
            // First create permanent tombstone.
            // Only after that delete the actual customer document.

            createCustomerDeletionMarker(
                    uid,
                    cloudId,
                    () -> {

                        firestore.collection("users")
                                .document(uid)
                                .collection("customers")
                                .document(cloudId)
                                .delete()
                                .addOnSuccessListener(unused -> {

                                    boolean removed =
                                            databaseHelper.deleteSyncDeletionRecord(
                                                    deletionId
                                            );

                                    if (!removed) {
                                        callback.onError(new Exception(
                                                "Customer delete successful hai, " +
                                                        "lekin pending record remove nahi hua."
                                        ));
                                        return;
                                    }

                                    processDeletionAtIndex(
                                            uid,
                                            deletions,
                                            index + 1,
                                            onComplete,
                                            callback
                                    );
                                })
                                .addOnFailureListener(callback::onError);
                    },
                    callback::onError
            );

        } else {

            callback.onError(new Exception(
                    "Unknown deletion type: " + entityType
            ));
        }
    }

    // --------------------------------------------------
    // UPLOAD CUSTOMERS AND TRANSACTIONS
    // --------------------------------------------------

    private void uploadCustomersAndTransactions(
            String uid,
            int userId,
            SyncCallback callback
    ) {

        List<Task<Void>> uploadTasks = new ArrayList<>();

        Cursor customers = null;

        try {

            customers = databaseHelper.getCustomers(userId);

            while (customers != null && customers.moveToNext()) {

                int customerId = customers.getInt(
                        customers.getColumnIndexOrThrow("id")
                );

                String name = customers.getString(
                        customers.getColumnIndexOrThrow("name")
                );

                String mobile = customers.getString(
                        customers.getColumnIndexOrThrow("mobile")
                );

                String address = customers.getString(
                        customers.getColumnIndexOrThrow("address")
                );

                String createdAt = customers.getString(
                        customers.getColumnIndexOrThrow("created_at")
                );

                String customerCloudId =
                        databaseHelper.getCustomerCloudId(customerId);

                if (isEmpty(customerCloudId)) {

                    customerCloudId = UUID.randomUUID().toString();

                    if (!databaseHelper.setCustomerCloudId(
                            customerId,
                            customerCloudId
                    )) {

                        throw new Exception(
                                "Customer cloud ID save nahi hua."
                        );
                    }
                }

                // Extra protection:
                // Never upload a customer whose ID has a deletion marker.
                // The full tombstone check is also done before this method.
                Map<String, Object> customerData = new HashMap<>();

                customerData.put("local_id", customerId);
                customerData.put("name", name);
                customerData.put("mobile", mobile);
                customerData.put("address", address);
                customerData.put("created_at", createdAt);
                customerData.put("cloud_id", customerCloudId);

                Task<Void> customerTask =
                        firestore.collection("users")
                                .document(uid)
                                .collection("customers")
                                .document(customerCloudId)
                                .set(customerData, SetOptions.merge());

                final int finalCustomerId = customerId;
                final String finalCustomerCloudId = customerCloudId;

                customerTask.addOnSuccessListener(unused -> {

                    boolean marked =
                            databaseHelper.markCustomerSynced(
                                    finalCustomerId,
                                    finalCustomerCloudId
                            );

                    if (!marked) {
                        Log.e(TAG,
                                "Customer sync status update failed: " +
                                        finalCustomerId);
                    }
                });

                uploadTasks.add(customerTask);

                Cursor transactions = null;

                try {

                    transactions =
                            databaseHelper.getTransactions(customerId);

                    while (transactions != null &&
                            transactions.moveToNext()) {

                        int transactionId = transactions.getInt(
                                transactions.getColumnIndexOrThrow("id")
                        );

                        String type = transactions.getString(
                                transactions.getColumnIndexOrThrow("type")
                        );

                        double amount = transactions.getDouble(
                                transactions.getColumnIndexOrThrow("amount")
                        );

                        String description = transactions.getString(
                                transactions.getColumnIndexOrThrow("description")
                        );

                        String date = transactions.getString(
                                transactions.getColumnIndexOrThrow("date")
                        );

                        String transactionCloudId =
                                databaseHelper.getTransactionCloudId(
                                        transactionId
                                );

                        if (isEmpty(transactionCloudId)) {

                            transactionCloudId =
                                    UUID.randomUUID().toString();

                            if (!databaseHelper.setTransactionCloudId(
                                    transactionId,
                                    transactionCloudId
                            )) {

                                throw new Exception(
                                        "Transaction cloud ID save nahi hua."
                                );
                            }
                        }

                        Map<String, Object> transactionData =
                                new HashMap<>();

                        transactionData.put("local_id", transactionId);
                        transactionData.put(
                                "customer_local_id",
                                customerId
                        );

                        transactionData.put(
                                "customer_cloud_id",
                                customerCloudId
                        );

                        transactionData.put("type", type);
                        transactionData.put("amount", amount);
                        transactionData.put("description", description);
                        transactionData.put("date", date);
                        transactionData.put(
                                "cloud_id",
                                transactionCloudId
                        );

                        Task<Void> transactionTask =
                                firestore.collection("users")
                                        .document(uid)
                                        .collection("transactions")
                                        .document(transactionCloudId)
                                        .set(
                                                transactionData,
                                                SetOptions.merge()
                                        );

                        final int finalTransactionId = transactionId;

                        final String finalTransactionCloudId =
                                transactionCloudId;

                        transactionTask.addOnSuccessListener(unused -> {

                            boolean marked =
                                    databaseHelper.markTransactionSynced(
                                            finalTransactionId,
                                            finalTransactionCloudId
                                    );

                            if (!marked) {
                                Log.e(TAG,
                                        "Transaction sync status update failed: " +
                                                finalTransactionId);
                            }
                        });

                        uploadTasks.add(transactionTask);
                    }

                } finally {

                    if (transactions != null) {
                        transactions.close();
                    }
                }
            }

        } catch (Exception exception) {

            callback.onError(exception);
            return;

        } finally {

            if (customers != null) {
                customers.close();
            }
        }

        Tasks.whenAll(uploadTasks)
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(callback::onError);
    }

    // --------------------------------------------------
    // HELPER
    // --------------------------------------------------

    private boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }

    // --------------------------------------------------
    // REAL-TIME FIRESTORE SYNC
    // --------------------------------------------------

    public void startRealtimeSync(Runnable onDataChanged) {

        FirebaseUser firebaseUser =
                FirebaseAuth.getInstance().getCurrentUser();

        if (firebaseUser == null) {
            return;
        }

        stopRealtimeSync();

        String uid = firebaseUser.getUid();

        pendingRefresh = onDataChanged;

        customerListener = firestore.collection("users")
                .document(uid)
                .collection("customers")
                .addSnapshotListener((snapshot, error) -> {

                    if (error != null || snapshot == null) {
                        Log.e(TAG, "Customer listener error", error);
                        return;
                    }

                    requestRealtimeRestore();
                });

        transactionListener = firestore.collection("users")
                .document(uid)
                .collection("transactions")
                .addSnapshotListener((snapshot, error) -> {

                    if (error != null || snapshot == null) {
                        Log.e(TAG, "Transaction listener error", error);
                        return;
                    }

                    requestRealtimeRestore();
                });

        // Listen for customer deletion markers too.
        deletionListener = firestore.collection("users")
                .document(uid)
                .collection("deleted_customers")
                .addSnapshotListener((snapshot, error) -> {

                    if (error != null || snapshot == null) {
                        Log.e(TAG, "Deletion listener error", error);
                        return;
                    }

                    requestRealtimeRestore();
                });
    }

    // --------------------------------------------------
    // PREVENT MULTIPLE RESTORES AT SAME TIME
    // --------------------------------------------------

    private void requestRealtimeRestore() {

        if (syncInProgress) {
            syncQueued = true;
            return;
        }

        syncInProgress = true;

        restoreCloudData(new SyncCallback() {

            @Override
            public void onSuccess() {

                syncInProgress = false;

                if (pendingRefresh != null) {
                    pendingRefresh.run();
                }

                if (syncQueued) {
                    syncQueued = false;
                    requestRealtimeRestore();
                }
            }

            @Override
            public void onError(@NonNull Exception exception) {

                syncInProgress = false;

                Log.e(TAG, "Real-time restore failed", exception);

                if (syncQueued) {
                    syncQueued = false;
                    requestRealtimeRestore();
                }
            }
        });
    }

    // --------------------------------------------------
    // STOP REAL-TIME LISTENERS
    // --------------------------------------------------

    public void stopRealtimeSync() {

        if (customerListener != null) {
            customerListener.remove();
            customerListener = null;
        }

        if (transactionListener != null) {
            transactionListener.remove();
            transactionListener = null;
        }

        if (deletionListener != null) {
            deletionListener.remove();
            deletionListener = null;
        }

        pendingRefresh = null;
    }
}
