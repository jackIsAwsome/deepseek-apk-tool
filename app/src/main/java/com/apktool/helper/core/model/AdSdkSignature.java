package com.apktool.helper.core.model;

import java.util.List;
import java.util.Set;

public class AdSdkSignature {
    private final String name;
    private final String packagePrefix;
    private final Set<String> classKeywords;
    private final Set<String> manifestComponents;
    private final List<String> permissions;

    public AdSdkSignature(String name, String packagePrefix,
                          Set<String> classKeywords,
                          Set<String> manifestComponents,
                          List<String> permissions) {
        this.name = name;
        this.packagePrefix = packagePrefix;
        this.classKeywords = classKeywords;
        this.manifestComponents = manifestComponents;
        this.permissions = permissions;
    }

    public String getName() { return name; }
    public String getPackagePrefix() { return packagePrefix; }
    public Set<String> getClassKeywords() { return classKeywords; }
    public Set<String> getManifestComponents() { return manifestComponents; }
    public List<String> getPermissions() { return permissions; }
}
