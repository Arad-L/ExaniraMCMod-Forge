package com.exanira.backstory;

import java.util.Map;

public class BackstoryTemplate {

    private final String template;

    public BackstoryTemplate(String template) {
        this.template = template;
    }

    public String resolve(Map<String, String> values) {
        String result = template;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }
}
