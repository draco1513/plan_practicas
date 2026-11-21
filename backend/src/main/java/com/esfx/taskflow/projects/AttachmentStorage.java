package com.esfx.taskflow.projects;

import com.esfx.taskflow.common.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Guarda los adjuntos en disco (en Docker, un volumen). En la BD solo se guarda la ruta relativa.
 * Más adelante se puede cambiar por S3 / MinIO sin tocar al resto del módulo.
 */
@Component
public class AttachmentStorage {
    private final Path root;

    public AttachmentStorage(@Value("${app.storage.path}") String path) {
        this.root = Path.of(path).toAbsolutePath().normalize();
    }

    /** Guarda el archivo y devuelve su ruta relativa. El nombre en disco es un UUID: el nombre original nunca se usa como ruta. */
    public String save(long taskId, MultipartFile file) {
        String relative = taskId + "/" + UUID.randomUUID();
        Path target = resolve(relative);
        try {
            Files.createDirectories(target.getParent());
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new ApiException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "STORAGE_ERROR", "No se pudo guardar el archivo");
        }
        return relative;
    }

    public Path resolve(String relative) {
        Path p = root.resolve(relative).normalize();
        if (!p.startsWith(root)) throw ApiException.badRequest("Ruta no válida");
        return p;
    }
}
