package com.harleytg.filewiper;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.ContentUris;
import android.provider.MediaStore;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.system.Os;
import android.system.OsConstants;
import android.view.View;
import android.view.WindowInsets;
import android.graphics.Insets;
import android.widget.*;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int SELECT_FILES = 100;
    private static final int MEDIA_DELETE_REQUEST = 101;
    private final ArrayList<Uri> pendingMediaDelete = new ArrayList<>();
    private final ArrayList<SelectedFile> files = new ArrayList<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final SecureRandom random = new SecureRandom();
    private TextView selection, status;
    private Button choose, wipe, deleteOnly;
    private Spinner passesSpinner;
    private ProgressBar progress;
    private volatile boolean busy = false;

    static class SelectedFile {
        Uri uri;
        String name, reason;
        long length;
        int flags;
        boolean eligible;
        boolean canDelete;
        Uri mediaUri;
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 24, 24, 24);
        root.setBackgroundColor(0xFF101722);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(root);
        setContentView(scroll);
        // Target SDK 35 draws edge-to-edge. Keep content clear of Samsung's status bar,
        // cutout and navigation area.
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            int top, bottom;
            if (Build.VERSION.SDK_INT >= 30) {
                Insets safe = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                top = safe.top;
                bottom = safe.bottom;
            } else {
                top = insets.getSystemWindowInsetTop();
                bottom = insets.getSystemWindowInsetBottom();
            }
            view.setPadding(0, top, 0, bottom);
            return insets;
        });
        scroll.requestApplyInsets();
        TextView title = label("FILE WIPER", 26, 0xFFFFFFFF);
        root.addView(title);
        root.addView(label("Best-effort logical overwrite and deletion of selected local files. Flash storage may retain older physical data.", 14, 0xFFC6D3E0));
        choose = new Button(this);
        choose.setText("CHOOSE FILES");
        root.addView(choose);
        selection = label("No files selected", 14, 0xFFFFFFFF);
        root.addView(selection);
        passesSpinner = new Spinner(this);
        passesSpinner.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item,
            new String[]{"1 pass: random data", "3 passes: random / zero / random"}));
        root.addView(passesSpinner);
        wipe = new Button(this);
        wipe.setText("OVERWRITE, VERIFY & DELETE");
        root.addView(wipe);
        deleteOnly = new Button(this);
        deleteOnly.setText("DELETE ONLY (NO OVERWRITE)");
        root.addView(deleteOnly);
        root.addView(label("Fallback for protected media: ordinary Android deletion with confirmation. This does NOT securely overwrite the file.", 13, 0xFFDBB7A8));
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setVisibility(View.GONE);
        root.addView(progress);
        status = label("Select local files. Cloud files, folders, and providers without direct read/write access are skipped.", 14, 0xFFB0C6DA);
        root.addView(status);
        choose.setOnClickListener(v -> pick());
        wipe.setOnClickListener(v -> confirm());
        deleteOnly.setOnClickListener(v -> confirmDeleteOnly());
        redraw();
    }

    private TextView label(String s, int size, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setPadding(4, 12, 4, 12);
        return t;
    }

    private void pick() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        startActivityForResult(i, SELECT_FILES);
    }

    @Override protected void onActivityResult(int code, int result, Intent data) {
        super.onActivityResult(code, result, data);
        if (code == MEDIA_DELETE_REQUEST) {
            if (result == RESULT_OK) {
                int count = pendingMediaDelete.size();
                files.removeIf(f -> f.mediaUri != null && pendingMediaDelete.contains(f.mediaUri));
                status.setText("Android confirmed deletion of " + count +
                    " media item(s) WITHOUT overwriting. Backups or cached copies may remain.");
            } else {
                status.setText("Android cancelled or refused media deletion.");
            }
            pendingMediaDelete.clear();
            redraw();
            return;
        }
        if (code != SELECT_FILES || result != RESULT_OK || data == null || busy) return;
        LinkedHashSet<Uri> uris = new LinkedHashSet<>();
        if (data.getData() != null) uris.add(data.getData());
        if (data.getClipData() != null)
            for (int n = 0; n < data.getClipData().getItemCount(); n++)
                uris.add(data.getClipData().getItemAt(n).getUri());
        files.clear();
        for (Uri uri : uris) {
            try { files.add(inspect(uri)); }
            catch (Exception error) { status.setText("Could not inspect some files: " + error.getMessage()); }
        }
        redraw();
    }

    private SelectedFile inspect(Uri uri) throws Exception {
        if (!DocumentsContract.isDocumentUri(this, uri)) throw new IOException("Not a document");
        String[] columns = {DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_SIZE, DocumentsContract.Document.COLUMN_FLAGS,
            DocumentsContract.Document.COLUMN_MIME_TYPE};
        try (android.database.Cursor c = getContentResolver().query(uri, columns, null, null, null)) {
            if (c == null || !c.moveToFirst()) throw new IOException("Metadata unavailable");
            SelectedFile f = new SelectedFile();
            f.uri = uri;
            f.name = readString(c, DocumentsContract.Document.COLUMN_DISPLAY_NAME, "Unnamed");
            int sizeCol = c.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE);
            int flagsCol = c.getColumnIndex(DocumentsContract.Document.COLUMN_FLAGS);
            f.length = sizeCol < 0 || c.isNull(sizeCol) ? -1 : c.getLong(sizeCol);
            f.flags = flagsCol < 0 || c.isNull(flagsCol) ? 0 : c.getInt(flagsCol);
            String mime = readString(c, DocumentsContract.Document.COLUMN_MIME_TYPE, "");
            String a = uri.getAuthority();
            boolean local = "com.android.externalstorage.documents".equals(a) ||
                "com.android.providers.downloads.documents".equals(a) ||
                "com.android.providers.media.documents".equals(a);
            boolean writable = (f.flags & DocumentsContract.Document.FLAG_SUPPORTS_WRITE) != 0;
            boolean deletable = (f.flags & DocumentsContract.Document.FLAG_SUPPORTS_DELETE) != 0;
            boolean virtual = (f.flags & DocumentsContract.Document.FLAG_VIRTUAL_DOCUMENT) != 0;
            boolean directory = DocumentsContract.Document.MIME_TYPE_DIR.equals(mime);
            // Flags can be conservative. Probe a non-truncating, seekable r/w
            // descriptor without modifying bytes, and refuse unsupported providers.
            boolean probeWritable = local && !virtual && !directory && f.length >= 0 &&
                supportsSeekableReadWrite(f.uri, f.length);
            f.mediaUri = mediaStoreUri(f.uri);
            f.canDelete = local && !virtual && !directory &&
                (deletable || (f.mediaUri != null && Build.VERSION.SDK_INT >= 30));
            f.eligible = local && probeWritable && deletable && !virtual && !directory && f.length >= 0;
            f.reason = !local ? "Unsupported (non-local) provider" :
                virtual || directory ? "Not a regular file" :
                f.length < 0 ? "Unknown file length" :
                !probeWritable ? (f.canDelete ? "Overwrite blocked; DELETE ONLY available" :
                    "Write access unavailable (Android storage provider restriction)") :
                !deletable ? "Provider does not allow deletion after overwriting" :
                "Ready";
            return f;
        }
    }

    private boolean supportsSeekableReadWrite(Uri uri, long length) {
        try (ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(uri, "rw")) {
            if (pfd == null || pfd.getStatSize() != length) return false;
            java.io.FileDescriptor fd = pfd.getFileDescriptor();
            if (Os.lseek(fd, 0, OsConstants.SEEK_END) != length) return false;
            return Os.lseek(fd, 0, OsConstants.SEEK_SET) == 0;
        } catch (Exception ignored) {
            return false;
        }
    }

    // DocumentsProvider media IDs such as "image:123" correspond to
    // Android MediaStore entries. Only the OS may approve their deletion.
    private Uri mediaStoreUri(Uri documentUri) {
        if (!"com.android.providers.media.documents".equals(documentUri.getAuthority())) return null;
        try {
            String[] parts = DocumentsContract.getDocumentId(documentUri).split(":");
            if (parts.length != 2) return null;
            long id = Long.parseLong(parts[1]);
            Uri collection;
            switch (parts[0]) {
                case "image": collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI; break;
                case "video": collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI; break;
                case "audio": collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI; break;
                default: return null;
            }
            return ContentUris.withAppendedId(collection, id);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String readString(android.database.Cursor c, String column, String fallback) {
        int i = c.getColumnIndex(column);
        return i < 0 || c.isNull(i) ? fallback : c.getString(i);
    }

    private void redraw() {
        int ready = 0;
        int deletable = 0;
        StringBuilder s = new StringBuilder();
        for (SelectedFile f : files) {
            if (f.eligible) ready++;
            if (f.canDelete) deletable++;
            s.append(f.eligible ? "✓ " : "× ").append(f.name).append(" · ")
                .append(f.length < 0 ? "unknown" : f.length + " bytes")
                .append("\n   ").append(f.reason).append("\n");
        }
        selection.setText(files.isEmpty() ? "No files selected" : ready + "/" + files.size() + " eligible\n\n" + s);
        choose.setEnabled(!busy);
        passesSpinner.setEnabled(!busy);
        wipe.setEnabled(!busy && ready > 0);
        deleteOnly.setEnabled(!busy && deletable > 0);
    }

    private void confirm() {
        if (busy) return;
        ArrayList<SelectedFile> ready = new ArrayList<>();
        for (SelectedFile f : files) if (f.eligible) ready.add(f);
        if (ready.isEmpty()) return;
        int passes = passesSpinner.getSelectedItemPosition() == 1 ? 3 : 1;
        new AlertDialog.Builder(this)
            .setTitle("Irreversibly overwrite " + ready.size() + " file(s)?")
            .setMessage("Selected files will be overwritten " + passes + " time(s), checked, then deleted. There is no undo. This cannot guarantee physical flash-media sanitization.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("OVERWRITE & DELETE", (dialog, which) -> start(ready, passes))
            .show();
    }

    private void start(ArrayList<SelectedFile> work, int passes) {
        busy = true;
        redraw();
        progress.setVisibility(View.VISIBLE);
        progress.setMax(work.size());
        progress.setProgress(0);
        status.setText("Processing. Keep the app open.");
        executor.execute(() -> {
            ArrayList<Uri> completed = new ArrayList<>();
            StringBuilder report = new StringBuilder();
            for (int index = 0; index < work.size(); index++) {
                SelectedFile f = work.get(index);
                final int number = index + 1;
                try {
                    SelectedFile current = inspect(f.uri);
                    if (!current.eligible || current.length != f.length)
                        throw new IOException("Permissions or size changed; not modified");
                    for (int pass = 0; pass < passes; pass++) {
                        int currentPass = pass + 1;
                        runOnUiThread(() -> status.setText("File " + number + "/" + work.size() + ": " + f.name + "\nPass " + currentPass + "/" + passes));
                        byte[] expected = overwrite(f.uri, f.length, passes == 3 && pass == 1);
                        byte[] actual = verify(f.uri, f.length);
                        if (!MessageDigest.isEqual(expected, actual)) throw new IOException("Read-back check failed; file not deleted");
                    }
                    if (!DocumentsContract.deleteDocument(getContentResolver(), f.uri))
                        throw new IOException("Provider refused deletion; overwritten file may remain");
                    completed.add(f.uri);
                    report.append("✓ ").append(f.name).append(": overwritten, verified and deleted\n");
                } catch (Exception e) {
                    report.append("× ").append(f.name).append(": ").append(e.getMessage()).append(". Not deleted by this app.\n");
                }
                runOnUiThread(() -> progress.setProgress(number));
            }
            runOnUiThread(() -> {
                files.removeIf(f -> completed.contains(f.uri));
                busy = false;
                redraw();
                status.setText(completed.size() + " deleted; " + (work.size() - completed.size()) +
                    " failed/skipped.\n\n" + report + "\nFlash-media physical erasure is not guaranteed.");
            });
        });
    }

    private byte[] overwrite(Uri uri, long size, boolean zeros) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(uri, "rw")) {
            if (pfd == null || pfd.getStatSize() != size) throw new IOException("No seekable r/w descriptor with matching size");
            java.io.FileDescriptor fd = pfd.getFileDescriptor();
            if (Os.lseek(fd, 0, OsConstants.SEEK_END) != size) throw new IOException("Invalid file length");
            Os.lseek(fd, 0, OsConstants.SEEK_SET);
            byte[] b = new byte[65536];
            long remaining = size;
            while (remaining > 0) {
                int count = (int)Math.min(remaining, b.length);
                if (zeros) Arrays.fill(b, (byte)0); else random.nextBytes(b);
                digest.update(b, 0, count);
                int pos = 0;
                while (pos < count) {
                    int n = Os.write(fd, b, pos, count - pos);
                    if (n <= 0) throw new IOException("Short write");
                    pos += n;
                }
                remaining -= count;
            }
            Os.fsync(fd);
            if (Os.lseek(fd, 0, OsConstants.SEEK_END) != size) throw new IOException("Size changed during write");
        }
        return digest.digest();
    }

    private byte[] verify(Uri uri, long size) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(uri, "r")) {
            if (pfd == null || pfd.getStatSize() != size) throw new IOException("Cannot reopen for verification");
            java.io.FileDescriptor fd = pfd.getFileDescriptor();
            Os.lseek(fd, 0, OsConstants.SEEK_SET);
            byte[] b = new byte[65536];
            long count = 0;
            while (true) {
                int n = Os.read(fd, b, 0, b.length);
                if (n <= 0) break;
                count += n;
                if (count > size) throw new IOException("Verification data length exceeded");
                digest.update(b, 0, n);
            }
            if (count != size) throw new IOException("Verification data length mismatch");
        }
        return digest.digest();
    }

    @Override protected void onDestroy() {
        executor.shutdown();
        super.onDestroy();
    }
}
