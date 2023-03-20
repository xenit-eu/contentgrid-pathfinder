package com.contentgrid.pathfinder.kcontroller;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.client.Watcher.Action;

public record Event(Action action, ConfigMap configMap) {


}
