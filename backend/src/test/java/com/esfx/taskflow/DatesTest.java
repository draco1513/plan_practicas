package com.esfx.taskflow;

import com.esfx.taskflow.common.ApiException;
import com.esfx.taskflow.common.Dates;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DatesTest {
    @Test
    void diaCompletoUsaHoraDeLima() {
        // Lima es UTC-5: el 4 de noviembre empieza a las 05:00 UTC
        assertEquals(Instant.parse("2026-11-04T05:00:00Z"), Dates.parse("2026-11-04", false));
        assertEquals(Instant.parse("2026-11-05T05:00:00Z"), Dates.parse("2026-11-04", true));
    }

    @Test
    void aceptaInstantesIso() {
        assertEquals(Instant.parse("2026-11-04T10:00:00Z"), Dates.parse("2026-11-04T10:00:00Z", false));
    }

    @Test
    void vacioDevuelveNulo() { assertNull(Dates.parse("", false)); }

    @Test
    void fechaInvalidaDa400() { assertThrows(ApiException.class, () -> Dates.parse("hola", false)); }
}
