/*
 * Copyright (C) 2016 Baidu, Inc. All Rights Reserved.
 */
package com.lody.manifest;

import org.gradle.api.Project;
import org.gradle.api.tasks.Input;

import groovy.transform.CompileStatic;

/**
 * Created by sunpengfei on 16/8/3.
 */

@CompileStatic
public class ManifestExtension {
    @Input
    public int stub_count = 20;

    public static ManifestExtension getConfig(Project project) {
        ManifestExtension config = project.getExtensions().findByType(ManifestExtension.class);
        if (config == null) {
            config = new ManifestExtension();
        }
        return config;
    }

}
