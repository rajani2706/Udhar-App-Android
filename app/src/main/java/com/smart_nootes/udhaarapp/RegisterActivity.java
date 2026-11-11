package com.smart_nootes.udhaarapp;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;

public class RegisterActivity extends AppCompatActivity {

    private EditText edtOwnerName;
    private EditText edtShopName;
    private EditText edtEmail;
    private EditText edtMobile;
    private EditText edtPassword;
    private EditText edtConfirmPassword;

    private Button btnRegister;
    private TextView txtGoToLogin;

    private DatabaseHelper databaseHelper;
    private FirebaseAuth firebaseAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        edtOwnerName = findViewById(R.id.edtOwnerName);
        edtShopName = findViewById(R.id.edtShopName);
        edtEmail = findViewById(R.id.edtEmail);
        edtMobile = findViewById(R.id.edtMobile);
        edtPassword = findViewById(R.id.edtPassword);
        edtConfirmPassword =
                findViewById(R.id.edtConfirmPassword);

        btnRegister = findViewById(R.id.btnRegister);
        txtGoToLogin = findViewById(R.id.txtGoToLogin);

        databaseHelper = new DatabaseHelper(this);
        firebaseAuth = FirebaseAuth.getInstance();

        btnRegister.setOnClickListener(v -> registerUser());

        txtGoToLogin.setOnClickListener(v -> {
            startActivity(
                    new Intent(
                            RegisterActivity.this,
                            MainActivity.class
                    )
            );
            finish();
        });
    }

    private void registerUser() {

        String ownerName =
                edtOwnerName.getText().toString().trim();

        String shopName =
                edtShopName.getText().toString().trim();

        String email =
                edtEmail.getText().toString().trim();

        String mobile =
                edtMobile.getText().toString().trim();

        String password =
                edtPassword.getText().toString();

        String confirmPassword =
                edtConfirmPassword.getText().toString();

        if (ownerName.isEmpty()
                || !ownerName.matches("[a-zA-Z ]+")) {
            edtOwnerName.setError(
                    "Enter a valid owner name using English letters"
            );
            edtOwnerName.requestFocus();
            return;
        }

        if (shopName.isEmpty()) {
            edtShopName.setError("Enter shop name");
            edtShopName.requestFocus();
            return;
        }

        if (email.isEmpty()
                || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            edtEmail.setError("Enter a valid email address");
            edtEmail.requestFocus();
            return;
        }

        if (!mobile.matches("[0-9]{10}")) {
            edtMobile.setError("Enter valid 10 digit mobile number");
            edtMobile.requestFocus();
            return;
        }

        // Firebase Email/Password default minimum: 6 characters
        if (password.length() < 6) {
            edtPassword.setError(
                    "Password must contain at least 6 characters"
            );
            edtPassword.requestFocus();
            return;
        }

        if (!password.equals(confirmPassword)) {
            edtConfirmPassword.setError(
                    "Passwords do not match"
            );
            edtConfirmPassword.requestFocus();
            return;
        }

        if (databaseHelper.checkMobileExists(mobile)) {
            edtMobile.setError(
                    "This mobile number is already registered"
            );

            Toast.makeText(
                    this,
                    "This shop already has a local account. Existing accounts need migration.",
                    Toast.LENGTH_LONG
            ).show();

            return;
        }

        btnRegister.setEnabled(false);

        firebaseAuth.createUserWithEmailAndPassword(
                email,
                password
        ).addOnCompleteListener(this, task -> {

            if (!task.isSuccessful()) {

                btnRegister.setEnabled(true);

                Toast.makeText(
                        this,
                        "Registration failed: "
                                + (task.getException() != null
                                ? task.getException().getMessage()
                                : "Please try again"),
                        Toast.LENGTH_LONG
                ).show();

                return;
            }

            // Firebase account created successfully.
            boolean inserted = databaseHelper.registerUser(
                    ownerName,
                    shopName,
                    mobile,
                    email
            );

            if (inserted) {

                Toast.makeText(
                        this,
                        "Registration successful. Please log in.",
                        Toast.LENGTH_LONG
                ).show();

                firebaseAuth.signOut();

                Intent intent = new Intent(
                        RegisterActivity.this,
                        MainActivity.class
                );

                intent.addFlags(
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
                                | Intent.FLAG_ACTIVITY_NEW_TASK
                );

                startActivity(intent);
                finish();

            } else {

                // Avoid leaving an unused Firebase account if
                // the local shop profile could not be created.
                if (firebaseAuth.getCurrentUser() != null) {
                    firebaseAuth.getCurrentUser().delete();
                }

                btnRegister.setEnabled(true);

                Toast.makeText(
                        this,
                        "Shop profile could not be saved. Please try again.",
                        Toast.LENGTH_LONG
                ).show();
            }
        });
    }
}