package com.esfx.taskflow;

import com.esfx.taskflow.notifications.TemplateRenderer;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TemplateRendererTest {
    @Test
    void reemplazaMarcadores() {
        String out = TemplateRenderer.render("Hola {{userName}}, tarea {{taskKey}}", Map.of("userName", "Ana", "taskKey", "TF-1"));
        assertEquals("Hola Ana, tarea TF-1", out);
    }

    @Test
    void marcadoresSinValorQuedanVacios() {
        assertEquals("Vence el .", TemplateRenderer.render("Vence el {{dueDate}}.", Map.of()));
    }
}
