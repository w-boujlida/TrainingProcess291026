package com.adient.training.service;

import com.adient.training.exception.ApiException;
import org.springframework.http.HttpStatus;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Iterator;

public final class SignatureValidator {
    private SignatureValidator() {}

    public static void validatePng(String signature) {
        String prefix = "data:image/png;base64,";
        if (signature == null || !signature.startsWith(prefix)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "La signature doit être une image PNG valide.");
        }
        try {
            byte[] image = Base64.getDecoder().decode(signature.substring(prefix.length()));
            byte[] png = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
            if (image.length < png.length || image.length > 30_000) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "La signature doit être une image de moins de 30 Ko.");
            }
            for (int i = 0; i < png.length; i++) {
                if (image[i] != png[i]) throw invalid();
            }
            try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(image))) {
                Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext()) throw invalid();
                ImageReader reader = readers.next();
                try {
                    reader.setInput(input, true, true);
                    if (!"png".equalsIgnoreCase(reader.getFormatName())
                            || reader.getWidth(0) > 1500 || reader.getHeight(0) > 400 || reader.read(0) == null) {
                        throw invalid();
                    }
                } finally {
                    reader.dispose();
                }
            }
        } catch (IllegalArgumentException | IOException e) {
            throw invalid();
        }
    }

    private static ApiException invalid() {
        return new ApiException(HttpStatus.BAD_REQUEST, "La signature PNG est invalide.");
    }
}
