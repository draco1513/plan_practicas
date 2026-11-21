package com.esfx.taskflow.common;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

/** Zona horaria de la organización y lectura flexible de fechas recibidas por query string. */
public final class Dates {
    public static final ZoneId ZONE = ZoneId.of("America/Lima");

    private Dates() {}

    /** Acepta "2026-11-04" (día completo en hora de Lima) o un instante ISO ("2026-11-04T10:00:00Z"). */
    public static Instant parse(String value, boolean endOfDay) {
        if (value == null || value.isBlank()) return null;
        try {
            if (value.length() == 10) {
                LocalDate d = LocalDate.parse(value);
                return (endOfDay ? d.plusDays(1) : d).atStartOfDay(ZONE).toInstant();
            }
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            throw ApiException.badRequest("Fecha no válida: " + value);
        }
    }
}
