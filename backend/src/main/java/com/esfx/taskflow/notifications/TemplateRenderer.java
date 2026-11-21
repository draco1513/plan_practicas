package com.esfx.taskflow.notifications;

import java.util.Map;

/** Reemplaza los marcadores {{nombre}} de una plantilla por sus valores. */
public final class TemplateRenderer {
    private TemplateRenderer() {}

    public static String render(String template, Map<String, String> vars) {
        String out = template;
        for (Map.Entry<String, String> e : vars.entrySet()) {
            out = out.replace("{{" + e.getKey() + "}}", e.getValue() == null ? "" : e.getValue());
        }
        return out.replaceAll("\\{\\{[^}]*}}", "");
    }
}
