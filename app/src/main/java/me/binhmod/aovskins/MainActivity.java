package me.binhmod.aovskins;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.ScrollView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.button.MaterialButton;
import android.content.Intent;
import android.net.Uri;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import android.provider.OpenableColumns;
import android.database.Cursor;
import android.view.View;
import java.io.IOException;
import java.io.InputStream;
import java.io.FileOutputStream;
import java.io.File;
import java.io.BufferedReader;
import java.io.InputStreamReader;

import rikka.shizuku.Shizuku;
import rikka.shizuku.ShizukuRemoteProcess;

public class MainActivity extends AppCompatActivity {
	
	private TextView textView;
	private ScrollView logScrollView;
	private MaterialButton btnSend, btnMoreOptions;
	private ExtendedFloatingActionButton fab;
	
	private static final int REQ_PICK_ZIP = 3001;
	private static final int REQ_CODE = 2004;
	private boolean isInitialized = false;
	private String currentModHeader = ""; 
	private static final String[] CLEAR_CODES = {"[2J\u001B[H", "\u001B[H", "[2J", "\u001B[2J"};
	
	private final Shizuku.OnBinderReceivedListener binderListener = () -> runOnUiThread(this::checkPermission);
	
	private final Shizuku.OnRequestPermissionResultListener permissionListener = (requestCode, grantResult) -> {
		if (requestCode != REQ_CODE) return;
		runOnUiThread(() -> {
			if (grantResult == PackageManager.PERMISSION_GRANTED) {
				isInitialized = false;
				checkPermission();
			} else {
				showShizukuDialog("Quyền Shizuku bị từ chối!");
			}
		});
	};
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		textView = findViewById(R.id.logText);
		logScrollView = findViewById(R.id.logScrollView);
		btnSend = findViewById(R.id.btnSend);
		btnMoreOptions = findViewById(R.id.btnMoreOptions);
		fab = findViewById(R.id.fab);
		
