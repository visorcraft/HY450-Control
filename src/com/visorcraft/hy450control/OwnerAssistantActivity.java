package com.visorcraft.hy450control;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
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

public final class OwnerAssistantActivity extends Activity {
    private static final int CREATE_DIAGNOSTIC = 40;
    private static final int MAX_DETAILS = 4;
    private static final float PAGE_MARGIN_PX = 64f;
    private static final float GAP_PX = 20f;
    private static final float HINT_PX = 32f;
    private static final float NOTE_PX = 40f;

    private TextView status;
    private TextView details;
    private JSONObject pendingDiagnostic;

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

        root.addView(Ui.kicker(this, "OWNER ASSISTANT"));
        root.addView(Ui.note(this, "D-pad moves • centre activates • BACK leaves without changes",
                HINT_PX, R.color.text_tertiary));
        root.addView(Ui.note(this, "Setup and diagnostic scans run only when you activate them.",
                NOTE_PX, R.color.text_secondary));

        root.addView(Ui.section(this, "CHECK & RECOVER", GAP_PX));
        LinearLayout first = Ui.chipRow(root, GAP_PX);
        Button setup = Ui.addChip(first, "Setup check", v -> showScan(), GAP_PX);
        Ui.addChip(first, "Routines",
                v -> startActivity(new Intent(this, RoutinesActivity.class)), GAP_PX);
        LinearLayout second = Ui.chipRow(root, GAP_PX);
        Ui.addChip(second, "Backup & recovery",
                v -> startActivity(new Intent(this, BackupRecoveryActivity.class)), GAP_PX);
        Ui.addChip(second, "Diagnostic report", v -> exportDiagnostic(), GAP_PX);

        status = Ui.note(this, "No scan has run", NOTE_PX, R.color.text_primary);
        root.addView(status);
        details = Ui.note(this, "", HINT_PX, R.color.text_secondary);
        root.addView(details);

        scroll.addView(root);
        setContentView(scroll);
        root.post(setup::requestFocus);
    }

    private void showScan() {
        try {
            CapabilityReport report = CapabilityReport.scan(loadFamilies());
            int readable = 0;
            int writable = 0;
            int failures = 0;
            StringBuilder detail = new StringBuilder();
            for (CapabilityReport.Entry entry : report.entries) {
                if (entry.readable) readable++;
                if (entry.writable) writable++;
                if (!entry.readable) {
                    failures++;
                    if (failures <= MAX_DETAILS) {
                        if (detail.length() > 0) detail.append("\n");
                        detail.append(entry.title).append(": ").append(entry.error);
                    }
                }
            }
            status.setText("Setup check: " + report.entries.size() + " controls • "
                    + readable + " readable • " + writable + " writable • "
                    + failures + " unavailable");
            if (failures > MAX_DETAILS) {
                detail.append("\n...and ").append(failures - MAX_DETAILS).append(" more");
            }
            details.setText(detail.length() == 0
                    ? "All scanned controls returned values; no settings were changed."
                    : detail.toString());
        } catch (Exception error) {
            status.setText("Setup check unavailable: " + Errors.describe(error));
            details.setText("No settings were changed");
        }
    }

    private void exportDiagnostic() {
        try {
            CapabilityReport report = CapabilityReport.scan(loadFamilies());
            pendingDiagnostic = report.toDiagnosticJson(appVersion(), Build.FINGERPRINT,
                    Build.VERSION.SDK_INT, utcNow());
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("application/json")
                    .putExtra(Intent.EXTRA_TITLE, "hy450-diagnostic.json");
            startActivityForResult(intent, CREATE_DIAGNOSTIC);
            status.setText("Choose where to save the diagnostic report");
            details.setText("Cancelling writes no file");
        } catch (ActivityNotFoundException | SecurityException error) {
            pendingDiagnostic = null;
            status.setText("Diagnostic export unavailable: " + Errors.describe(error));
            details.setText("No file was written");
        } catch (Exception error) {
            pendingDiagnostic = null;
            status.setText("Diagnostic scan unavailable: " + Errors.describe(error));
            details.setText("No file was written");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != CREATE_DIAGNOSTIC) return;
        JSONObject document = pendingDiagnostic;
        pendingDiagnostic = null;
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            status.setText("Diagnostic export cancelled");
            details.setText("No file was written");
            return;
        }
        if (document == null) {
            status.setText("Diagnostic export no longer available");
            details.setText("No file was written");
            return;
        }
        Uri target = data.getData();
        try (OutputStream output = getContentResolver().openOutputStream(target)) {
            if (output == null) throw new IllegalStateException("Document stream unavailable");
            BoundedDocument.write(output, document.toString(2).getBytes(StandardCharsets.UTF_8));
            status.setText("Diagnostic report exported");
            details.setText("Only the selected document was written");
        } catch (Exception error) {
            status.setText("Diagnostic export failed: " + Errors.describe(error));
            details.setText("No settings were changed");
        }
    }

    private Map<String, List<Control>> loadFamilies() throws Exception {
        Map<String, List<Control>> families = new LinkedHashMap<>();
        families.put("picture", ControlPageActivity.loadControls(this, "picture"));
        families.put("audio", ControlPageActivity.loadControls(this, "audio"));
        families.put("source", ControlPageActivity.loadControls(this, "source"));
        return families;
    }

    private String appVersion() throws Exception {
        return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
    }

    private static String utcNow() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date());
    }
}
