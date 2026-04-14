package com.hayden.utilitymodule.schema;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class DelegatingSchemaReplacer {

    @Autowired(required = false)
    List<SchemaReplacer> replacerList = new ArrayList<>();


    public String replace(String toReplaceFrom) {
        if (toReplaceFrom == null) {
            return null;
        }

        for (var r : replacerList) {
            toReplaceFrom = r.replace(toReplaceFrom);
        }

        return toReplaceFrom;
    }

}
