package com.smartgym.training.ai;

import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class ProgrammeTextPolicy {
    private static final Pattern MEDICAL_REQUEST = Pattern.compile(
            "\\b(injur(?:y|ies)|rehab(?:ilitation)?|treat(?:ment)?|diagnos(?:e|is)|medical advice|therapy|pain|hurt|sore|illness)\\b",
            Pattern.CASE_INSENSITIVE);

    private ProgrammeTextPolicy() {}

    public static void requireNonMedical(String text) {
        if (isMedicalRequest(text)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Training generation does not provide medical diagnosis, injury rehabilitation, or treatment. Please consult a qualified professional.");
        }
    }

    public static boolean isMedicalRequest(String text) {
        return text != null && MEDICAL_REQUEST.matcher(text.toLowerCase(Locale.ROOT)).find();
    }
}
