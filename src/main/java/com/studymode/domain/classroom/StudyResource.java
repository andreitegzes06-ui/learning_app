package com.studymode.domain.classroom;

import com.studymode.domain.Validation;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.UUID;

public record StudyResource(UUID id, String title, URI uri) {
    public StudyResource {
        Validation.required(id, "Resource ID");
        title = Validation.text(title, "Resource title", 100);
        Validation.required(uri, "Resource URL");
        if (uri.toString().length() > 2048 || uri.getHost() == null || uri.getUserInfo() != null
                || !("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                || uri.getPort() > 65535 || uri.toString().codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Resource URL must be an HTTP or HTTPS address with a host and no credentials.");
        }
    }

    public static StudyResource fromUrl(UUID id, String title, String url) {
        try {
            return new StudyResource(id, title, new URI(Validation.required(url, "URL").strip()));
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Resource URL is malformed.", e);
        }
    }
}
