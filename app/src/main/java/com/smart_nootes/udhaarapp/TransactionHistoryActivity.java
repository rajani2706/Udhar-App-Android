
package com.smart_nootes.udhaarapp;

import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.Locale;

public class TransactionHistoryActivity extends AppCompatActivity {

    private TextView txtCustomerName;
    private TextView txtTotalBalance;
    private TextView txtTotalUdhar;
    private TextView txtTotalPayment;

    private Button btnAddUdhar;
    private Button btnAddPayment;

    private ListView listTransactions;

    private DatabaseHelper databaseHelper;
    private ArrayList<Transaction> transactionList;
    private TransactionAdapter adapter;

    private int customerId = -1;
    private String customerName = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transaction_history);

        txtCustomerName = findViewById(R.id.txtCustomerName);
        txtTotalBalance = findViewById(R.id.txtTotalBalance);
        txtTotalUdhar = findViewById(R.id.txtTotalUdhar);
        txtTotalPayment = findViewById(R.id.txtTotalPayment);

        btnAddUdhar = findViewById(R.id.btnAddUdharFromHisab);
        btnAddPayment = findViewById(R.id.btnAddPaymentFromHisab);

        listTransactions = findViewById(R.id.listTransactions);

        databaseHelper = new DatabaseHelper(this);
        transactionList = new ArrayList<>();

        customerId = getIntent().getIntExtra("customer_id", -1);

        String receivedName = getIntent().getStringExtra("customer_name");
        if (receivedName != null) {
            customerName = receivedName;
        }

        txtCustomerName.setText(customerName);

        if (customerId == -1) {
            Toast.makeText(this, "Customer not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        adapter = new TransactionAdapter();
        listTransactions.setAdapter(adapter);

        // Open Add Udhar screen for this same customer
        btnAddUdhar.setOnClickListener(v -> {
            Intent intent = new Intent(
                    TransactionHistoryActivity.this,
                    AddUdharActivity.class
            );

            intent.putExtra("customer_id", customerId);
            intent.putExtra("customer_name", customerName);
            startActivity(intent);
        });

        // Open Jama Payment screen for this same customer
        btnAddPayment.setOnClickListener(v -> {
            Intent intent = new Intent(
                    TransactionHistoryActivity.this,
                    PaymentActivity.class
            );

            intent.putExtra("customer_id", customerId);
            intent.putExtra("customer_name", customerName);
            startActivity(intent);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (databaseHelper != null && customerId != -1 && adapter != null) {
            loadTransactions();
        }
    }

    private void updateBalanceSummary() {

        double totalUdhar = databaseHelper.getTotalUdhar(customerId);
        double totalPayment = databaseHelper.getTotalPayment(customerId);
        double balance = databaseHelper.getCustomerBalance(customerId);

        txtTotalUdhar.setText(formatAmount(totalUdhar));
        txtTotalPayment.setText(formatAmount(totalPayment));
        txtTotalBalance.setText(formatAmount(balance));

        if (balance > 0) {
            txtTotalBalance.setTextColor(0xFFC62828);
        } else if (balance < 0) {
            txtTotalBalance.setTextColor(0xFF1565C0);
        } else {
            txtTotalBalance.setTextColor(0xFF2E7D32);
        }
    }

    private String formatAmount(double amount) {
        return String.format(
                Locale.getDefault(),
                "₹ %.2f",
                amount
        );
    }

    private void loadTransactions() {

        transactionList.clear();
        updateBalanceSummary();

        Cursor cursor = databaseHelper.getTransactions(customerId);

        if (cursor != null) {
            try {
                while (cursor.moveToNext()) {

                    int id = cursor.getInt(
                            cursor.getColumnIndexOrThrow("id")
                    );

                    String type = cursor.getString(
                            cursor.getColumnIndexOrThrow("type")
                    );

                    double amount = cursor.getDouble(
                            cursor.getColumnIndexOrThrow("amount")
                    );

                    String description = cursor.getString(
                            cursor.getColumnIndexOrThrow("description")
                    );

                    String date = cursor.getString(
                            cursor.getColumnIndexOrThrow("date")
                    );

                    transactionList.add(new Transaction(
                            id, type, amount, description, date
                    ));
                }
            } finally {
                cursor.close();
            }
        }

        adapter.notifyDataSetChanged();
    }

    class Transaction {
        int id;
        String type;
        double amount;
        String description;
        String date;

        Transaction(int id, String type, double amount,
                    String description, String date) {
            this.id = id;
            this.type = type;
            this.amount = amount;
            this.description = description;
            this.date = date;
        }
    }

    class TransactionAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return transactionList.size();
        }

        @Override
        public Object getItem(int position) {
            return transactionList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return transactionList.get(position).id;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {

            if (convertView == null) {
                convertView = LayoutInflater.from(
                        TransactionHistoryActivity.this
                ).inflate(
                        R.layout.transaction_list_item,
                        parent,
                        false
                );
            }

            TextView txtType =
                    convertView.findViewById(R.id.txtTransactionType);

            TextView txtAmount =
                    convertView.findViewById(R.id.txtTransactionAmount);

            TextView txtDescription =
                    convertView.findViewById(R.id.txtTransactionDescription);

            TextView txtDate =
                    convertView.findViewById(R.id.txtTransactionDate);

            Transaction transaction = transactionList.get(position);

            String type = transaction.type == null ? "" : transaction.type;

            txtType.setText(type);
            txtAmount.setText(formatAmount(transaction.amount));

            if (type.equalsIgnoreCase("UDHAR")) {
                txtType.setTextColor(0xFFC62828);
                txtAmount.setTextColor(0xFFC62828);
            } else if (type.equalsIgnoreCase("PAYMENT")
                    || type.equalsIgnoreCase("JAMA")) {
                txtType.setTextColor(0xFF2E7D32);
                txtAmount.setTextColor(0xFF2E7D32);
            } else {
                txtType.setTextColor(0xFF333333);
                txtAmount.setTextColor(0xFF333333);
            }

            txtDescription.setText(
                    transaction.description == null
                            || transaction.description.trim().isEmpty()
                            ? "No description"
                            : transaction.description
            );

            txtDate.setText(
                    transaction.date == null ? "" : transaction.date
            );

            return convertView;
        }
    }
}
