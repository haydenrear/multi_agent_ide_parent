package com.hayden.utilitymodule.telemetry.log;

import io.opentelemetry.api.common.Attributes;

import java.util.List;

public interface AttributeProvider {

    List<Attributes> getAttributes();



}
