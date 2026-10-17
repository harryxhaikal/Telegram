package org.telegram.ui;

// TELETHON_STAGE2A: explicit user-driven export, no network uploads.
import android.content.Context;
import android.content.ContentResolver;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

public final class TelethonSessionExporter {
    private TelethonSessionExporter() {}

    public static void export(Context context, Uri destination, int account) throws IOException {
        if (account < 0 || account >= UserConfig.MAX_ACCOUNT_COUNT ||
            !UserConfig.getInstance(account).isClientActivated()) {
            throw new IOException("Akun tidak login");
        }
        // Telegram native returns DC ID and permanent 256-byte MTProto auth key.
        byte[] raw = ConnectionsManager.native_exportTelethonSessionKey(account);
        if (raw == null || raw.length != 260) throw new IOException("Kunci sesi tidak tersedia");
        File file = null;
        SQLiteDatabase db = null;
        try {
            int dcId = ByteBuffer.wrap(raw, 0, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
            String server;
            switch (dcId) {
                case 1: server = "149.154.175.50"; break;
                case 2: server = "149.154.167.51"; break;
                case 3: server = "149.154.175.100"; break;
                case 4: server = "149.154.167.91"; break;
                case 5: server = "149.154.171.5"; break;
                default: throw new IOException("DC tidak dikenal");
            }
            byte[] key = Arrays.copyOfRange(raw, 4, raw.length);
            try {
                file = File.createTempFile("telethon-export-", ".session", context.getCacheDir());
                db = SQLiteDatabase.openOrCreateDatabase(file, null);
                db.execSQL("PRAGMA journal_mode=DELETE");
                db.execSQL("CREATE TABLE version (version integer primary key)");
                db.execSQL("CREATE TABLE sessions (dc_id integer primary key, server_address text, port integer, auth_key blob, takeout_id integer)");
                db.execSQL("CREATE TABLE entities (id integer primary key, hash integer not null, username text, phone integer, name text, date integer)");
                db.execSQL("CREATE TABLE sent_files (md5_digest blob, file_size integer, type integer, id integer, hash integer, primary key(md5_digest, file_size, type))");
                db.execSQL("CREATE TABLE update_state (id integer primary key, pts integer, qts integer, date integer, seq integer)");
                db.execSQL("INSERT INTO version (version) VALUES (7)");
                db.execSQL("INSERT INTO sessions (dc_id, server_address, port, auth_key, takeout_id) VALUES (?,?,?,?,NULL)", new Object[]{dcId, server, 443, key});
                db.close();
                db = null;
                try (InputStream in = new FileInputStream(file);
                     OutputStream out = context.getContentResolver().openOutputStream(destination, "w")) {
                    if (out == null) throw new IOException("Tidak bisa membuka lokasi tujuan");
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
                    Arrays.fill(buf, (byte) 0);
                }
            } finally {
                Arrays.fill(key, (byte) 0);
            }
        } finally {
            Arrays.fill(raw, (byte) 0);
            if (db != null) db.close();
            if (file != null && file.exists() && !file.delete()) file.deleteOnExit();
        }
    }
}
