package com.contentgrid.pathfinder.kcontroller.events;

import io.fabric8.kubernetes.api.model.ConfigMap;

public record CreatedEvent(ConfigMap configMap) implements Event {

    @Override
    public String getName() {
        return configMap.getMetadata().getName();
    }
}
