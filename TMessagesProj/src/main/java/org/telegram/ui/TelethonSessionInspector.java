package org.telegram.ui;

// TELETHON_PATCH_STAGE1
// Read-only local inspection of Telethon v1 SQLite sessions; never logs or exports auth_key.
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;

public final class TelethonSessionInspector {
    private TelethonSessionInspector() {}

    public static final class Info {
        public final int datacenterId;
        Info(int dc) { datacenterId = dc; }
    }

    public static Info inspect(Context context, Uri uri) throws IOException {
        File staging = File.createTempFile("inspect-", ".session", context.getCacheDir());
        SQLiteDatabase db = null;
        try {
            try (InputStream input = context.getContentResolver().openInputStream(uri);
                 FileOutputStream output = new FileOutputStream(staging)) {
                if (input == null) throw new IOException("Tidak dapat membuka file");
                byte[] buffer = new byte[8192];
                int n;
                long total = 0;
                while ((n = input.read(buffer)) != -1) {
                    total += n;
                    if (total > 20L * 1024L * 1024L)
                        throw new IOException("Ukuran sesi melebihi 20 MB");
                    output.write(buffer, 0, n);
                }
            }
            db = SQLiteDatabase.openDatabase(staging.getAbsolutePath(), null,
                    SQLiteDatabase.OPEN_READONLY | SQLiteDatabase.NO_LOCALIZED_COLLATORS);
            try (Cursor c = db.rawQuery("SELECT dc_id, auth_key FROM sessions LIMIT 1", null)) {
                if (!c.moveToFirst()) throw new IOException("Tabel sessions kosong");
                int dc = c.getInt(0);
                byte[] key = c.getBlob(1);
                if (dc < 1 || dc > 5 || key == null || key.length != 256)
                    throw new IOException("DC atau auth_key Telethon tidak valid");
                java.util.Arrays.fill(key, (byte) 0);
                return new Info(dc);
            }
        } catch (android.database.sqlite.SQLiteException e) {
            throw new IOException("Bukan SQLite session Telethon yang valid", e);
        } finally {
            if (db != null) db.close();
            if (!staging.delete()) staging.deleteOnExit();
        }
    }
}
