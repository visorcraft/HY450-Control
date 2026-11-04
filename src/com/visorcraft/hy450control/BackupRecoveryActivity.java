package com.visorcraft.hy450control;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import org.json.JSONObject;

public final class BackupRecoveryActivity extends Activity {
    private static final int CREATE_BACKUP = 50;
    private static final int OPEN_BACKUP = 51;
    private static final int MAX_WARNINGS = 3;
    private static final float PAGE_MARGIN_PX = 64f;
    private static final float GAP_PX = 20f;
    private static final float HINT_PX = 32f;
    private static final float NOTE_PX = 40f;

    static final class ImportState {
        final JSONObject root;
        final PortableBackup.Preview preview;
        final String error;
        final boolean restoreEnabled;

        ImportState(JSONObject root, PortableBackup.Preview preview, String error,
                boolean restoreEnabled) {
            this.root = root;
            this.preview = preview;
            this.error = error;
            this.restoreEnabled = restoreEnabled;
        }
    }

    private Map<String, List<Control>> families;
    private JSONObject pendingExport;
    private ImportState imported = cancelledImport();
    private Button exportButton;
    private Button importButton;
    private Button restoreButton;
    private Button recoverButton;
    private TextView status;
    private TextView preview;
    private TextView error;

    static ImportState cancelledImport() {
        return new ImportState(null, null, null, false);
    }

    static ImportState inspect(JSONObject root, Map<String, List<Control>> families,
            String fingerprint) {
        if (root == null) return cancelledImport();
        PortableBackup.Preview preview = null;
        try {
            preview = PortableBackup.preview(root, fingerprint);
            if (preview.firmwareMatches) PortableBackup.validate(root, families, fingerprint);
            return new ImportState(root, preview, null, preview.firmwareMatches);
        } catch (Exception failure) {
            return new ImportState(null, preview, Errors.describe(failure), false);
        }
    }

    static TextView renderPreview(Context context, PortableBackup.Preview value) {
        return Ui.note(context, previewText(value), NOTE_PX,
                value != null && !value.firmwareMatches ? R.color.warning : R.color.text_secondary);
    }

    static TextView renderError(Context context, String message) {
        return Ui.note(context, message == null ? "" : message, HINT_PX, R.color.error);
    }

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        scroll.setFocusable(false);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setClipChildren(false);
        root.setClipToPadding(false);
        int margin = Ui.px(this, PAGE_MARGIN_PX);
        root.setPadding(margin, margin, margin, margin);

        root.addView(Ui.kicker(this, "OWNER ASSISTANT · BACKUP & RECOVERY"));
        root.addView(Ui.note(this, "D-pad moves • centre activates • BACK cancels without writes",
                HINT_PX, R.color.text_tertiary));
        root.addView(Ui.note(this, "Imports stay in memory until Restore is confirmed. "
                + "A fresh safety copy is required before every imported restore.",
                NOTE_PX, R.color.text_secondary));

        root.addView(Ui.section(this, "PORTABLE BACKUP", GAP_PX));
        LinearLayout documents = Ui.chipRow(root, GAP_PX);
        exportButton = Ui.addChip(documents, "Export fresh backup", v -> exportBackup(), GAP_PX);
        importButton = Ui.addChip(documents, "Import backup", v -> openBackup(), GAP_PX);
        LinearLayout recovery = Ui.chipRow(root, GAP_PX);
        restoreButton = Ui.addChip(recovery, "Restore imported", v -> confirmImportedRestore(), GAP_PX);
        recoverButton = Ui.addChip(recovery, "Recover pre-restore state", v -> confirmRecovery(), GAP_PX);

        status = Ui.note(this, "No backup imported", NOTE_PX, R.color.text_primary);
        root.addView(status);
        preview = renderPreview(this, null);
        root.addView(preview);
        error = renderError(this, "");
        root.addView(error);

