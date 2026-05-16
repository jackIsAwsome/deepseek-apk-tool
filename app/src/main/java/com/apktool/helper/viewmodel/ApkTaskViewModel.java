package com.apktool.helper.viewmodel;

import android.app.Application;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

public class ApkTaskViewModel extends AndroidViewModel {

    private final MutableLiveData<String> selectedApkPath = new MutableLiveData<>();
    private final MutableLiveData<String> outputPath = new MutableLiveData<>();
    private final MutableLiveData<String> logText = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isProcessing = new MutableLiveData<>(false);
    private final MutableLiveData<String> keystorePath = new MutableLiveData<>();
    private final MutableLiveData<String> keystorePass = new MutableLiveData<>("android");
    private final MutableLiveData<String> keyAlias = new MutableLiveData<>("androiddebugkey");
    private final MutableLiveData<String> keyPass = new MutableLiveData<>("android");

    public ApkTaskViewModel(Application app) {
        super(app);
    }

    public LiveData<String> getSelectedApkPath() { return selectedApkPath; }
    public void setSelectedApkPath(String path) { selectedApkPath.setValue(path); }

    public LiveData<String> getOutputPath() { return outputPath; }
    public void setOutputPath(String path) { outputPath.setValue(path); }

    public LiveData<String> getLogText() { return logText; }
    public void appendLog(String msg) {
        String current = logText.getValue();
        logText.setValue((current != null ? current : "") + msg + "\n");
    }
    public void clearLog() { logText.setValue(""); }

    public LiveData<Boolean> getIsProcessing() { return isProcessing; }
    public void setIsProcessing(boolean v) { isProcessing.setValue(v); }

    public LiveData<String> getKeystorePath() { return keystorePath; }
    public void setKeystorePath(String path) { keystorePath.setValue(path); }

    public LiveData<String> getKeystorePass() { return keystorePass; }
    public void setKeystorePass(String pass) { keystorePass.setValue(pass); }

    public LiveData<String> getKeyAlias() { return keyAlias; }
    public void setKeyAlias(String alias) { keyAlias.setValue(alias); }

    public LiveData<String> getKeyPass() { return keyPass; }
    public void setKeyPass(String pass) { keyPass.setValue(pass); }
}
