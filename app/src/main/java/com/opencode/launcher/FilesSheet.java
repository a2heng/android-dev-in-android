package com.opencode.launcher;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.content.FileProvider;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Explorador de archivos del HOME privado, dentro del menu flotante.
 *
 * Permite ver las carpetas y archivos reales del proyecto y sacarlos del
 * movil de dos maneras:
 *   - Compartir: hoja del sistema (ACTION_SEND) con FileProvider.
 *   - Guardar en el telefono: ACTION_CREATE_DOCUMENT (Descargas/Documentos).
 * Las carpetas se empaquetan en un ZIP antes de compartirlas o guardarlas.
 */
public class FilesSheet {
    private final Activity activity;
    private final BiFunction<String, String, String> t;
    private final Consumer<String> snack;
    private final Consumer<Intent> exportLauncher;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final File home;

    private BottomSheetDialog dialog;
    private File current;

    /** Exportacion en curso: origen y ZIP temporal que hay que borrar al acabar. */
    private File pendingSource;
    private File pendingTemp;
    private String pendingName;

    public FilesSheet(Activity activity,
                      BiFunction<String, String, String> t,
                      Consumer<String> snack,
                      Consumer<Intent> exportLauncher) {
        this.activity = activity;
        this.t = t;
        this.snack = snack;
        this.exportLauncher = exportLauncher;
        this.home = LocalRuntime.homeDir(activity);
    }

    public void show() {
        File start = new File(home, "workspace");
        show(start.isDirectory() ? start : home);
    }

    public void show(File dir) {
        if (dir == null || !dir.isDirectory()) dir = home;
        current = dir;
        if (dialog != null) {
            try {
                dialog.dismiss();
            } catch (Exception ignored) {
            }
        }
        dialog = new BottomSheetDialog(activity);
        dialog.setContentView(build());
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
        // La lista se desplaza dentro de la hoja: arrastrar la hoja hacia
        // abajo no debe cerrarla ni moverla (antes competia con el scroll).
        try {
            BottomSheetBehavior<?> behavior = dialog.getBehavior();
            behavior.setDraggable(false);
            behavior.setSkipCollapsed(true);
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        } catch (Exception ignored) {
        }
    }

    private View build() {
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(0, dp(4), 0, dp(8));

        TextView title = new TextView(activity);
        title.setText(t.apply("Archivos", "Files"));
        title.setTextSize(18);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setPadding(dp(20), dp(8), dp(20), 0);
        root.addView(title);

        TextView path = new TextView(activity);
        path.setText(pretty(current));
        path.setTextSize(12);
        path.setMaxLines(1);
        path.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        path.setPadding(dp(20), dp(2), dp(20), dp(8));
        path.setTextColor(secondaryColor());
        root.addView(path);

        LinearLayout bar = new LinearLayout(activity);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(12), 0, dp(12), dp(4));
        if (!sameFile(current, home)) {
            bar.addView(action(t.apply("↑ Subir", "↑ Up"), () -> {
                File parent = current.getParentFile();
                if (parent != null) show(parent);
            }));
        }
        bar.addView(action(t.apply("Nueva carpeta", "New folder"),
                this::newFolder));
        bar.addView(action(t.apply("Compartir carpeta", "Share folder"),
                () -> confirmShare(current)));
        root.addView(bar);