		logScrollView.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
			if (Math.abs(scrollY - oldScrollY) < 10) return;
			if (scrollY > oldScrollY) fab.shrink(); else fab.extend();
		});
		
		fab.setOnClickListener(v -> pickZipFile());
		btnMoreOptions.setOnClickListener(this::showPopupMenu);
		btnSend.setOnClickListener(v -> shareLog());
		
		Shizuku.addBinderReceivedListener(binderListener);
		Shizuku.addRequestPermissionResultListener(permissionListener);
		
		if (Shizuku.pingBinder()) checkPermission();
		else showShizukuDialog("Shizuku chưa chạy!");
		
		String message =
		"binhmod @ github v1.0\n"
		+ "Nguồn chính thức:\nhttps://github.com/binhmod/AoVSkins\n\n"
		+ "LƯU Ý: Tải App từ nguồn chính thức!\nChọn file mod uy tín để tránh bị khóa acc.\nMình không chịu trách nhiệm! <3\nApp có chức năng:\n- Cài nhiều mod cùng lúc\n- Nhanh, gọn lẹ, tiện lợi :3\n- An toàn!\n- snvv nghen";
		
		textView.setText(message);
		copyFunctions();
	}
	
	private void pickZipFile() {
		Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
		i.addCategory(Intent.CATEGORY_OPENABLE);
		i.setType("application/zip");
		i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
		i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/zip", "application/x-zip-compressed"});
		startActivityForResult(i, REQ_PICK_ZIP);
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode == REQ_PICK_ZIP && resultCode == RESULT_OK && data != null) {
			java.util.ArrayList<Uri> uris = new java.util.ArrayList<>();
			if (data.getClipData() != null) {
				for (int i = 0; i < data.getClipData().getItemCount(); i++) uris.add(data.getClipData().getItemAt(i).getUri());
			} else if (data.getData() != null) {
				uris.add(data.getData());
			}
			if (!uris.isEmpty()) processZips(uris);
		}
	}
	
	private String lastLogLine = ""; // Biến phụ để kiểm tra trùng dòng
	
	private void processZips(java.util.ArrayList<Uri> uris) {
		StringBuilder sb = new StringBuilder();
		if (uris.size() == 1) {
			sb.append("Cài đặt bản mod:\n").append(getFileName(uris.get(0)));
		} else {
			sb.append("Bạn có muốn cài đặt gộp ").append(uris.size()).append(" file sau không?\n");
			for (int i = 0; i < uris.size(); i++) {
				sb.append("\n").append(i + 1).append(". ").append(getFileName(uris.get(i)));
			}
		}
		
		showConfirmDialog("Xác nhận", sb.toString(), () -> new Thread(() -> {
			runOnUiThread(() -> { 
				textView.setText(""); 
				currentModHeader = ""; 
				lastLogLine = ""; 
			});
			String script = getExternalFilesDir(null).getAbsolutePath() + "/xuli.sh";
			
			for (int i = 0; i < uris.size(); i++) {
				try {
					Uri u = uris.get(i);
					File cached = copyToCache(u);
					
					// In tiêu đề Mod gọn gàng hơn
					final String separator = "\n─── [ MOD " + (i + 1) + " : " + getFileName(u) + " ] ───";
					
					runOnUiThread(() -> {
						textView.append(separator);
						currentModHeader = textView.getText().toString() + "\n";
					});
					
					runShellCommand("sh " + script + " " + cached.getAbsolutePath(), true);
					
					runOnUiThread(() -> currentModHeader = textView.getText().toString() + "\n");
					cached.delete(); 
				} catch (Exception e) { appendLog("ERR: " + e.getMessage()); }
			}
		}).start());
	}
	
	
	
	private void runShellCommand(String command, boolean wait) {
		try {
			ShizukuRemoteProcess p = Shizuku.newProcess(new String[]{"sh", "-c", command}, null, null);
			BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
			BufferedReader e = new BufferedReader(new InputStreamReader(p.getErrorStream()));
			String line;
			while ((line = r.readLine()) != null) appendLog(line);
			while ((line = e.readLine()) != null) appendLog("ERR: " + line);
			if (wait) p.waitFor();
		} catch (Exception e) { appendLog("Error: " + e.getMessage()); }
	}
	
	private void appendLog(String rawText) {
		String processed = rawText.trim();
		if (processed.isEmpty()) return;
		
		// Chống lặp dòng (Ví dụ: 3 mod đều in cùng 1 dòng Backup giống nhau thì chỉ in 1 lần)
		if (processed.equals(lastLogLine) && !processed.contains("MOD")) return; 
		lastLogLine = processed;
		
		boolean clear = false;
		for (String code : CLEAR_CODES) {
			if (processed.contains(code)) {
				clear = true;
				processed = processed.replace(code, "").trim();
			}
		}
		
		final String finalText = processed;
		final boolean finalClear = clear;
		
		runOnUiThread(() -> {
			if (finalClear) textView.setText(currentModHeader);
			
			// Chỉ thêm dòng nếu nó thực sự có nội dung sau khi replace ANSI
			if (!finalText.isEmpty()) {
				if (textView.getText().length() == 0) textView.setText(finalText);
				else textView.append("\n" + finalText);
			}
			
			logScrollView.post(() -> logScrollView.fullScroll(ScrollView.FOCUS_DOWN));
		});
	}
	
	private void showPopupMenu(View v) {
		androidx.appcompat.widget.PopupMenu p = new androidx.appcompat.widget.PopupMenu(this, v);
		p.getMenu().add(0, 1, 0, "Xóa nhật ký");
		p.getMenu().add(0, 2, 1, "Khôi phục");
		p.getMenu().add(0, 3, 2, "Xóa Resources");
		p.setOnMenuItemClickListener(item -> {
			if (item.getItemId() == 1) {
				textView.setText("");
				currentModHeader = "";
			}
			else if (item.getItemId() == 2) {
				showConfirmDialog("Khôi phục", "Khôi phục gốc vừa mod?", () -> {
					textView.setText(""); // Xóa text khi bắt đầu khôi phục
					currentModHeader = "";
					runShellCommand("sh " + getExternalFilesDir(null) + "/xuli.sh restore", false);
				});
			}
			else if (item.getItemId() == 3) {
				showConfirmDialog("Xóa", "Xóa Resources, sẽ xóa hết tất cả mod.Vào game sẽ tự động tải lại gói tài nguyên.", () -> {
					textView.setText(""); // Xóa text khi bắt đầu xóa res
					currentModHeader = "";
					runShellCommand("rm -rf /storage/emulated/0/Android/data/com.garena.game.kgvn/files/Resources && echo 'Đã dọn dẹp Resources!'", false);
				});
			}
			return true;
		});
		p.show();
	}
	
	private void showConfirmDialog(String t, String m, Runnable onOk) {
		new MaterialAlertDialogBuilder(this).setTitle(t).setMessage(m).setPositiveButton("Đồng ý", (d, w) -> onOk.run()).setNegativeButton("Hủy", null).show();
	}
	
	private String getFileName(Uri uri) {
		String name = "mod.zip";
		Cursor c = getContentResolver().query(uri, null, null, null, null);
		if (c != null && c.moveToFirst()) {
			int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
			if (idx >= 0) name = c.getString(idx);
			c.close();
		}
		return name;
	}
	
	private File copyToCache(Uri uri) throws Exception {
		File out = new File(getCacheDir(), "temp_" + System.currentTimeMillis() + ".zip");
		try (InputStream in = getContentResolver().openInputStream(uri); FileOutputStream fos = new FileOutputStream(out)) {
			byte[] b = new byte[8192]; int l;
			while ((l = in.read(b)) > 0) fos.write(b, 0, l);
		}
		return out;
	}
	
	private void checkPermission() {
		if (isInitialized || !Shizuku.pingBinder()) return;
		if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
			isInitialized = true;
			appendLog("Đã có quyền Shizuku!");
			copyNativeLib();
		} else {
			Shizuku.requestPermission(REQ_CODE);
		}
	}
	
	private void copyNativeLib() {
		String lib = getLibPath(this, "libminiz.so");
		if (lib != null) runShellCommand("mkdir -p /data/local/tmp/binhmod && cp '" + lib + "' /data/local/tmp/binhmod/libminiz.so && chmod 755 /data/local/tmp/binhmod/libminiz.so", false);
	}
	
	private void shareLog() {
		String log = textView.getText().toString();
		if (log.isEmpty()) return;
		Intent s = new Intent(Intent.ACTION_SEND);
		s.setType("text/plain");
		s.putExtra(Intent.EXTRA_TEXT, log);
		startActivity(Intent.createChooser(s, "Chia sẻ nhật kí"));
	}
	
	private void showShizukuDialog(String msg) {
		new MaterialAlertDialogBuilder(this).setTitle("Shizuku").setMessage(msg).setCancelable(false)
		.setPositiveButton("Thoát", (d, w) -> finishAffinity())
		.setNegativeButton("Cài Shizuku", (d, w) -> {
			startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api")));
			finishAffinity();
		}).show();
	}
	
	public String getLibPath(Context c, String name) {
		File f = new File(c.getApplicationInfo().nativeLibraryDir, name);
		return f.exists() ? f.getAbsolutePath() : null;
	}
	
	private void copyFunctions() {
		try {
			File o = new File(getExternalFilesDir(null), "xuli.sh");
			InputStream i = getAssets().open("xuli.sh");
			FileOutputStream fos = new FileOutputStream(o);
			byte[] b = new byte[4096]; int l;
			while ((l = i.read(b)) != -1) fos.write(b, 0, l);
			i.close(); fos.close();
			o.setExecutable(true, false);
		} catch (Exception e) {}
	}
}
