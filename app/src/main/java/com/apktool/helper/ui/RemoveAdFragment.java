package com.apktool.helper.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.apktool.helper.R;
import com.apktool.helper.service.ApkProcessService;
import com.apktool.helper.viewmodel.ApkTaskViewModel;
import com.google.android.material.textfield.TextInputEditText;

import java.io.File;

public class RemoveAdFragment extends Fragment implements ApkProcessService.ProcessCallback {

    private ApkTaskViewModel viewModel;
    private TextInputEditText etApkPath, etOutApk;
    private Button btnRemoveAd, btnAiRemoveAd;
    private ProgressBar progressBar;
    private TextView tvLog;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_remove_ad, container, false);
        viewModel = new ViewModelProvider(requireActivity()).get(ApkTaskViewModel.class);

        etApkPath = root.findViewById(R.id.et_apk_path);
        etOutApk = root.findViewById(R.id.et_out_apk);
        btnRemoveAd = root.findViewById(R.id.btn_remove_ad);
        btnAiRemoveAd = root.findViewById(R.id.btn_ai_remove_ad);
        progressBar = root.findViewById(R.id.progress_bar);
        tvLog = root.findViewById(R.id.tv_log);

        String apk = viewModel.getSelectedApkPath().getValue();
        if (apk != null) etApkPath.setText(apk);
        File defaultOut = new File(requireContext().getExternalFilesDir(null), "apktool/output/output.apk");
        etOutApk.setText(defaultOut.getAbsolutePath());

        viewModel.getLogText().observe(getViewLifecycleOwner(), log -> tvLog.setText(log));
        viewModel.getIsProcessing().observe(getViewLifecycleOwner(), processing -> {
            btnRemoveAd.setEnabled(!processing);
            btnAiRemoveAd.setEnabled(!processing);
            progressBar.setVisibility(processing ? View.VISIBLE : View.GONE);
        });

        btnRemoveAd.setOnClickListener(v -> startRemoveAd());
        btnAiRemoveAd.setOnClickListener(v -> startAiRemoveAd());

        return root;
    }

    private void startRemoveAd() {
        String apkPath = etApkPath.getText().toString();
        String outApk = etOutApk.getText().toString();

        if (apkPath.isEmpty()) {
            viewModel.appendLog("Error: No APK file selected");
            return;
        }

        viewModel.setOutputPath(outApk);
        viewModel.clearLog();
        viewModel.setIsProcessing(true);

        ApkProcessService.setCallback(this);
        Intent intent = new Intent(requireContext(), ApkProcessService.class);
        intent.putExtra("action", ApkProcessService.ACTION_REMOVE_AD);
        intent.putExtra(ApkProcessService.EXTRA_INPUT, apkPath);
        intent.putExtra(ApkProcessService.EXTRA_OUTPUT, outApk);

        String ks = viewModel.getKeystorePath().getValue();
        if (ks != null && !ks.isEmpty()) {
            intent.putExtra(ApkProcessService.EXTRA_KEYSTORE, ks);
            intent.putExtra(ApkProcessService.EXTRA_STORE_PASS, viewModel.getKeystorePass().getValue());
            intent.putExtra(ApkProcessService.EXTRA_KEY_ALIAS, viewModel.getKeyAlias().getValue());
            intent.putExtra(ApkProcessService.EXTRA_KEY_PASS, viewModel.getKeyPass().getValue());
        }

        requireContext().startService(intent);
    }

    private void startAiRemoveAd() {
        String apkPath = etApkPath.getText().toString();
        String outApk = etOutApk.getText().toString();

        if (apkPath.isEmpty()) {
            viewModel.appendLog("Error: No APK file selected");
            return;
        }

        viewModel.setOutputPath(outApk);
        viewModel.clearLog();
        viewModel.setIsProcessing(true);

        ApkProcessService.setCallback(this);
        Intent intent = new Intent(requireContext(), ApkProcessService.class);
        intent.putExtra("action", ApkProcessService.ACTION_AI_REMOVE_AD);
        intent.putExtra(ApkProcessService.EXTRA_INPUT, apkPath);
        intent.putExtra(ApkProcessService.EXTRA_OUTPUT, outApk);

        String ks = viewModel.getKeystorePath().getValue();
        if (ks != null && !ks.isEmpty()) {
            intent.putExtra(ApkProcessService.EXTRA_KEYSTORE, ks);
            intent.putExtra(ApkProcessService.EXTRA_STORE_PASS, viewModel.getKeystorePass().getValue());
            intent.putExtra(ApkProcessService.EXTRA_KEY_ALIAS, viewModel.getKeyAlias().getValue());
            intent.putExtra(ApkProcessService.EXTRA_KEY_PASS, viewModel.getKeyPass().getValue());
        }

        requireContext().startService(intent);
    }

    @Override
    public void onLog(String message) {
        viewModel.appendLog(message);
    }

    @Override
    public void onComplete(boolean success, String message) {
        viewModel.setIsProcessing(false);
        viewModel.appendLog(success ? "Success: " + message : "Failed: " + message);
        ApkProcessService.clearCallback();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        ApkProcessService.clearCallback();
    }
}