        View sep = new View(activity);
        sep.setBackgroundColor(dividerColor());
        root.addView(sep, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(1))));

        ScrollView scroll = new ScrollView(activity);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        scroll.setLayoutParams(sp);
        LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, dp(4), 0, dp(4));
        scroll.addView(list, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll);

        int count = fillList(list);
        int maxHeight = (int) (activity.getResources().getDisplayMetrics().heightPixels * 0.58f);
        int wanted = dp(70) * Math.max(1, count) + dp(10);
        sp.height = Math.min(maxHeight, wanted);
        scroll.setLayoutParams(sp);
        return root;
    }

    private int fillList(LinearLayout list) {
        list.removeAllViews();
        File[] all = current.listFiles();
        if (all == null) all = new File[0];
        List<File> entries = new ArrayList<>(Arrays.asList(all));
        entries.sort((a, b) -> {
            boolean ha = a.getName().startsWith(".");
            boolean hb = b.getName().startsWith(".");
            if (ha != hb) return ha ? 1 : -1;
            if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
            return a.getName().compareToIgnoreCase(b.getName());
        });
        if (entries.isEmpty()) {
            TextView empty = new TextView(activity);
            empty.setText(t.apply("Carpeta vacía", "Empty folder"));
            empty.setTextSize(14);
            empty.setPadding(dp(20), dp(20), dp(20), dp(20));
            empty.setTextColor(secondaryColor());
            list.addView(empty);
            return 1;
        }
        for (File f : entries) list.addView(row(f));
        return entries.size();
    }

    private View row(File f) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(20), dp(10), dp(12), dp(10));
        TypedValue bg = new TypedValue();
        activity.getTheme().resolveAttribute(
                android.R.attr.selectableItemBackground, bg, true);
        row.setBackgroundResource(bg.resourceId);
        row.setClickable(true);

        ImageView icon = new ImageView(activity);
        icon.setImageResource(f.isDirectory() ? R.drawable.ic_folder : R.drawable.ic_file);
        icon.setColorFilter(secondaryColor());
        row.addView(icon, new LinearLayout.LayoutParams(dp(24), dp(24)));

        LinearLayout col = new LinearLayout(activity);
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        cp.leftMargin = dp(14);
        col.setLayoutParams(cp);
        TextView name = new TextView(activity);
        name.setText(f.getName());
        name.setTextSize(15);
        name.setMaxLines(1);
        name.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        col.addView(name);
        TextView sub = new TextView(activity);
        sub.setTextSize(11);
        sub.setTextColor(secondaryColor());
        sub.setText(f.isDirectory()
                ? t.apply("Carpeta", "Folder")
                : human(f.length()));
        col.addView(sub);
        row.addView(col);

        // Sin boton de compartir: se comparte con una pulsacion larga.
        row.setOnClickListener(v -> {
            if (f.isDirectory()) show(f);
            else actions(f);
        });
        row.setOnLongClickListener(v -> {
            actions(f);
            return true;
        });
        return row;
    }

    /** Crea una carpeta dentro de la carpeta actual. */
    private void newFolder() {
        EditText input = new EditText(activity);
        input.setHint(t.apply("Nombre de la carpeta", "Folder name"));
        input.setSingleLine(true);
        int p = dp(20);
        input.setPadding(p, dp(8), p, dp(8));
        new MaterialAlertDialogBuilder(activity)
                .setTitle(t.apply("Nueva carpeta", "New folder"))
                .setView(input)
                .setPositiveButton(t.apply("Crear", "Create"), (d, w) -> {
                    String name = input.getText() == null
                            ? "" : input.getText().toString().trim();
                    if (name.isEmpty() || name.contains("/") || name.equals(".")
                            || name.equals("..")) {
                        snack.accept(t.apply("Nombre no válido", "Invalid name"));
                        return;
                    }
                    File dir = new File(current, name);
                    if (dir.exists()) {
                        snack.accept(t.apply("Ya existe", "Already exists"));
                        return;
                    }
                    if (dir.mkdirs()) {
                        snack.accept(t.apply("Carpeta creada", "Folder created"));
                        show(current);
                    } else {
                        snack.accept(t.apply("No se pudo crear", "Could not create"));
                    }
                })
                .setNegativeButton(t.apply("Cancelar", "Cancel"), null)
                .show();
    }

    private void actions(File f) {
        List<String> labels = new ArrayList<>();
        List<Runnable> runs = new ArrayList<>();
        if (f.isDirectory()) {
            labels.add(t.apply("Abrir", "Open"));
            runs.add(() -> show(f));
            labels.add(t.apply("Compartir carpeta (ZIP)", "Share folder (ZIP)"));
            runs.add(() -> confirmShare(f));
            labels.add(t.apply("Guardar carpeta (ZIP)", "Save folder (ZIP)"));
            runs.add(() -> zipAndSave(f));
        } else {
            labels.add(t.apply("Compartir archivo", "Share file"));
            runs.add(() -> shareFile(f));
            labels.add(t.apply("Guardar en el teléfono", "Save to phone"));
            runs.add(() -> exportFile(f));
        }
        new MaterialAlertDialogBuilder(activity)
                .setTitle(f.getName())
                .setItems(labels.toArray(new String[0]), (d, which) -> runs.get(which).run())
                .setNegativeButton(t.apply("Cancelar", "Cancel"), null)
                .show();
    }

    private void confirmShare(File dir) {
        long size = treeSize(dir);
        new MaterialAlertDialogBuilder(activity)
                .setTitle(t.apply("Compartir carpeta", "Share folder"))
                .setMessage(t.apply(
                        "Se comprimirá en un ZIP y se abrirá la hoja de compartir:",
                        "It will be zipped and the share sheet will open:")
                        + "\n" + dir.getName() + " (" + human(size) + ")")
                .setPositiveButton(t.apply("Compartir", "Share"), (d, w) -> zipThen(dir, this::shareFile))
                .setNegativeButton(t.apply("Cancelar", "Cancel"), null)
                .show();
    }

    private void zipAndSave(File dir) {
        long size = treeSize(dir);
        new MaterialAlertDialogBuilder(activity)
                .setTitle(t.apply("Guardar carpeta", "Save folder"))
                .setMessage(t.apply(
                        "Se comprimirá en un ZIP para guardarlo en Descargas o Documentos:",
                        "It will be zipped so you can save it to Downloads or Documents:")
                        + "\n" + dir.getName() + " (" + human(size) + ")")
                .setPositiveButton(t.apply("Guardar", "Save"), (d, w) -> zipThen(dir, zip -> {
                    pendingTemp = zip;
                    export(zip, zip.getName(), "application/zip");
                }))
                .setNegativeButton(t.apply("Cancelar", "Cancel"), null)
                .show();
    }

    private interface FileDone {
        void run(File file);
    }

    private void zipThen(File dir, FileDone done) {
        final androidx.appcompat.app.AlertDialog[] progress =
                new androidx.appcompat.app.AlertDialog[1];
        progress[0] = new MaterialAlertDialogBuilder(activity)
                .setTitle(t.apply("Comprimiendo…", "Zipping…"))
                .setMessage(dir.getName())
                .setCancelable(false)
                .show();
        new Thread(() -> {
            File zip = null;
            String error = null;
            try {
                zip = zip(dir);
            } catch (Exception e) {
                error = String.valueOf(e.getMessage());
            }
            final File out = zip;
            final String err = error;
            ui.post(() -> {
                try {
                    if (progress[0] != null) progress[0].dismiss();
                } catch (Exception ignored) {
                }
                if (err != null) snack.accept(t.apply("No se pudo comprimir", "Could not zip")
                        + " (" + err + ")");
                else done.run(out);
            });
        }, "adia-zip").start();
    }

    private void shareFile(File f) {
        try {
            Uri uri = FileProvider.getUriForFile(activity,
                    activity.getPackageName() + ".fileprovider", f);
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType(mimeOf(f));
            send.putExtra(Intent.EXTRA_STREAM, uri);
            send.putExtra(Intent.EXTRA_SUBJECT, f.getName());
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activity.startActivity(Intent.createChooser(send,
                    t.apply("Compartir", "Share")));
        } catch (ActivityNotFoundException e) {
            snack.accept(t.apply("No hay ninguna app para compartir",
                    "No app available to share"));
        } catch (Exception e) {
            snack.accept(t.apply("No se pudo compartir", "Could not share")
                    + " (" + e.getMessage() + ")");
        }
    }

    private void exportFile(File f) {
        export(f, f.getName(), mimeOf(f));
    }

    private void export(File source, String name, String mime) {
        pendingSource = source;
        pendingName = name;
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType(mime);
        i.putExtra(Intent.EXTRA_TITLE, name);
        try {
            exportLauncher.accept(i);
        } catch (Exception e) {
            cleanupPending();
            snack.accept(t.apply("No se pudo abrir el selector de archivos",
                    "Could not open the file picker"));
        }
    }

    /** La actividad reenvía aquí el resultado de ACTION_CREATE_DOCUMENT. */
    public boolean onExportResult(int resultCode, Intent data) {
        File source = pendingSource;
        String name = pendingName;
        File temp = pendingTemp;
        pendingSource = null;
        pendingName = null;
        pendingTemp = null;
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) {
            if (temp != null) temp.delete();
            return true;
        }
        if (source == null || !source.exists()) {
            if (temp != null) temp.delete();
            snack.accept(t.apply("El archivo ya no existe", "The file no longer exists"));
            return true;
        }
        Uri target = data.getData();
        new Thread(() -> {
            String error = null;
            try (InputStream in = new FileInputStream(source);
                 OutputStream out = activity.getContentResolver().openOutputStream(target)) {
                if (out == null) throw new IOException("sin destino");
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                out.flush();
            } catch (Exception e) {
                error = String.valueOf(e.getMessage());
            } finally {
                if (temp != null) temp.delete();
            }
            final String err = error;
            ui.post(() -> {
                if (err != null) {
                    snack.accept(t.apply("No se pudo guardar", "Could not save")
                            + " (" + err + ")");
                } else {
                    snack.accept(t.apply("Guardado: ", "Saved: ") + name);
                }
            });
        }, "adia-export").start();
        return true;
    }

    private void cleanupPending() {
        if (pendingTemp != null) {
            pendingTemp.delete();
            pendingTemp = null;
        }
        pendingSource = null;
        pendingName = null;
    }

    private File zip(File dir) throws IOException {
        File outDir = new File(activity.getCacheDir(), "share");
        if (!outDir.exists() && !outDir.mkdirs()) throw new IOException("sin cache");
        File zip = new File(outDir, safeName(dir.getName()) + ".zip");
        if (zip.exists() && !zip.delete()) throw new IOException("zip ocupado");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zip))) {
            addTree(dir, dir, zos);
        }
        return zip;
    }

    private void addTree(File root, File f, ZipOutputStream zos) throws IOException {
        String rootCanon;
        String fileCanon;
        try {
            rootCanon = root.getCanonicalPath();
            fileCanon = f.getCanonicalPath();
        } catch (IOException e) {
            return;
        }
        // No seguir enlaces que salgan de la carpeta (sdcard montada, etc.).
        if (!fileCanon.equals(rootCanon)
                && !fileCanon.startsWith(rootCanon + File.separator)) {
            return;
        }
        String base = root.getParentFile() == null
                ? root.getName() : root.getParentFile().getAbsolutePath();
        String path = f.getAbsolutePath();
        String entry = path.startsWith(base + File.separator)
                ? path.substring(base.length() + 1) : f.getName();
        entry = entry.replace(File.separatorChar, '/');
        if (f.isDirectory()) {
            zos.putNextEntry(new ZipEntry(entry + "/"));
            zos.closeEntry();
            File[] kids = f.listFiles();
            if (kids != null) {
                Arrays.sort(kids, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
                for (File k : kids) addTree(root, k, zos);
            }
        } else {
            try {
                zos.putNextEntry(new ZipEntry(entry));
                try (InputStream in = new FileInputStream(f)) {
                    byte[] buf = new byte[64 * 1024];
                    int n;
                    while ((n = in.read(buf)) > 0) zos.write(buf, 0, n);
                }
                zos.closeEntry();
            } catch (IOException e) {
                // Un archivo sin permisos no debe abortar todo el ZIP.
                try {
                    zos.closeEntry();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static String safeName(String name) {
        String n = name == null ? "carpeta" : name.replaceAll("[^A-Za-z0-9._-]", "_");
        return n.isEmpty() ? "carpeta" : n;
    }

    private long treeSize(File f) {
        if (f.isFile()) return f.length();
        long total = 0;
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) total += treeSize(k);
        return total;
    }

    private String mimeOf(File f) {
        String name = f.getName();
        int dot = name.lastIndexOf('.');
        if (dot >= 0) {
            String ext = name.substring(dot + 1).toLowerCase(Locale.US);
            String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
            if (mime != null) return mime;
        }
        return "application/octet-stream";
    }

    private String human(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format(Locale.US, "%.0f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb);
        return String.format(Locale.US, "%.2f GB", mb / 1024.0);
    }

    private String pretty(File f) {
        String base = home.getAbsolutePath();
        String path = f.getAbsolutePath();
        if (path.equals(base)) return "~";
        if (path.startsWith(base + "/")) return "~/" + path.substring(base.length() + 1);
        return path;
    }

    private static boolean sameFile(File a, File b) {
        return a != null && b != null
                && a.getAbsolutePath().equals(b.getAbsolutePath());
    }

    private TextView action(String label, Runnable run) {
        TextView b = new TextView(activity);
        b.setText(label);
        b.setTextSize(13);
        b.setPadding(dp(12), dp(8), dp(12), dp(8));
        TypedValue bg = new TypedValue();
        activity.getTheme().resolveAttribute(
                android.R.attr.selectableItemBackground, bg, true);
        b.setBackgroundResource(bg.resourceId);
        b.setClickable(true);
        b.setOnClickListener(v -> run.run());
        return b;
    }

    private int secondaryColor() {
        TypedValue tv = new TypedValue();
        activity.getTheme().resolveAttribute(android.R.attr.textColorSecondary, tv, true);
        if (tv.resourceId != 0) return activity.getColor(tv.resourceId);
        return tv.data;
    }

    private int dividerColor() {
        TypedValue tv = new TypedValue();
        activity.getTheme().resolveAttribute(
                com.google.android.material.R.attr.colorOutlineVariant, tv, true);
        if (tv.resourceId != 0) return activity.getColor(tv.resourceId);
        return 0x22808080;
    }

    private int dp(int v) {
        return Math.round(v * activity.getResources().getDisplayMetrics().density);
    }
}
