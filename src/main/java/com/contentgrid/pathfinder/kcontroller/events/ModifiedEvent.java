package com.contentgrid.pathfinder.kcontroller.events;

import io.fabric8.kubernetes.api.model.ConfigMap;

public record ModifiedEvent(ConfigMap oldConfigMap, ConfigMap newConfigMap) implements Event{

    @Override
    public String getName() {
        return newConfigMap.getMetadata().getName();
    }
}
