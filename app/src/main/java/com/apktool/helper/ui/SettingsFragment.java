package com.apktool.helper.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.apktool.helper.R;
import com.apktool.helper.service.ApkProcessService;
import com.apktool.helper.viewmodel.ApkTaskViewModel;
import com.google.android.material.textfield.TextInputEditText;

import java.io.File;

public class SettingsFragment extends Fragment implements ApkProcessService.ProcessCallback {

    private ApkTaskViewModel viewModel;
    private TextInputEditText etKeystorePath, etKeystorePass, etKeyAlias, etKeyPass;
    private Button btnGenerate, btnSign;
    private TextView tvSignLog;
    private ActivityResultLauncher<String[]> openKeystore;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_settings, container, false);
        viewModel = new ViewModelProvider(requireActivity()).get(ApkTaskViewModel.class);

        etKeystorePath = root.findViewById(R.id.et_keystore_path);
        etKeystorePass = root.findViewById(R.id.et_keystore_pass);
        etKeyAlias = root.findViewById(R.id.et_key_alias);
        etKeyPass = root.findViewById(R.id.et_key_pass);
        btnGenerate = root.findViewById(R.id.btn_generate_keystore);
        btnSign = root.findViewById(R.id.btn_sign);
        tvSignLog = root.findViewById(R.id.tv_sign_log);

        etKeystorePath.setText(viewModel.getKeystorePath().getValue());
        etKeystorePass.setText(viewModel.getKeystorePass().getValue());
        etKeyAlias.setText(viewModel.getKeyAlias().getValue());
        etKeyPass.setText(viewModel.getKeyPass().getValue());

        openKeystore = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> {
                    if (uri != null) {
                        etKeystorePath.setText(uri.getPath());
                        viewModel.setKeystorePath(uri.getPath());
                    }
                });

        etKeystorePath.setOnClickListener(v -> openKeystore.launch(new String[]{"*/*"}));

        btnGenerate.setOnClickListener(v -> generateDebugKeystore());
        btnSign.setOnClickListener(v -> startSigning());

        return root;
    }

    private void generateDebugKeystore() {
        File dir = new File(requireContext().getExternalFilesDir(null), "keystore");
        dir.mkdirs();
        File ks = new File(dir, "debug.keystore");

        try {
            // Use keytool to generate keystore
            String javaHome = System.getProperty("java.home");
            String keytool = javaHome + "/bin/keytool";

            ProcessBuilder pb = new ProcessBuilder(
                    keytool, "-genkey", "-v",
                    "-keystore", ks.getAbsolutePath(),
                    "-alias", "androiddebugkey",
                    "-keyalg", "RSA",
                    "-keysize", "2048",
                    "-validity", "10000",
                    "-storepass", "android",
                    "-keypass", "android",
                    "-dname", "CN=Android Debug,O=Android,C=US"
            );
            Process process = pb.start();
            int exit = process.waitFor();

            if (exit == 0) {
                etKeystorePath.setText(ks.getAbsolutePath());
                viewModel.setKeystorePath(ks.getAbsolutePath());
                tvSignLog.setText("Debug keystore generated:\n" + ks.getAbsolutePath());
            } else {
                // If keytool not available on device, create a simple status update
                tvSignLog.setText("Note: keytool not available on this device.\n" +
                        "Will use jarsigner with a pre-installed debug.keystore.\n" +
                        "Place your debug.keystore at:\n" + ks.getAbsolutePath());
            }
        } catch (Exception e) {
            tvSignLog.setText("Error generating keystore: " + e.getMessage() +
                    "\n\nYou can manually place a debug.keystore at:\n" +
                    ks.getAbsolutePath());
        }
    }

    private void startSigning() {
        String apkPath = viewModel.getSelectedApkPath().getValue();
        if (apkPath == null || apkPath.isEmpty()) {
            tvSignLog.setText("Error: No APK file selected. Go to Home tab to select one.");
            return;
        }

        String ks = etKeystorePath.getText().toString();
        String storePass = etKeystorePass.getText().toString();
        String alias = etKeyAlias.getText().toString();
        String keyPass = etKeyPass.getText().toString();

        viewModel.setKeystorePath(ks);
        viewModel.setKeystorePass(storePass);
        viewModel.setKeyAlias(alias);
        viewModel.setKeyPass(keyPass);

        tvSignLog.setText("");
        String outApk = apkPath.replace(".apk", "_signed.apk");

        ApkProcessService.setCallback(this);
        Intent intent = new Intent(requireContext(), ApkProcessService.class);
        intent.putExtra("action", ApkProcessService.ACTION_SIGN);
        intent.putExtra(ApkProcessService.EXTRA_INPUT, apkPath);
        intent.putExtra(ApkProcessService.EXTRA_OUTPUT, outApk);
        intent.putExtra(ApkProcessService.EXTRA_KEYSTORE, ks);
        intent.putExtra(ApkProcessService.EXTRA_STORE_PASS, storePass);
        intent.putExtra(ApkProcessService.EXTRA_KEY_ALIAS, alias);
        intent.putExtra(ApkProcessService.EXTRA_KEY_PASS, keyPass);

        requireContext().startService(intent);
    }

    @Override
    public void onLog(String message) {
        tvSignLog.append(message + "\n");
    }

    @Override
    public void onComplete(boolean success, String message) {
        tvSignLog.append(success ? "\nSuccess: " + message : "\nFailed: " + message);
        ApkProcessService.clearCallback();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        ApkProcessService.clearCallback();
    }
}
