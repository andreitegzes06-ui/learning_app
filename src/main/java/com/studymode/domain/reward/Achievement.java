package com.studymode.domain.reward;

import com.studymode.domain.Validation;

public record Achievement(String code, String title, String description) {
    public Achievement {
        code = Validation.text(code, "Achievement code", 60);
        if (!code.matches("[a-z][a-z0-9_]*")) throw new IllegalArgumentException("Achievement code must be a stable lowercase identifier.");
        title = Validation.text(title, "Achievement title", 100);
        description = Validation.text(description, "Achievement description", 2000);
    }
}
