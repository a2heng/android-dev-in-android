package com.opencode.launcher;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Process;
import android.util.Log;

import org.json.JSONObject;

/**
 * Entrega la informacion del servidor opencode local (URL + credenciales) a
 * otros clientes del mismo telefono, para que se conecten sin que el usuario
 * tenga que copiar la contrasena a mano.
 *
 * <p>Es el punto de union con OpenCode Mobile (submodulo {@code mobile/}):
 * el cliente consulta {@code content://<applicationId>.local-server/info} y
 * recibe un JSON con {@code url}, {@code username}, {@code password},
 * {@code running} y {@code port}.</p>
 *
 * <p>Solo lectura, exportado y protegido por el permiso normal
 * {@code com.a2heng.adia.permission.LOCAL_SERVER_INFO}. Cualquier app que lo
 * declare en su manifiesto puede consultarlo; no se expone nada mas alla del
 * servidor que ya escucha en 127.0.0.1.</p>
 */
public final class LocalServerInfoProvider extends ContentProvider {
    private static final String TAG = "OpenCodeLocal";
    public static final String AUTHORITY_SUFFIX = ".local-server";
    public static final String PERMISSION =
            BuildConfig.APPLICATION_ID + ".permission.LOCAL_SERVER_INFO";
    static final String[] COLUMNS = {"info"};

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        MatrixCursor cursor = new MatrixCursor(COLUMNS);
        JSONObject info = new JSONObject();
        try {
            Context ctx = getContext();
            info.put("url", "http://127.0.0.1:" + BuildConfig.SERVER_PORT);
            info.put("port", BuildConfig.SERVER_PORT);
            info.put("username", "opencode");
            info.put("password", ctx == null ? "" : LocalRuntime.serverPassword(ctx));
            info.put("running", LocalRuntime.running());
            info.put("pid", Process.myPid());
            info.put("app", "adia");
        } catch (Exception e) {
            Log.w(TAG, "no se pudo preparar la info del servidor local: " + e);
        }
        cursor.addRow(new Object[]{info.toString()});
        return cursor;
    }

    @Override
    public String getType(Uri uri) {
        return "application/json";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("solo lectura");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("solo lectura");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("solo lectura");
    }
}