        scroll.addView(root);
        setContentView(scroll);
        showImport();
        refreshRecovery();
        root.post(this::focusInitial);
    }

    private void exportBackup() {
        try {
            pendingExport = captureBackup();
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("application/json")
                    .putExtra(Intent.EXTRA_TITLE, "hy450-backup.json");
            startActivityForResult(intent, CREATE_BACKUP);
            status.setText("Choose where to save the fresh backup");
        } catch (ActivityNotFoundException | SecurityException failure) {
            pendingExport = null;
            showError("Backup export unavailable", failure);
        } catch (Exception failure) {
            pendingExport = null;
            showError("Backup capture unavailable", failure);
        }
    }

    private void openBackup() {
        imported = cancelledImport();
        showImport();
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("application/json");
            startActivityForResult(intent, OPEN_BACKUP);
        } catch (ActivityNotFoundException | SecurityException failure) {
            showError("Backup import unavailable", failure);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == CREATE_BACKUP) {
            JSONObject document = pendingExport;
            pendingExport = null;
            if (resultCode != RESULT_OK || data == null || data.getData() == null) {
                status.setText("Backup export cancelled • no file written");
                return;
            }
            if (document == null) {
                status.setText("Backup export no longer available • no file written");
                return;
            }
            writeExport(data.getData(), document);
            return;
        }
        if (requestCode != OPEN_BACKUP) return;
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            imported = cancelledImport();
            status.setText("Backup import cancelled • no changes made");
            showImport();
            return;
        }
        readImport(data.getData());
    }

    private void writeExport(Uri target, JSONObject document) {
        try (OutputStream output = getContentResolver().openOutputStream(target)) {
            if (output == null) throw new IllegalStateException("Document stream unavailable");
            BoundedDocument.write(output, document.toString(2).getBytes(StandardCharsets.UTF_8));
            status.setText("Fresh backup exported");
            error.setText("");
        } catch (Exception failure) {
            showError("Backup export failed", failure);
        }
    }

    private void readImport(Uri source) {
        try (InputStream input = getContentResolver().openInputStream(source)) {
            if (input == null) throw new IllegalStateException("Document stream unavailable");
            String json = new String(BoundedDocument.read(input), StandardCharsets.UTF_8);
            imported = inspect(new JSONObject(json), families(), Build.FINGERPRINT);
            status.setText(imported.restoreEnabled ? "Backup imported in memory"
                    : "Backup is not restorable");
        } catch (Exception failure) {
            imported = new ImportState(null, null, Errors.describe(failure), false);
            status.setText("Backup import failed • no changes made");
        }
        showImport();
    }

    private void confirmImportedRestore() {
        if (!imported.restoreEnabled || imported.root == null) return;
        new AlertDialog.Builder(this)
                .setTitle("Restore imported backup?")
                .setMessage("A fresh pre-restore safety copy is saved before any write.\n"
                        + previewText(imported.preview))
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save safety & restore", (dialog, which) -> restoreImported())
                .show();
    }

    private void restoreImported() {
        try {
            JSONObject safety = captureBackup();
            new OwnerBackupStore(this).saveSafety(safety);
            RestoreEngine.Result result = RestoreEngine.restore(imported.root, families(),
                    Build.FINGERPRINT, projection -> OemFocus.cancelAndRun(this,
                            () -> ProjectionBackup.restoreValues(this, projection)));
            showResult("Restore", result);
            refreshRecovery();
        } catch (Exception failure) {
            showError("Restore stopped", failure);
        }
    }

    private void confirmRecovery() {
        try {
            JSONObject safety = new OwnerBackupStore(this).readSafety();
            final ImportState recovery = inspect(safety, families(), Build.FINGERPRINT);
            if (!recovery.restoreEnabled) {
                refreshRecovery();
                return;
            }
            new AlertDialog.Builder(this)
                    .setTitle("Recover pre-restore state?")
                    .setMessage("The existing safety copy is reused and never replaced.\n"
                            + previewText(recovery.preview))
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Recover", (dialog, which) -> recover(recovery.root))
                    .show();
        } catch (Exception failure) {
            showError("Recovery unavailable", failure);
        }
    }

    private void recover(JSONObject safety) {
        try {
            RestoreEngine.Result result = RestoreEngine.restore(safety, families(),
                    Build.FINGERPRINT, projection -> OemFocus.cancelAndRun(this,
                            () -> ProjectionBackup.restoreValues(this, projection)));
            showResult("Recovery", result);
            refreshRecovery();
        } catch (Exception failure) {
            showError("Recovery stopped", failure);
        }
    }

    private void refreshRecovery() {
        boolean enabled = false;
        try {
            JSONObject safety = new OwnerBackupStore(this).readSafety();
            if (safety != null) {
                enabled = inspect(safety, families(), Build.FINGERPRINT).restoreEnabled;
            }
        } catch (Exception failure) {
            error.setText("Safety copy unavailable: " + Errors.describe(failure));
        }
        recoverButton.setEnabled(enabled);
        keepFocusEnabled();
    }

    private void showImport() {
        restoreButton.setEnabled(imported.restoreEnabled);
        preview.setText(previewText(imported.preview));
        preview.setTextColor(getColor(imported.preview != null && !imported.preview.firmwareMatches
                ? R.color.warning : R.color.text_secondary));
        error.setText(imported.error == null ? "" : imported.error);
        keepFocusEnabled();
    }

    private JSONObject captureBackup() throws Exception {
        return PortableBackup.capture(families(), new ProjectionBackup.ValueAccess() {
            @Override
            public String getGlobal(String key) {
                return android.provider.Settings.Global.getString(getContentResolver(), key);
            }

            @Override
            public void putGlobal(String key, String value) {
                throw new UnsupportedOperationException("Capture is read-only");
            }

            @Override
            public String getProperty(String key) throws Exception {
                return (String) Class.forName("android.os.SystemProperties")
                        .getMethod("get", String.class).invoke(null, key);
            }

            @Override
            public void putProperty(String key, String value) {
                throw new UnsupportedOperationException("Capture is read-only");
            }
        }, appVersion(), Build.FINGERPRINT, utcNow());
    }

    private Map<String, List<Control>> families() throws Exception {
        if (families == null) {
            families = new LinkedHashMap<>();
            families.put("picture", ControlPageActivity.loadControls(this, "picture"));
            families.put("audio", ControlPageActivity.loadControls(this, "audio"));
            families.put("source", ControlPageActivity.loadControls(this, "source"));
        }
        return families;
    }

    private void showResult(String operation, RestoreEngine.Result result) {
        StringBuilder message = new StringBuilder(operation);
        message.append(result.failures.isEmpty() ? " completed successfully • "
                : " finished with failures • ").append(result.applied).append(" values");
        if (!result.failures.isEmpty()) {
            message.append(" • ").append(result.failures.size()).append(" failed");
            int shown = Math.min(result.failures.size(), MAX_WARNINGS);
            for (int i = 0; i < shown; i++) message.append("\n").append(result.failures.get(i));
        }
        status.setText(message.toString());
        error.setText("");
    }

    private void showError(String prefix, Exception failure) {
        status.setText(prefix + ": " + Errors.describe(failure));
        error.setText("");
    }

    private static String previewText(PortableBackup.Preview value) {
        if (value == null) return "No import preview";
        StringBuilder text = new StringBuilder(value.summary)
                .append("\nOmissions / warnings: ").append(value.warnings.size());
        int shown = Math.min(value.warnings.size(), MAX_WARNINGS);
        for (int i = 0; i < shown; i++) text.append("\n• ").append(value.warnings.get(i));
        if (value.warnings.size() > shown) {
            text.append("\n...and ").append(value.warnings.size() - shown).append(" more");
        }
        return text.toString();
    }

    private String appVersion() throws Exception {
        return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
    }

    private static String utcNow() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date());
    }

    private void focusInitial() {
        if (exportButton.isEnabled() && exportButton.requestFocus()) return;
        if (importButton.isEnabled()) importButton.requestFocus();
    }

    private void keepFocusEnabled() {
        View focused = getCurrentFocus();
        if (focused == null || focused.isEnabled()) return;
        if (exportButton.requestFocus()) return;
        if (importButton.isEnabled()) importButton.requestFocus();
    }
}
