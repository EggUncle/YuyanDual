package io.github.egguncle.rpduo.imeprobe;

import android.app.Activity;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Display;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;

/** Standalone local input test surface. No networking, storage, or runtime permissions. */
public final class ProbeActivity extends Activity {
    private View root;
    private TextView displayStatus;
    private TextView focusStatus;
    private TextView actionStatus;
    private EditText plain;
    private EditText number;
    private EditText password;
    private EditText requestedField;
    private boolean showKeyboardOnFocus;
    private int editorActionCount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_probe);
        root = findViewById(R.id.probe_root);
        displayStatus = findViewById(R.id.display_status);
        focusStatus = findViewById(R.id.focus_status);
        actionStatus = findViewById(R.id.action_status);
        plain = bindField(R.id.plain_input, R.id.plain_status, "plain", false);
        number = bindField(R.id.number_input, R.id.number_status, "number", false);
        password = bindField(R.id.password_input, R.id.password_status, "password", true);
        root.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> updateBounds());
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            // Keep the controls reachable when Android 15 enforces edge-to-edge layout.
            int padding = Math.round(16 * getResources().getDisplayMetrics().density);
            root.setPadding(padding + insets.getSystemWindowInsetLeft(), padding + insets.getSystemWindowInsetTop(),
                    padding + insets.getSystemWindowInsetRight(), padding + insets.getSystemWindowInsetBottom());
            return insets;
        });
        findViewById(R.id.reset_button).setOnClickListener(v -> {
            plain.setText("");
            number.setText("");
            password.setText("");
            editorActionCount = 0;
            actionStatus.setText("Editor actions: 0");
            plain.requestFocus();
        });
        findViewById(R.id.hide_button).setOnClickListener(v -> hideKeyboard());
        String field = getIntent().getStringExtra("field");
        requestedField = "number".equals(field) ? number : "password".equals(field) ? password : plain;
        showKeyboardOnFocus = getIntent().getBooleanExtra("show_keyboard", false);
        requestedField.requestFocus();
        focusStatus.setText("Focus: " + (requestedField == number ? "number" : requestedField == password ? "password" : "plain"));
        root.post(this::updateBounds);
    }

    private EditText bindField(int fieldId, int statusId, String name, boolean secret) {
        EditText field = findViewById(fieldId);
        TextView status = findViewById(statusId);
        field.setOnFocusChangeListener((v, focused) -> {
            if (focused) focusStatus.setText("Focus: " + name);
        });
        field.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                status.setText(name + ": length=" + s.length() + (secret ? "" : " text=[" + s + "]"));
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        field.setOnEditorActionListener((view, actionId, event) -> {
            boolean done = actionId == EditorInfo.IME_ACTION_DONE;
            boolean enter = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_UP;
            if (!done && !enter) return false;
            editorActionCount++;
            actionStatus.setText("Editor actions: " + editorActionCount + " field=" + name
                    + " action=" + actionId + " length=" + view.length());
            hideKeyboard();
            return true;
        });
        return field;
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && showKeyboardOnFocus) {
            showKeyboardOnFocus = false;
            requestedField.post(() -> {
                requestedField.requestFocus();
                InputMethodManager imm = getSystemService(InputMethodManager.class);
                if (imm != null) imm.showSoftInput(requestedField, InputMethodManager.SHOW_IMPLICIT);
            });
        }
    }

    private void hideKeyboard() {
        InputMethodManager imm = getSystemService(InputMethodManager.class);
        if (imm != null) imm.hideSoftInputFromWindow(root.getWindowToken(), 0);
    }

    private void updateBounds() {
        Display display = root.getDisplay();
        int displayId = display == null ? -1 : display.getDisplayId();
        displayStatus.setText("Display: " + displayId + " root=" + root.getWidth() + "x" + root.getHeight()
                + " density=" + getResources().getDisplayMetrics().densityDpi);
    }
}
